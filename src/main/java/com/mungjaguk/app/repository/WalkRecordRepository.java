package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.WalkRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkRecordRepository extends JpaRepository<WalkRecord, Long> {

    List<WalkRecord> findByUser_UserIdOrderByStartedAtDesc(Long userId);

    Optional<WalkRecord> findByIdAndUser_UserId(Long id, Long userId);

}