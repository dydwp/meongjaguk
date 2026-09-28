package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.WalkRecordPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 산책 경로 좌표 저장/조회 - 담당: 박용제 */
public interface WalkRecordPointRepository extends JpaRepository<WalkRecordPoint, Long> {

    /** 한 산책의 좌표를 지나간 순서대로 (지도에 경로 그릴 때 사용) */
    List<WalkRecordPoint> findByWalkRecordIdOrderBySequenceNoAsc(Long walkRecordId);
}