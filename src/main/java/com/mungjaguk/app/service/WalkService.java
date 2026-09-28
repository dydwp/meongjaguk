package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.WalkSaveRequest;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.entity.WalkRecordPoint;
import com.mungjaguk.app.repository.WalkRecordPointRepository;
import com.mungjaguk.app.repository.WalkRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** 산책 기록 저장 - 담당: 박용제 */
@Service
@RequiredArgsConstructor
public class WalkService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_POINTS = 10_000; // 비정상적으로 많은 좌표 방지

    private final WalkRecordRepository walkRecordRepository;
    private final WalkRecordPointRepository walkRecordPointRepository;

    /** 끝난 산책(+ 경로 좌표)을 저장하고 저장된 기록 번호를 돌려줌 */
    @Transactional
    public Long saveCompletedWalk(Long userId, WalkSaveRequest request) {
        if (request.startedAt() == null || request.endedAt() == null
                || request.endedAt() <= request.startedAt()) {
            throw new IllegalArgumentException("산책 시간이 올바르지 않습니다.");
        }

        LocalDateTime startedAt = toSeoulTime(request.startedAt());
        LocalDateTime endedAt = toSeoulTime(request.endedAt());
        int durationSeconds = (int) ((request.endedAt() - request.startedAt()) / 1000);
        int distanceM = request.distanceM() == null ? 0 : Math.max(0, request.distanceM());

        // 1) 산책 기록 한 줄 저장
        WalkRecord record = WalkRecord.completed(
                userId, request.courseId(), startedAt, endedAt, durationSeconds, distanceM);
        Long walkRecordId = walkRecordRepository.save(record).getWalkRecordId();

        // 2) 경로 좌표 저장 (같은 트랜잭션이라 하나라도 실패하면 기록도 같이 취소됨)
        saveRoutePoints(walkRecordId, request.points(), startedAt);

        return walkRecordId;
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
}