package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.MeetCardDto;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.repository.RouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 메인 페이지에 필요한 데이터를 모아주는 서비스 (담당: 박용제)
 * 다른 팀원의 Repository는 조회만 하고 수정하지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MainService {

    private final RouteRepository routeRepository;

    /** 추천 산책로를 course_id 순서로 limit개 가져옴 */
    public List<Route> getRecommendRoutes(int limit) {
        PageRequest page = PageRequest.of(0, limit, Sort.by("courseId").ascending());
        return routeRepository.findAll(page).getContent();
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