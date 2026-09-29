package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.WalkRecordPlannedPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WalkRecordPlannedPointRepository extends JpaRepository<WalkRecordPlannedPoint, Long> {
    List<WalkRecordPlannedPoint> findByWalkRecordIdOrderBySequenceNoAsc(Long walkRecordId);

    @Modifying
    @Query("delete from WalkRecordPlannedPoint p where p.walkRecordId = :walkRecordId")
    void deleteByWalkRecordId(@Param("walkRecordId") Long walkRecordId);
}
