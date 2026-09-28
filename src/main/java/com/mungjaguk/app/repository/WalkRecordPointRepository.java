package com.mungjaguk.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mungjaguk.app.entity.WalkRecordPoint;

/** 산책 경로 좌표 저장/조회 - 담당: 박용제 */
public interface WalkRecordPointRepository extends JpaRepository<WalkRecordPoint, Long> {

    /** 한 산책의 좌표를 지나간 순서대로 */
    List<WalkRecordPoint> findByWalkRecordIdOrderBySequenceNoAsc(Long walkRecordId);
}