package com.mungjaguk.app.dto;

import java.time.LocalDateTime;

/**
 * 공유 산책로 댓글
 * hostComment: 모집글 작성자가 쓴 댓글인지 (화면에서 아바타 색 구분용)
 * mine       : 로그인 사용자가 쓴 댓글인지 (삭제 버튼 표시용, 비로그인이면 false)
 */
public record CommentDto(
    Long commentId,
    String authorNickname,
    boolean hostComment,
    boolean mine,
    String content,
    LocalDateTime createdAt) {
}
