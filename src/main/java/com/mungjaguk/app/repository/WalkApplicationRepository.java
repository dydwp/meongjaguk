package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.WalkApplication;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkApplicationRepository extends JpaRepository<WalkApplication, Long> {

    List<WalkApplication> findByApplicant_UserIdOrderByCreatedAtDesc(Long userId);

    List<WalkApplication> findByMeetingIdInOrderByCreatedAtDesc(List<Long> meetingIds);

}