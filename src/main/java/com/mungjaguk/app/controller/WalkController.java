package com.mungjaguk.app.controller;

import com.mungjaguk.app.dto.WalkDetailView;
import com.mungjaguk.app.security.LoginUser;
import com.mungjaguk.app.service.WalkRecordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * 담당 영역: 산책 기록
 * 산책 시작/종료, 기록 저장, 활동 내역 조회 관련 Service/Repository는
 * 이 컨트롤러 기준으로 붙여주세요.
 */
@Controller
public class WalkController {

    private final WalkRecordService walkRecordService;
    private final String kakaoMapsJavaScriptKey;

    public WalkController(WalkRecordService walkRecordService,
                        @Value("${kakao.maps.javascript-key:}") String kakaoMapsJavaScriptKey) {
        this.walkRecordService = walkRecordService;
        this.kakaoMapsJavaScriptKey = kakaoMapsJavaScriptKey;
    }

    @GetMapping("/walk-record")
    public String walkRecord() {
        return "walk/record";
    }

    @GetMapping("/activity-detail")
    public String activityDetail(@RequestParam Long id,
                                 @AuthenticationPrincipal LoginUser loginUser,
                                 Model model) {
        Long userId = loginUser.getUserId();

        WalkDetailView detail = walkRecordService.getDetail(id, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "산책 기록을 찾을 수 없습니다."
                ));

        model.addAttribute("detail", detail);
        model.addAttribute("walkPoints", walkRecordService.getWalkPoints(id, userId));
        model.addAttribute("kakaoMapsJavaScriptKey", kakaoMapsJavaScriptKey);

        return "member/activity";
    }
}