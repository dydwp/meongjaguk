package com.mungjaguk.app.view;

import com.mungjaguk.app.controller.AuthController;
import com.mungjaguk.app.controller.MeetupController;
import com.mungjaguk.app.controller.RouteController;
import com.mungjaguk.app.controller.WalkController;
import com.mungjaguk.app.service.PetService;
import com.mungjaguk.app.service.RouteService;
import com.mungjaguk.app.service.WalkRecordService;
import com.mungjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 데이터 없이 JS로 채우는 화면들이 오류 없이 그려지는지 + 공통 레이아웃(헤더·CSRF 메타)
 */
@WebMvcTest({AuthController.class, MeetupController.class, RouteController.class, WalkController.class})
class StaticPagesViewTest extends WebTestSupport {

    @MockitoBean PetService petService;
    @MockitoBean RouteService routeService;
    @MockitoBean WalkRecordService walkRecordService;

    @Test
    void loginPageHasSocialLoginLinks() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("/oauth2/authorization/kakao"),
                        containsString("/oauth2/authorization/naver"),
                        containsString("/oauth2/authorization/google"))));
    }

    @Test
    void guestPagesRender() throws Exception {
        assertRenders("/board", null, "멍자국");
        assertRenders("/course-detail-shared?meetingId=1", null, "멍자국");
        assertRenders("/routes", null, "멍자국");
        assertRenders("/course-detail?id=1", null, "멍자국");
    }

    @Test
    void memberPagesRender() throws Exception {
        assertRenders("/board/new", login(), "용제님");
        assertRenders("/pet-profile", login(), "용제님");
        assertRenders("/walk-record", login(), "용제님");
    }

    @Test
    void layoutHasCsrfMetaForJavaScript() throws Exception {
        mvc.perform(get("/board").with(login()))
                .andExpect(content().string(allOf(
                        containsString("<meta name=\"_csrf\" content=\""),
                        containsString("<meta name=\"_csrf_header\" content=\"X-CSRF-TOKEN\""))));
    }

    @Test
    void headerShowsMemberMenu() throws Exception {
        mvc.perform(get("/board").with(login()))
                .andExpect(content().string(allOf(
                        containsString("용제님"),
                        containsString("action=\"/logout\""),
                        containsString("data-notification-panel"))));
    }

    private void assertRenders(String url, RequestPostProcessor user, String expected) throws Exception {
        var request = get(url);
        if (user != null) {
            request.with(user);
        }
        mvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString(expected)));
    }
}
