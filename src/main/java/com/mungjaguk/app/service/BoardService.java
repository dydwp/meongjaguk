package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.BoardCardDto;
import com.mungjaguk.app.dto.BoardDetailDto;
import com.mungjaguk.app.dto.CommentDto;
import com.mungjaguk.app.entity.ApplicationStatus;
import com.mungjaguk.app.entity.Board;
import com.mungjaguk.app.entity.BoardComment;
import com.mungjaguk.app.entity.CompanionRequest;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.BoardCommentRepository;
import com.mungjaguk.app.repository.BoardRepository;
import com.mungjaguk.app.repository.CompanionRequestRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 산책로 게시판: 목록/상세 조회, 댓글 조회/작성/삭제
 */
@Service
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final CompanionRequestRepository companionRequestRepository;
    private final BoardCommentRepository commentRepository;
    private final UserService userService;

    public BoardService(BoardRepository boardRepository,
                        CompanionRequestRepository companionRequestRepository,
                        BoardCommentRepository commentRepository,
                        UserService userService) {
        this.boardRepository = boardRepository;
        this.companionRequestRepository = companionRequestRepository;
        this.commentRepository = commentRepository;
        this.userService = userService;
    }

    /** 산책로 게시판 목록: 최신순 6개 */
    public List<BoardCardDto> getRecentBoards() {
        List<Board> boards = boardRepository.findTop6ByOrderByCreatedAtDescMeetingIdDesc();
        if (boards.isEmpty()) {
            return List.of();
        }

        List<Long> meetingIds = new ArrayList<>();
        for (Board board : boards) {
            meetingIds.add(board.getMeetingId());
        }
        Map<Long, Long> acceptedCounts = new HashMap<>();
        for (Object[] row : companionRequestRepository.countByMeetingIdsAndStatus(meetingIds, ApplicationStatus.ACCEPTED)) {
            acceptedCounts.put((Long) row[0], (Long) row[1]);
        }

        List<BoardCardDto> cards = new ArrayList<>();
        for (Board board : boards) {
            Route course = board.getCourse();
            int accepted = acceptedCounts.getOrDefault(board.getMeetingId(), 0L).intValue();
            cards.add(new BoardCardDto(
                    board.getMeetingId(),
                    board.getTitle(),
                    board.getHost().getNickname(),
                    board.getCreatedAt(),
                    board.getMeetingDate(),
                    board.getMeetingTime(),
                    course.getDistanceM(),
                    course.getEstimatedMinutes(),
                    1 + accepted,
                    board.getMaxParticipants(),
                    board.getStatus().name()));
        }
        return cards;
    }

    /** 공유 산책로 상세 (loginUserId는 비로그인이면 null) */
    public BoardDetailDto getBoard(Long meetingId, Long loginUserId) {
        Board board = findBoard(meetingId);
        Route course = board.getCourse();

        List<CompanionRequest> accepted = companionRequestRepository
                .findByMeetingIdAndStatusOrderByCreatedAtAsc(meetingId, ApplicationStatus.ACCEPTED);

        List<String> participants = new ArrayList<>();
        participants.add(board.getHost().getNickname());
        accepted.forEach(request -> participants.add(request.getApplicant().getNickname()));

        boolean isHost = loginUserId != null && board.isHostedBy(loginUserId);
        String myStatus = null;
        if (loginUserId != null) {
            myStatus = companionRequestRepository.findByMeetingIdAndApplicant_UserId(meetingId, loginUserId)
                    .map(request -> request.getStatus().name())
                    .orElse(null);
        }

        return new BoardDetailDto(
                board.getMeetingId(),
                board.getTitle(),
                board.getDescription(),
                course.getCourseName(),
                course.getDistanceM(),
                course.getEstimatedMinutes(),
                board.getMeetingDate(),
                board.getMeetingTime(),
                board.isPetRequired(),
                board.getParticipationCondition(),
                board.getHost().getNickname(),
                board.getCreatedAt(),
                participants,
                participants.size(),
                board.getMaxParticipants(),
                board.getStatus().name(),
                isHost,
                myStatus);
    }

    /** 댓글 목록: 오래된 순 (loginUserId는 비로그인이면 null) */
    public List<CommentDto> getComments(Long meetingId, Long loginUserId) {
        Board board = findBoard(meetingId);
        return commentRepository.findByBoard_MeetingIdOrderByCreatedAtAscCommentIdAsc(meetingId)
                .stream()
                .map(comment -> toCommentDto(comment, board, loginUserId))
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
        if (trimmed.length() > BoardComment.MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 " + BoardComment.MAX_CONTENT_LENGTH + "자까지 입력할 수 있어요.");
        }

        Board board = findBoard(meetingId);
        User user = userService.findById(userId);
        BoardComment saved = commentRepository.save(BoardComment.create(board, user, trimmed));
        return toCommentDto(saved, board, userId);
    }

    /**
     * 댓글 삭제
     * - 본인이 작성한 댓글만 삭제 가능
     */
    @Transactional
    public void deleteComment(Long meetingId, Long commentId, Long userId) {
        BoardComment comment = commentRepository.findById(commentId)
                .filter(c -> c.getBoard().getMeetingId().equals(meetingId))
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없어요."));

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("본인이 작성한 댓글만 삭제할 수 있어요.");
        }

        commentRepository.delete(comment);
    }

    private CommentDto toCommentDto(BoardComment comment, Board board, Long loginUserId) {
        User author = comment.getUser();
        return new CommentDto(
                comment.getCommentId(),
                author.getNickname(),
                board.isHostedBy(author.getUserId()),
                author.getUserId().equals(loginUserId),
                comment.getContent(),
                comment.getCreatedAt());
    }

    private Board findBoard(Long meetingId) {
        return boardRepository.findWithHostAndCourseByMeetingId(meetingId)
                .orElseThrow(() -> new NoSuchElementException("모집 정보를 찾을 수 없어요."));
    }
}
