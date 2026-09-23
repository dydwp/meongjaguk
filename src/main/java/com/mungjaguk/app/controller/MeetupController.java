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

    @GetMapping("/course-detail-shared")
    public String courseDetailShared() {
        return "board/detail";
    }
}
