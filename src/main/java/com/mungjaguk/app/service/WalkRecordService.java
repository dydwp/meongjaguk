package com.mungjaguk.app.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mungjaguk.app.dto.WalkDetailView;
import com.mungjaguk.app.dto.WalkHistoryItemView;
import com.mungjaguk.app.dto.WalkPointView;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.repository.RouteRepository;
import com.mungjaguk.app.repository.WalkRecordPointRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;

@Service
@Transactional(readOnly = true)
public class WalkRecordService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA);

    private final WalkRecordRepository walkRecordRepository;
    private final WalkRecordPointRepository walkRecordPointRepository;
    private final RouteRepository routeRepository;

    public WalkRecordService(WalkRecordRepository walkRecordRepository,
                             RouteRepository routeRepository,
                             WalkRecordPointRepository walkRecordPointRepository) {
        this.walkRecordRepository = walkRecordRepository;
        this.routeRepository = routeRepository;
        this.walkRecordPointRepository = walkRecordPointRepository;
    }

    public List<WalkHistoryItemView> getMyWalkHistory(Long userId) {
        return walkRecordRepository.findByUserIdOrderByStartedAtDesc(userId).stream()
                .map(this::toHistoryItem)
                .toList();
    }

    public Optional<WalkDetailView> getDetail(Long walkRecordId, Long userId) {
        return walkRecordRepository.findByWalkRecordIdAndUserId(walkRecordId, userId)
                .map(this::toDetailView);
    }

    private WalkHistoryItemView toHistoryItem(WalkRecord record) {
        Optional<Route> route = findRoute(record.getCourseId());

        return new WalkHistoryItemView(
                record.getWalkRecordId(),
                route.map(Route::getCourseName).orElse("자유 산책"),
                "개인 산책",
                distanceLabel(record.getDistanceM()),
                minuteDurationLabel(record.getDurationSeconds()),
                record.getStartedAt().format(DATE_FORMAT)
        );
    }

    private WalkDetailView toDetailView(WalkRecord record) {
        Optional<Route> route = findRoute(record.getCourseId());

        String title = route.map(Route::getCourseName).orElse("자유 산책");
        String description = route.map(Route::getDescription).orElse("");
        String plannedDistance = route.map(Route::getDistanceM).map(this::distanceLabel).orElse("-");
        String plannedDuration = route.map(Route::getEstimatedMinutes)
                .map(minutes -> "약 " + minutes + "분").orElse("-");

        return new WalkDetailView(
                record.getWalkRecordId(),
                title,
                "개인 산책",
                route.isPresent(),
                WalkRecord.STATUS_COMPLETED.equals(record.getStatus()),
                description,
                "거리 · " + plannedDistance,
                "예상 소요시간 · " + plannedDuration,
                distanceLabel(record.getDistanceM()),
                clockDurationLabel(record.getDurationSeconds()),
                record.getEndedAt() == null
                        ? "산책 중"
                        : record.getEndedAt().format(DATE_FORMAT) + " 완료"
        );
    }

    private Optional<Route> findRoute(Long courseId) {
        if (courseId == null || courseId > Integer.MAX_VALUE) {
            return Optional.empty();
        }

        return routeRepository.findById(courseId.intValue());
    }

    private String distanceLabel(Number distanceM) {
        if (distanceM == null) {
            return "0.0km";
        }

        double km = distanceM.doubleValue() / 1000.0;
        return String.format(Locale.KOREA, "%.1fkm", km);
    }

    private String minuteDurationLabel(Integer durationSeconds) {
        if (durationSeconds == null) {
            return "진행 중";
        }

        int minutes = Math.max(1, Math.round(durationSeconds / 60f));
        return "약 " + minutes + "분";
    }

    private String clockDurationLabel(Integer durationSeconds) {
        if (durationSeconds == null) {
            return "00:00";
        }

        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format(Locale.KOREA, "%02d:%02d", minutes, seconds);
    }

    public List<WalkPointView> getWalkPoints(Long walkRecordId, Long userId) {
        if (walkRecordRepository.findByWalkRecordIdAndUserId(walkRecordId, userId).isEmpty()) {
            return List.of();
        }

        return walkRecordPointRepository.findByWalkRecordIdOrderBySequenceNoAsc(walkRecordId).stream()
                .map(point -> new WalkPointView(point.getLatitude(), point.getLongitude()))
                .toList();
    }

    @Transactional
    public void deleteMyCompletedWalkRecord(Long walkRecordId, Long userId) {
        WalkRecord record = walkRecordRepository.findByWalkRecordIdAndUserId(walkRecordId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "산책 기록을 찾을 수 없습니다."
                ));

        if (!WalkRecord.STATUS_COMPLETED.equals(record.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "완료된 산책 기록만 삭제할 수 있습니다."
            );
        }

        walkRecordPointRepository.deleteByWalkRecordId(walkRecordId);
        walkRecordRepository.delete(record);
    }
}