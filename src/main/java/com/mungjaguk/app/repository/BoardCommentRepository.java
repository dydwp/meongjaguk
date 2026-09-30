package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.BoardComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

    /** 게시글 댓글 목록: 최신순 (작성자 함께 조회) */
    @EntityGraph(attributePaths = {"user"})
    List<BoardComment> findByBoard_MeetingIdOrderByCreatedAtDescCommentIdDesc(Long meetingId);

    /** 모집글 삭제 시 댓글 전부 삭제 */
    @Modifying
    @Query("delete from BoardComment c where c.board.meetingId = :meetingId")
    int deleteByMeetingId(@Param("meetingId") Long meetingId);
}
