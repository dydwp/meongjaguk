package com.mungjaguk.app.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mungjaguk.app.entity.WalkRecord;

/** 산책 기록 저장/조회 - 담당: 박용제 */
public interface WalkRecordRepository extends JpaRepository<WalkRecord, Long> {

    /** 내 산책 기록 최신순 */
    List<WalkRecord> findByUserIdOrderByStartedAtDesc(Long userId);

    /** 현재 로그인 사용자의 특정 산책 기록 조회 */
    Optional<WalkRecord> findByWalkRecordIdAndUserId(Long walkRecordId, Long userId);

    /** 메인 "이번 주 나의 산책": 특정 시각 이후 시작한 기록 */
    List<WalkRecord> findByUserIdAndStatusAndStartedAtGreaterThanEqual(Long userId, String status, LocalDateTime from);

    /** 메인 "마지막 산책": 가장 최근 기록 1개 */
    Optional<WalkRecord> findFirstByUserIdAndStatusOrderByStartedAtDesc(Long userId, String status);
}