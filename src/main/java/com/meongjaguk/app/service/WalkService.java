package com.meongjaguk.app.service;

import com.meongjaguk.app.dto.CoursePointDto;
import com.meongjaguk.app.dto.WalkSaveRequest;
import com.meongjaguk.app.entity.Pet;
import com.meongjaguk.app.entity.WalkRecord;
import com.meongjaguk.app.entity.WalkRecordPoint;
import com.meongjaguk.app.entity.WalkRecordPlannedPoint;
import com.meongjaguk.app.repository.PetRepository;
import com.meongjaguk.app.repository.WalkRecordPetRepository;
import com.meongjaguk.app.repository.WalkRecordPointRepository;
import com.meongjaguk.app.repository.WalkRecordPlannedPointRepository;
import com.meongjaguk.app.repository.WalkRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** 산책 기록 저장 - 담당: 박용제 */
@Service
@RequiredArgsConstructor
public class WalkService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_POINTS = 10_000; // 비정상적으로 많은 좌표 방지
    private static final int MAX_PLANNED_POINTS = 5_000;

    private final WalkRecordRepository walkRecordRepository;
    private final WalkRecordPointRepository walkRecordPointRepository;
    private final WalkRecordPlannedPointRepository plannedPointRepository;
    private final WalkRecordPetRepository walkRecordPetRepository;
    private final PetRepository petRepository;

    /** 끝난 산책(+ 경로 좌표)을 저장하고 저장된 기록 번호를 돌려줌 */
    @Transactional
    public Long saveCompletedWalk(Long userId, WalkSaveRequest request) {
        if (request == null || request.startedAt() == null || request.endedAt() == null
                || request.endedAt() <= request.startedAt()) {
            throw new IllegalArgumentException("산책 시간이 올바르지 않습니다.");
        }

        WalkSaveRequest.RecommendedRoute recommended = request.recommendedRoute();
        validateRecommendedRoute(recommended);
        List<Long> petIds = validatePetIds(userId, request.petIds());

        if (recommended != null && request.courseId() != null) {
            throw new IllegalArgumentException("산책 경로 정보가 올바르지 않습니다.");
        }

        LocalDateTime startedAt = toSeoulTime(request.startedAt());
        LocalDateTime endedAt = toSeoulTime(request.endedAt());
        int durationSeconds = (int) ((request.endedAt() - request.startedAt()) / 1000);
        int distanceM = request.distanceM() == null ? 0 : Math.max(0, request.distanceM());

        // 1) 산책 기록 한 줄 저장
        WalkRecord record = WalkRecord.completed(
                userId, request.courseId(), startedAt, endedAt, durationSeconds, distanceM);
        if (recommended != null) {
            record.setPlannedRoute(recommended.title().trim(), recommended.description(),
                    recommended.distanceM(), recommended.estimatedMinutes());
        }
        Long walkRecordId = walkRecordRepository.save(record).getWalkRecordId();

        // 2) 경로 좌표 저장 (같은 트랜잭션이라 하나라도 실패하면 기록도 같이 취소됨)
        saveRoutePoints(walkRecordId, request.points(), startedAt);
        if (recommended != null) {
            savePlannedPoints(walkRecordId, recommended.points());
        }

        walkRecordPetRepository.savePetLinks(walkRecordId, petIds);

        return walkRecordId;
    }

    private void validateRecommendedRoute(WalkSaveRequest.RecommendedRoute route) {
        if (route == null) {
            return;
        }
        if (route.title() == null || route.title().isBlank() || route.title().length() > 100
                || (route.description() != null && route.description().length() > 1000)
                || route.distanceM() == null || route.distanceM() <= 0
                || route.estimatedMinutes() == null || route.estimatedMinutes() <= 0
                || route.points() == null || route.points().size() < 2
                || route.points().size() > MAX_PLANNED_POINTS) {
            throw new IllegalArgumentException("추천 산책 경로 정보가 올바르지 않습니다.");
        }
        for (CoursePointDto point : route.points()) {
            if (point == null || point.sequence() == null
                    || point.latitude() == null || point.longitude() == null
                    || !Double.isFinite(point.latitude()) || !Double.isFinite(point.longitude())
                    || Math.abs(point.latitude()) > 90 || Math.abs(point.longitude()) > 180) {
                throw new IllegalArgumentException("추천 산책 경로 정보가 올바르지 않습니다.");
            }
        }
    }

    private void savePlannedPoints(Long walkRecordId, List<CoursePointDto> points) {
        List<CoursePointDto> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparing(CoursePointDto::sequence));
        List<WalkRecordPlannedPoint> entities = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            CoursePointDto point = sorted.get(index);
            entities.add(WalkRecordPlannedPoint.of(walkRecordId, index + 1,
                    point.latitude(), point.longitude()));
        }
        plannedPointRepository.saveAll(entities);
    }

    private void saveRoutePoints(Long walkRecordId, List<WalkSaveRequest.Point> points,
                                 LocalDateTime fallbackTime) {
        if (points == null || points.isEmpty()) {
            return; // 위치 권한이 없었으면 좌표 없이 기록만 저장
        }
        if (points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("좌표가 너무 많습니다.");
        }

        List<WalkRecordPoint> entities = new ArrayList<>();
        int sequenceNo = 1;
        for (WalkSaveRequest.Point p : points) {
            if (p == null || p.lat() == null || p.lng() == null) {
                continue; // 잘못된 점은 건너뜀
            }
            LocalDateTime recordedAt = p.t() == null ? fallbackTime : toSeoulTime(p.t());
            entities.add(WalkRecordPoint.of(walkRecordId, sequenceNo++, p.lat(), p.lng(), recordedAt));
        }
        walkRecordPointRepository.saveAll(entities);
    }

    /** 브라우저 밀리초 → 한국 시간 */
    private LocalDateTime toSeoulTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), SEOUL);
    }

    /* saveCompletedWalk()에서 산책 기록을 DB에 저장하기 전에 반려견 ID를 검증하는 메서드 */
    private List<Long> validatePetIds(Long userId, List<Long> petIds) {
        if (petIds == null || petIds.isEmpty()) {
            return List.of();
        }

        if (petIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("반려견 정보가 올바르지 않습니다.");
        }

        List<Long> distinctPetIds = new LinkedHashSet<>(petIds).stream().toList();

        Set<Long> myPetIds = petRepository.findByUser_UserIdOrderByPetIdAsc(userId).stream()
                .map(Pet::getPetId)
                .collect(Collectors.toSet());

        if (!myPetIds.containsAll(distinctPetIds)) {
            throw new IllegalArgumentException("본인의 반려견만 선택할 수 있습니다.");
        }

        return distinctPetIds;
    }
}
