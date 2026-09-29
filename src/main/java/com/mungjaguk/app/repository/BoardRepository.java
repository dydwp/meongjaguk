package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.Board;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

    /** 최신순 6개 (작성자, 코스 함께 조회) - 메인 페이지 등에서 사용 가능 */
    @EntityGraph(attributePaths = {"host", "course"})
    List<Board> findTop6ByOrderByCreatedAtDescMeetingIdDesc();

    /** 산책로 게시판 무한스크롤 첫 페이지: 게시글 번호 내림차순 */
    @EntityGraph(attributePaths = {"host", "course"})
    List<Board> findByOrderByMeetingIdDesc(Pageable pageable);

    /** 산책로 게시판 무한스크롤 다음 페이지: cursor(마지막으로 본 게시글 번호)보다 작은 것 */
    @EntityGraph(attributePaths = {"host", "course"})
    List<Board> findByMeetingIdLessThanOrderByMeetingIdDesc(Long cursor, Pageable pageable);

    /** 공유 산책로 상세 (작성자, 코스 함께 조회) */
    @EntityGraph(attributePaths = {"host", "course"})
    Optional<Board> findWithHostAndCourseByMeetingId(Long meetingId);
}
