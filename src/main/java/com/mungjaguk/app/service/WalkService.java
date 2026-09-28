package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.WalkSaveRequest;
import com.mungjaguk.app.entity.WalkRecord;
import com.mungjaguk.app.repository.WalkRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 산책 기록 저장 - 담당: 박용제 */
@Service
@RequiredArgsConstructor
public class WalkService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WalkRecordRepository walkRecordRepository;

    /** 끝난 산책을 저장하고 저장된 기록 번호를 돌려줌 */
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

        WalkRecord record = WalkRecord.completed(
                userId, request.courseId(), startedAt, endedAt, durationSeconds, distanceM);

        return walkRecordRepository.save(record).getWalkRecordId();
    }

    /** 브라우저 밀리초 → 한국 시간 */
    private LocalDateTime toSeoulTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), SEOUL);
    }
}