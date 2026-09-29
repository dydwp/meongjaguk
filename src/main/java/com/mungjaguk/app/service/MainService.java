package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.MeetCardDto;
import com.mungjaguk.app.dto.WeeklyWalkSummary;
import com.mungjaguk.app.entity.Pet;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.repository.PetRepository;
import com.mungjaguk.app.repository.RouteRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

/**
 * 메인 페이지에 필요한 데이터를 모아주는 서비스 (담당: 박용제)
 * 다른 팀원의 Repository는 조회만 하고 수정하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainService {

    private final RouteRepository routeRepository;
    private final WalkRecordRepository walkRecordRepository;
    private final PetRepository petRepository;

    /** 추천 산책로를 course_id 순서로 limit개 가져옴 */
    public List<Route> getRecommendRoutes(int limit) {
        PageRequest page = PageRequest.of(0, limit, Sort.by("courseId").ascending());
        return routeRepository.findAll(page).getContent();
    }

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
        if (date.equals(today)) return "오늘";
        if (date.equals(today.minusDays(1))) return "어제";
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    private static String formatKm(int meters) {
        return String.format(Locale.ROOT, "%.1fkm", meters / 1000.0);
    }

    /**
     * 같이 걷기 모집 카드 limit개
     * TODO: 환중님 walk_meetings 엔티티/Repository가 dev에 합쳐지면
     *       이 메서드 안쪽만 "DB 조회 → MeetCardDto 변환"으로 교체
     */
    public List<MeetCardDto> getRecentMeets(int limit) {
        LocalDate today = LocalDate.now();
        List<MeetCardDto> meets = List.of(
                new MeetCardDto(1L, "주말 서울숲 같이 걸어요",
                        "서울숲 반려견 산책 코스", "소형견 환영",
                        today.plusDays(1), LocalTime.of(16, 0), 3, 5, "RECRUITING"),
                new MeetCardDto(2L, "아침 뚝섬 한강 산책 메이트 구해요",
                        "한강공원 뚝섬 산책 코스", "활동량 많은 친구",
                        today.plusDays(2), LocalTime.of(8, 0), 2, 4, "RECRUITING"),
                new MeetCardDto(3L, "퇴근 후 올림픽공원 한 바퀴",
                        "올림픽공원 순환 산책 코스", "대형견 가능",
                        today.minusDays(1), LocalTime.of(19, 30), 6, 6, "CLOSED")
        );
        return meets.stream().limit(limit).toList();
    }
}