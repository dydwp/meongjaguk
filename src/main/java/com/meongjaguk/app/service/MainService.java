package com.meongjaguk.app.service;

import com.meongjaguk.app.dto.MeetCardDto;
import com.meongjaguk.app.dto.WeeklyWalkSummary;
import com.meongjaguk.app.entity.ApplicationStatus;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.Pet;
import com.meongjaguk.app.entity.WalkRecord;
import com.meongjaguk.app.repository.BoardRepository;
import com.meongjaguk.app.repository.CompanionRequestRepository;
import com.meongjaguk.app.repository.PetRepository;
import com.meongjaguk.app.repository.WalkRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 메인 페이지에 필요한 데이터를 모아주는 서비스 (담당: 박용제)
 * 다른 팀원의 Repository는 조회만 하고 수정하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainService {

    private final WalkRecordRepository walkRecordRepository;
    private final PetRepository petRepository;
    private final BoardRepository boardRepository;
    private final CompanionRequestRepository companionRequestRepository;

    /** 메인 배너 "이번 주 나의 산책": 월요일 0시부터 지금까지 완료한 산책 합계 + 마지막 산책 */
    public WeeklyWalkSummary getWeeklyWalkSummary(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WalkRecord> records = walkRecordRepository.findByUserIdAndStatusAndStartedAtGreaterThanEqual(
                userId, WalkRecord.STATUS_COMPLETED, monday.atStartOfDay());

        long meters = 0;
        long seconds = 0;
        for (WalkRecord record : records) {
            meters += record.getDistanceM() != null ? record.getDistanceM() : 0;
            seconds += record.getDurationSeconds() != null ? record.getDurationSeconds() : 0;
        }

        String lastWalkLabel = walkRecordRepository
                .findFirstByUserIdAndStatusOrderByStartedAtDesc(userId, WalkRecord.STATUS_COMPLETED)
                .map(last -> dayLabel(last.getStartedAt().toLocalDate(), today) + " "
                        + formatKm(last.getDistanceM() != null ? last.getDistanceM() : 0))
                .orElse(null);

        return new WeeklyWalkSummary(records.size(), meters / 1000.0, (int) Math.round(seconds / 60.0), lastWalkLabel);
    }

    /**
     * 메인 배너 인사말용 반려견 이름 + 조사: "보리랑", "초코랑", "뭉치랑", "밤이랑"
     * 등록한 반려견이 없으면 null
     */
    public String getPetWith(Long userId) {
        List<Pet> pets = petRepository.findByUser_UserIdOrderByPetIdAsc(userId);
        if (pets.isEmpty() || pets.get(0).getName() == null || pets.get(0).getName().isBlank()) {
            return null;
        }
        String name = pets.get(0).getName().trim();
        char last = name.charAt(name.length() - 1);
        boolean hasFinalConsonant = last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
        return name + (hasFinalConsonant ? "이랑" : "랑");
    }

    private static String dayLabel(LocalDate date, LocalDate today) {
        if (date.equals(today))
            return "오늘";
        if (date.equals(today.minusDays(1)))
            return "어제";
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    private static String formatKm(int meters) {
        return String.format(Locale.ROOT, "%.1fkm", meters / 1000.0);
    }

    /**
     * 같이 걷기 모집 카드 limit개 (walk_meetings 실제 데이터)
     * - 최신 글 6개 중 모집 중인 글을 먼저, 부족하면 마감된 글로 채움 (각각 최신순 유지)
     * - 참여 인원 = 작성자 1명 + 수락된 신청 수 (동행 게시판과 같은 기준)
     */
    public List<MeetCardDto> getRecentMeets(int limit) {
        List<Board> boards = boardRepository.findTop6ByOrderByCreatedAtDescMeetingIdDesc();
        if (boards.isEmpty()) {
            return List.of();
        }

        List<Long> meetingIds = boards.stream().map(Board::getMeetingId).toList();
        Map<Long, Long> acceptedCounts = new HashMap<>();
        for (Object[] row : companionRequestRepository.countByMeetingIdsAndStatus(meetingIds,
                ApplicationStatus.ACCEPTED)) {
            acceptedCounts.put((Long) row[0], (Long) row[1]);
        }

        List<MeetCardDto> cards = new ArrayList<>();
        for (Board board : boards) {
            int accepted = acceptedCounts.getOrDefault(board.getMeetingId(), 0L).intValue();
            cards.add(new MeetCardDto(
                    board.getMeetingId(),
                    board.getTitle(),
                    board.getCourse().getCourseName(),
                    board.getParticipationCondition(),
                    board.getMeetingDate(),
                    board.getMeetingTime(),
                    1 + accepted,
                    board.getMaxParticipants(),
                    board.getStatus().name()));
        }

        // 모집 중 먼저 (정렬은 안정 정렬이라 같은 그룹 안에서는 최신순 유지)
        return cards.stream()
                .sorted(Comparator.comparing(MeetCardDto::isClosed))
                .limit(limit)
                .toList();
    }
}