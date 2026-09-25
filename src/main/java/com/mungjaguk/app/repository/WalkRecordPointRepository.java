package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.WalkRecordPoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkRecordPointRepository extends JpaRepository<WalkRecordPoint, Long> {

    List<WalkRecordPoint> findByWalkRecord_IdOrderBySequenceNoAsc(Long walkRecordId);

    List<WalkRecordPoint> findByWalkRecord_IdAndWalkRecord_User_UserIdOrderBySequenceNoAsc(Long walkRecordId, Long userId);

}