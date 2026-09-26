package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.CommentDto;
import com.mungjaguk.app.dto.MeetingCardDto;
import com.mungjaguk.app.dto.MeetingDetailDto;
import com.mungjaguk.app.entity.ApplicationStatus;
import com.mungjaguk.app.entity.MeetingStatus;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.entity.WalkApplication;
import com.mungjaguk.app.entity.WalkMeeting;
import com.mungjaguk.app.entity.WalkMeetingComment;
import com.mungjaguk.app.repository.WalkApplicationRepository;
import com.mungjaguk.app.repository.WalkMeetingCommentRepository;
import com.mungjaguk.app.repository.WalkMeetingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 같이 걷기 게시판: 목록/상세 조회, 동행 신청/취소, 댓글 조회/작성
 */
@Service
@Transactional(readOnly = true)
public class MeetupService {

    private final WalkMeetingRepository meetingRepository;
    private final WalkApplicationRepository applicationRepository;
    private final WalkMeetingCommentRepository commentRepository;
    private final UserService userService;

    public MeetupService(WalkMeetingRepository meetingRepository,
                         WalkApplicationRepository applicationRepository,
                         WalkMeetingCommentRepository commentRepository,
                         UserService userService) {
        this.meetingRepository = meetingRepository;
        this.applicationRepository = applicationRepository;
        this.commentRepository = commentRepository;
        this.userService = userService;
    }

    /** 산책로 게시판 목록: 최신순 6개 */
    public List<MeetingCardDto> getRecentMeetings() {
        List<WalkMeeting> meetings = meetingRepository.findTop6ByOrderByCreatedAtDescMeetingIdDesc();
        if (meetings.isEmpty()) {
            return List.of();
        }

        List<Long> meetingIds = meetings.stream().map(WalkMeeting::getMeetingId).toList();
        Map<Long, Long> acceptedCounts = new HashMap<>();
        for (Object[] row : applicationRepository.countByMeetingIdsAndStatus(meetingIds, ApplicationStatus.ACCEPTED)) {
            acceptedCounts.put((Long) row[0], (Long) row[1]);
        }

        return meetings.stream()
                .map(meeting -> {
                    Route course = meeting.getCourse();
                    int accepted = acceptedCounts.getOrDefault(meeting.getMeetingId(), 0L).intValue();
                    return new MeetingCardDto(
                            meeting.getMeetingId(),
                            meeting.getTitle(),
                            meeting.getHost().getNickname(),
                            meeting.getCreatedAt(),
                            meeting.getMeetingDate(),
                            meeting.getMeetingTime(),
                            course.getDistanceM(),
                            course.getEstimatedMinutes(),
                            1 + accepted,
                            meeting.getMaxParticipants(),
                            meeting.getStatus().name());
                })
                .toList();
    }

    /** 공유 산책로 상세 (loginUserId는 비로그인이면 null) */
    public MeetingDetailDto getMeeting(Long meetingId, Long loginUserId) {
        WalkMeeting meeting = findMeeting(meetingId);
        Route course = meeting.getCourse();

        List<WalkApplication> accepted = applicationRepository
                .findByMeeting_MeetingIdAndStatusOrderByCreatedAtAsc(meetingId, ApplicationStatus.ACCEPTED);

        List<String> participants = new ArrayList<>();
        participants.add(meeting.getHost().getNickname());
        accepted.forEach(application -> participants.add(application.getUser().getNickname()));

        boolean isHost = loginUserId != null && meeting.isHostedBy(loginUserId);
        String myStatus = null;
        if (loginUserId != null) {
            myStatus = applicationRepository.findByMeeting_MeetingIdAndUser_UserId(meetingId, loginUserId)
                    .map(application -> application.getStatus().name())
                    .orElse(null);
        }

        return new MeetingDetailDto(
                meeting.getMeetingId(),
                meeting.getTitle(),
                meeting.getDescription(),
                course.getCourseName(),
                course.getDistanceM(),
                course.getEstimatedMinutes(),
                meeting.getMeetingDate(),
                meeting.getMeetingTime(),
                meeting.isPetRequired(),
                meeting.getParticipationCondition(),
                meeting.getHost().getNickname(),
                meeting.getCreatedAt(),
                participants,
                participants.size(),
                meeting.getMaxParticipants(),
                meeting.getStatus().name(),
                isHost,
                myStatus);
    }

