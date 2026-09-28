package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.BoardComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardCommentRepository extends JpaRepository<BoardComment, Long> {

    /** 게시글 댓글 목록: 오래된 순 (작성자 함께 조회) */
    @EntityGraph(attributePaths = {"user"})
    List<BoardComment> findByBoard_MeetingIdOrderByCreatedAtAscCommentIdAsc(Long meetingId);
}
