package com.mungjaguk.app.repository;

import com.mungjaguk.app.entity.WalkMeetingComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalkMeetingCommentRepository extends JpaRepository<WalkMeetingComment, Long> {

    /** 모집글 댓글 목록: 오래된 순 (작성자 함께 조회) */
    @EntityGraph(attributePaths = {"user"})
    List<WalkMeetingComment> findByMeeting_MeetingIdOrderByCreatedAtAscCommentIdAsc(Long meetingId);
}
