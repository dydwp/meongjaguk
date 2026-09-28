package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.Board;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    /** 산책로 게시판: 최신순 6개 (작성자, 코스 함께 조회) */
    @EntityGraph(attributePaths = {"host", "course"})
    List<Board> findTop6ByOrderByCreatedAtDescMeetingIdDesc();

    /** 공유 산책로 상세 (작성자, 코스 함께 조회) */
    @EntityGraph(attributePaths = {"host", "course"})
    Optional<Board> findWithHostAndCourseByMeetingId(Long meetingId);
}