    /**
     * 동행 신청
     * - 본인이 공유한 모집에는 신청 불가
     * - RECRUITING 상태에서만 신청 가능
     * - 같은 모집에 중복 신청 불가
     * - 신청 후 상태는 PENDING
     */
    @Transactional
    public void apply(Long meetingId, Long userId) {
        WalkMeeting meeting = findMeeting(meetingId);

        if (meeting.isHostedBy(userId)) {
            throw new IllegalStateException("본인이 공유한 모집에는 신청할 수 없어요.");
        }
        if (meeting.getStatus() != MeetingStatus.RECRUITING) {
            throw new IllegalStateException("모집이 마감되어 신청할 수 없어요.");
        }
        if (applicationRepository.existsByMeeting_MeetingIdAndUser_UserId(meetingId, userId)) {
            throw new IllegalStateException("이미 신청한 모집이에요.");
        }

        User user = userService.findById(userId);
        try {
            applicationRepository.saveAndFlush(WalkApplication.create(meeting, user));
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 요청된 경우 유니크 제약(uk_walk_applications_meeting_user)에 걸림
            throw new IllegalStateException("이미 신청한 모집이에요.");
        }
    }

    /**
     * 동행 신청 취소
     * - PENDING 상태에서만 취소 가능
     * - 취소 시 신청 행 삭제 (유니크 제약 때문에 재신청이 가능하도록)
     */
    @Transactional
    public void cancel(Long meetingId, Long userId) {
        WalkApplication application = applicationRepository
                .findByMeeting_MeetingIdAndUser_UserId(meetingId, userId)
                .orElseThrow(() -> new NoSuchElementException("신청 내역이 없어요."));

        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 신청만 취소할 수 있어요.");
        }

        applicationRepository.delete(application);
    }

    /** 댓글 목록: 오래된 순 */
    public List<CommentDto> getComments(Long meetingId) {
        WalkMeeting meeting = findMeeting(meetingId);
        return commentRepository.findByMeeting_MeetingIdOrderByCreatedAtAscCommentIdAsc(meetingId)
                .stream()
                .map(comment -> toCommentDto(comment, meeting))
                .toList();
    }

    /**
     * 댓글 작성 (로그인 회원만)
     * - 내용은 앞뒤 공백 제거 후 1자 이상, 500자 이하
     */
    @Transactional
    public CommentDto addComment(Long meetingId, Long userId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }
        if (trimmed.length() > WalkMeetingComment.MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 " + WalkMeetingComment.MAX_CONTENT_LENGTH + "자까지 입력할 수 있어요.");
        }

        WalkMeeting meeting = findMeeting(meetingId);
        User user = userService.findById(userId);
        WalkMeetingComment saved = commentRepository.save(WalkMeetingComment.create(meeting, user, trimmed));
        return toCommentDto(saved, meeting);
    }

    private CommentDto toCommentDto(WalkMeetingComment comment, WalkMeeting meeting) {
        User author = comment.getUser();
        return new CommentDto(
                comment.getCommentId(),
                author.getNickname(),
                meeting.isHostedBy(author.getUserId()),
                comment.getContent(),
                comment.getCreatedAt());
    }

    private WalkMeeting findMeeting(Long meetingId) {
        return meetingRepository.findWithHostAndCourseByMeetingId(meetingId)
                .orElseThrow(() -> new NoSuchElementException("모집 정보를 찾을 수 없어요."));
    }
}
