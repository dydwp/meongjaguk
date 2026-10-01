package com.meongjaguk.app.view;

import com.meongjaguk.app.controller.HomeController;
import com.meongjaguk.app.dto.MeetCardDto;
import com.meongjaguk.app.dto.WeeklyWalkSummary;
import com.meongjaguk.app.service.MainService;
import com.meongjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 메인 화면(index.html)이 데이터에 맞게 그려지는지 */
@WebMvcTest(HomeController.class)
class HomeViewTest extends WebTestSupport {

    @MockitoBean
    MainService mainService;

    @Test
    void guestSeesDefaultBannerAndLoginLink() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<title>멍자국 — 메인 / 홈</title>"),
                        containsString("오늘은 어디로"),
                        containsString("로그인하면 이번 주 산책 기록을 볼 수 있어요."),
                        containsString("data-login-required=\"true\""),
                        containsString("href=\"/login\""),
                        not(containsString("로그아웃")))));
    }

    @Test
    void memberSeesGreetingWithPetAndWeeklyStats() throws Exception {
        when(mainService.getWeeklyWalkSummary(USER_ID)).thenReturn(new WeeklyWalkSummary(3, 5.25, 96, "오늘 2.1km"));
        when(mainService.getPetWith(USER_ID)).thenReturn("보리랑");

        mvc.perform(get("/").with(login()))
                .andExpect(content().string(allOf(
                        containsString("용제님, 오늘도"),
                        containsString("보리랑 같이 걸어요"),
                        containsString("<b>3</b>회"),
                        containsString("<b>96</b>분"),
                        containsString("이번 주 3회"),
                        containsString("마지막 산책 · 오늘 2.1km"),
                        containsString("data-login-required=\"false\""),
                        containsString("로그아웃"),
                        not(containsString("오늘은 어디로")))));
    }

    @Test
    void memberWithoutPetOrWalks() throws Exception {
        when(mainService.getWeeklyWalkSummary(USER_ID)).thenReturn(new WeeklyWalkSummary(0, 0, 0, null));

        mvc.perform(get("/").with(login()))
                .andExpect(content().string(allOf(
                        containsString(">같이 걸어요<"),
                        containsString("아직 산책 기록이 없어요"))));
    }

    @Test
    void meetCardsShowStatusAndParticipants() throws Exception {
        LocalDateTime future = LocalDateTime.of(2099, 10, 3, 16, 0);
        when(mainService.getRecentMeets(3)).thenReturn(List.of(
                new MeetCardDto(1L, "토요일 한강 산책", "한강 코스", "소형견", future.toLocalDate(), future.toLocalTime(),
                        2, 4, "RECRUITING"),
                new MeetCardDto(2L, "정원 찬 모집", "서울숲 코스", null, future.toLocalDate(), future.toLocalTime(),
                        4, 4, "RECRUITING")));

        mvc.perform(get("/"))
                .andExpect(content().string(allOf(
                        containsString("href=\"/course-detail-shared?meetingId=1\""),
                        containsString("토요일 한강 산책"),
                        containsString("한강 코스 · 소형견"),
                        containsString("2 / 4명"),
                        containsString("width:50%"),
                        containsString(">모집중<"),
                        containsString(">서울숲 코스<"),   // 참여 조건 없으면 코스 이름만
                        containsString(">마감<"),
                        containsString("is-full"),
                        not(containsString("아직 모집 중인 산책이 없어요.")))));
    }

    @Test
    void noMeetCardsShowsEmptyMessage() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("아직 모집 중인 산책이 없어요.")));
    }
}
