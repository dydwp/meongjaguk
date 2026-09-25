package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.ApplicationStatus;
import com.mungjaguk.app.entity.WalkApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WalkApplicationRepository extends JpaRepository<WalkApplication, Long> {

    Optional<WalkApplication> findByMeeting_MeetingIdAndUser_UserId(Long meetingId, Long userId);

    boolean existsByMeeting_MeetingIdAndUser_UserId(Long meetingId, Long userId);

    /** 특정 모집의 특정 상태 신청 목록 (신청자 함께 조회, 신청순) */
    @EntityGraph(attributePaths = {"user"})
    List<WalkApplication> findByMeeting_MeetingIdAndStatusOrderByCreatedAtAsc(Long meetingId, ApplicationStatus status);

    /** 여러 모집의 상태별 신청 수: [meetingId, count] */
    @Query("select a.meeting.meetingId, count(a) from WalkApplication a " +
           "where a.meeting.meetingId in :meetingIds and a.status = :status " +
           "group by a.meeting.meetingId")
    List<Object[]> countByMeetingIdsAndStatus(@Param("meetingIds") Collection<Long> meetingIds,
                                              @Param("status") ApplicationStatus status);
}
