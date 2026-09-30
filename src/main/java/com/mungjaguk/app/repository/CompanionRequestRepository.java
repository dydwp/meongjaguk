package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.ApplicationStatus;
import com.mungjaguk.app.entity.CompanionRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanionRequestRepository extends JpaRepository<CompanionRequest, Long> {

    // ---------- 최주영: 마이페이지 ----------

    /** 내가 신청한 동행 목록 (최신순) */
    List<CompanionRequest> findByApplicant_UserIdOrderByCreatedAtDesc(Long userId);

    /** 내 모집글들에 들어온 신청 목록 (최신순) */
    List<CompanionRequest> findByMeetingIdInOrderByCreatedAtDesc(List<Long> meetingIds);

    // ---------- 김환중: 게시판 상세 / 동행 신청·취소 ----------

    Optional<CompanionRequest> findByMeetingIdAndApplicant_UserId(Long meetingId, Long userId);

    boolean existsByMeetingIdAndApplicant_UserId(Long meetingId, Long userId);

    /** 특정 모집글의 특정 상태 신청 목록 (신청자 함께 조회, 신청순) */
    @EntityGraph(attributePaths = {"applicant"})
    List<CompanionRequest> findByMeetingIdAndStatusOrderByCreatedAtAsc(Long meetingId, ApplicationStatus status);

    /** 특정 모집글의 상태별 신청 수 */
    long countByMeetingIdAndStatus(Long meetingId, ApplicationStatus status);

    /** 여러 모집글의 상태별 신청 수: [meetingId, count] */
    @Query("select r.meetingId, count(r) from CompanionRequest r " +
           "where r.meetingId in :meetingIds and r.status = :status " +
           "group by r.meetingId")
    List<Object[]> countByMeetingIdsAndStatus(@Param("meetingIds") Collection<Long> meetingIds,
                                              @Param("status") ApplicationStatus status);

    /** 모집글 삭제 시 신청 전부 삭제 */
    @Modifying
    @Query("delete from CompanionRequest r where r.meetingId = :meetingId")
    int deleteByMeetingId(@Param("meetingId") Long meetingId);
}