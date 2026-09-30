package com.meongjaguk.app.dto;

import java.util.List;

/**
 * 산책로 게시판 무한스크롤 응답
 * - items     : 이번에 불러온 카드
 * - hasNext   : 더 불러올 게시글이 있는지
 * - nextCursor: 다음 요청에 넘길 커서 (이번 목록의 마지막 게시글 번호, 없으면 null)
 */
public record BoardPageDto(
    List<BoardCardDto> items,
    boolean hasNext,
    Long nextCursor) {
}
