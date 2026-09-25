package com.mungjaguk.app.controller;

import com.mungjaguk.app.dto.MeetingCardDto;
import com.mungjaguk.app.dto.MeetingDetailDto;
import com.mungjaguk.app.security.LoginUser;
import com.mungjaguk.app.service.MeetupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 담당 영역: 같이 걷기 게시판
 * 모집 글 목록/상세, 참여 신청/수락/거절 관련 Service/Repository는
 * 이 컨트롤러 기준으로 붙여주세요.
 */
@Controller
public class MeetupController {

    private final MeetupService meetupService;

    public MeetupController(MeetupService meetupService) {
        this.meetupService = meetupService;
    }

    // ---------- 화면 ----------

    @GetMapping("/board")
    public String board() {
        return "board/list";
    }

    /** 공유 산책로 상세: /course-detail-shared?meetingId={id} (데이터는 JS에서 API로 조회) */
    @GetMapping("/course-detail-shared")
    public String courseDetailShared() {
        return "board/detail";
    }

    // ---------- API ----------

    /** 산책로 게시판 목록 (최신순 6개) */
    @GetMapping("/api/meetings")
    public ResponseEntity<List<MeetingCardDto>> meetings() {
        return ResponseEntity.ok(meetupService.getRecentMeetings());
    }

    /** 공유 산책로 상세 */
    @GetMapping("/api/meetings/{meetingId}")
    public ResponseEntity<MeetingDetailDto> meeting(@PathVariable Long meetingId,
                                                    @AuthenticationPrincipal LoginUser loginUser) {
        Long loginUserId = loginUser != null ? loginUser.getUserId() : null;
        return ResponseEntity.ok(meetupService.getMeeting(meetingId, loginUserId));
    }

    /** 동행 신청 */
    @PostMapping("/api/meetings/{meetingId}/applications")
    public ResponseEntity<Map<String, String>> apply(@PathVariable Long meetingId,
                                                     @AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요해요."));
        }
        meetupService.apply(meetingId, loginUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "PENDING"));
    }

    /** 동행 신청 취소 */
    @DeleteMapping("/api/meetings/{meetingId}/applications")
    public ResponseEntity<Void> cancel(@PathVariable Long meetingId,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        meetupService.cancel(meetingId, loginUser.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ---------- 예외 처리 (이 컨트롤러에만 적용) ----------

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleBadState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }
}
