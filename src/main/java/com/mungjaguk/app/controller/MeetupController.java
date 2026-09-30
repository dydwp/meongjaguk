package com.mungjaguk.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 담당 영역: 같이 걷기 게시판
 * 모집 글 목록/상세, 참여 신청/수락/거절 관련 Service/Repository는
 * 이 컨트롤러 기준으로 붙여주세요.
 */
@Controller
public class MeetupController {

    @GetMapping("/board")
    public String board() {
        return "board/list";
    }

    /** 추천 산책로 등록 (공유하기 → 모집 정보 입력): /board/new?route={key} 또는 ?courseId={id} */
    @GetMapping("/board/new")
    public String boardForm() {
        return "board/form";
    }

    /** 산책로 게시글 수정 (작성자만, 같은 입력 화면 사용): /board/edit?meetingId={id} */
    @GetMapping("/board/edit")
    public String boardEditForm() {
        return "board/form";
    }

    @GetMapping("/course-detail-shared")
    public String courseDetailShared() {
        return "board/detail";
    }
}
