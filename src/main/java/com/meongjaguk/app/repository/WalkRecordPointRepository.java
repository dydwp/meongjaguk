package com.meongjaguk.app.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.meongjaguk.app.entity.WalkRecordPoint;

/** 산책 경로 좌표 저장/조회 - 담당: 박용제 */
public interface WalkRecordPointRepository extends JpaRepository<WalkRecordPoint, Long> {

    /** 한 산책의 좌표를 지나간 순서대로 */
    List<WalkRecordPoint> findByWalkRecordIdOrderBySequenceNoAsc(Long walkRecordId);

    /** 특정 산책 기록의 GPS 좌표 전체 삭제 */
    @Modifying
    @Query("delete from WalkRecordPoint p where p.walkRecordId = :walkRecordId")
    void deleteByWalkRecordId(@Param("walkRecordId") Long walkRecordId);
}