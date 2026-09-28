package com.mungjaguk.app.repository;

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
}