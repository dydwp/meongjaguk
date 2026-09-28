package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.WalkRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 산책 기록 저장/조회 - 담당: 박용제 */
public interface WalkRecordRepository extends JpaRepository<WalkRecord, Long> {

    /** 내 산책 기록 최신순 (마이페이지·활동 상세에서도 사용 가능) */
    List<WalkRecord> findByUserIdOrderByStartedAtDesc(Long userId);
}