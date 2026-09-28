package com.mungjaguk.app.dto;

/**
 * 댓글 등록 요청: { "content": "..." }
 */
public record CommentRequest(String content) {
}
