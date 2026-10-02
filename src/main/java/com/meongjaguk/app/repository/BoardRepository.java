package com.meongjaguk.app.repository;

import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.BoardStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BoardRepository extends JpaRepository<Board, Long> {

       /** 최신순 6개 (작성자, 코스 함께 조회) - 메인 페이지 등에서 사용 가능 */
       @EntityGraph(attributePaths = { "host", "course" })
       List<Board> findTop6ByOrderByCreatedAtDescMeetingIdDesc();

       /** 동행 게시판 무한스크롤 첫 페이지: 게시글 번호 내림차순 */
       @EntityGraph(attributePaths = { "host", "course" })
       List<Board> findByOrderByMeetingIdDesc(Pageable pageable);

       /** 동행 게시판 무한스크롤 다음 페이지: cursor(마지막으로 본 게시글 번호)보다 작은 것 */
       @EntityGraph(attributePaths = { "host", "course" })
       List<Board> findByMeetingIdLessThanOrderByMeetingIdDesc(Long cursor, Pageable pageable);

       /** 공유 산책로 상세 (작성자, 코스 함께 조회) */
       @EntityGraph(attributePaths = { "host", "course" })
       Optional<Board> findWithHostAndCourseByMeetingId(Long meetingId);

       /**
        * 모임 일시가 지난 모집 중(RECRUITING) 게시글을 한 번에 모집 마감(CLOSED)으로 변경
        * - 모임 시작 시각이 되면 지난 것으로 봄 (Board.isMeetingTimePassed와 같은 기준)
        * 
        * @return 변경된 게시글 수
        */
       @Modifying
       @Query("update Board b set b.status = :closed, b.updatedAt = :now " +
                     "where b.status = :recruiting " +
                     "and (b.meetingDate < :today or (b.meetingDate = :today and b.meetingTime <= :time))")
       int closeExpired(@Param("recruiting") BoardStatus recruiting,
                     @Param("closed") BoardStatus closed,
                     @Param("today") LocalDate today,
                     @Param("time") LocalTime time,
                     @Param("now") LocalDateTime now);
}
