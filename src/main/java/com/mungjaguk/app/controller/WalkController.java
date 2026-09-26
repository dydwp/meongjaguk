package com.mungjaguk.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 담당 영역: 산책 기록
 * 산책 시작/종료, 기록 저장, 활동 내역 조회 관련 Service/Repository는
 * 이 컨트롤러 기준으로 붙여주세요.
 */
@Controller
public class WalkController {

    @GetMapping("/walk-record")
    public String walkRecord() {
        return "walk/record";
    }

    @GetMapping("/activity-detail")
    public String activityDetail() {
        return "member/activity";
    }
}
