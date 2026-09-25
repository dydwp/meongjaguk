package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.WalkMeeting;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalkMeetingRepository extends JpaRepository<WalkMeeting, Long> {

    /** 산책로 게시판: 최신순 6개 (작성자, 코스 함께 조회) */
    @EntityGraph(attributePaths = {"host", "course"})
    List<WalkMeeting> findTop6ByOrderByCreatedAtDescMeetingIdDesc();

    /** 공유 산책로 상세 (작성자, 코스 함께 조회) */
    @EntityGraph(attributePaths = {"host", "course"})
    Optional<WalkMeeting> findWithHostAndCourseByMeetingId(Long meetingId);
}
