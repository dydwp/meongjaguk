package com.mungjaguk.app.controller;

import com.mungjaguk.app.dto.BoardDetailDto;
import com.mungjaguk.app.dto.BoardPageDto;
import com.mungjaguk.app.dto.CommentDto;
import com.mungjaguk.app.dto.CommentRequest;
import com.mungjaguk.app.security.LoginUser;
import com.mungjaguk.app.service.BoardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 산책로 게시판 API: 목록/상세 조회, 댓글 조회/작성/삭제
 * (화면 연결 /board, /course-detail-shared 는 MeetupController)
 */
@RestController
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    /**
     * 산책로 게시판 목록 (무한스크롤)
     * 예) /api/meetings?size=6 → 첫 페이지, /api/meetings?cursor=12&size=6 → 12번보다 오래된 글
     */
    @GetMapping("/api/meetings")
    public ResponseEntity<BoardPageDto> boards(@RequestParam(required = false) Long cursor,
                                               @RequestParam(defaultValue = "6") int size) {
        int pageSize = Math.max(1, Math.min(size, 30));
        return ResponseEntity.ok(boardService.getBoards(cursor, pageSize));
    }

    /** 공유 산책로 상세 */
    @GetMapping("/api/meetings/{meetingId}")
    public ResponseEntity<BoardDetailDto> board(@PathVariable Long meetingId,
                                                @AuthenticationPrincipal LoginUser loginUser) {
        Long loginUserId = loginUser != null ? loginUser.getUserId() : null;
        return ResponseEntity.ok(boardService.getBoard(meetingId, loginUserId));
    }

    /** 댓글 목록 (비회원도 조회 가능) */
    @GetMapping("/api/meetings/{meetingId}/comments")
    public ResponseEntity<List<CommentDto>> comments(@PathVariable Long meetingId,
                                                     @AuthenticationPrincipal LoginUser loginUser) {
        Long loginUserId = loginUser != null ? loginUser.getUserId() : null;
        return ResponseEntity.ok(boardService.getComments(meetingId, loginUserId));
    }

    /** 댓글 작성 (로그인 회원만) */
    @PostMapping("/api/meetings/{meetingId}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long meetingId,
                                        @RequestBody CommentRequest request,
                                        @AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요해요."));
        }
        CommentDto saved = boardService.addComment(meetingId, loginUser.getUserId(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /** 댓글 삭제 (본인 댓글만) */
    @DeleteMapping("/api/meetings/{meetingId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long meetingId,
                                              @PathVariable Long commentId,
                                              @AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boardService.deleteComment(meetingId, commentId, loginUser.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ---------- 예외 처리 (이 컨트롤러에만 적용) ----------

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
    }
}
