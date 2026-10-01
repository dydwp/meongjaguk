package com.meongjaguk.app.controller;

import com.meongjaguk.app.dto.WeeklyWalkSummary;
import com.meongjaguk.app.entity.Route;
import com.meongjaguk.app.service.MainService;
import com.meongjaguk.app.service.PetService;
import com.meongjaguk.app.service.RouteService;
import com.meongjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.meongjaguk.app.support.Fixtures.route;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 화면 연결 컨트롤러: 홈 / 로그인·반려견 프로필 / 추천 산책로 / 같이 걷기 게시판
 * + 비회원이 볼 수 있는 곳과 로그인이 필요한 곳 (SecurityConfig)
 */
@WebMvcTest({HomeController.class, AuthController.class, RouteController.class, MeetupController.class})
class PageControllerTest extends WebTestSupport {

    @MockitoBean MainService mainService;
    @MockitoBean PetService petService;
    @MockitoBean RouteService routeService;

    // ---------- 홈 ----------

    @Test
    void guestHomeHasOnlyMeetCards() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("meetCards"))
                .andExpect(model().attributeDoesNotExist("weeklyWalk", "petWith"));

        verify(mainService).getRecentMeets(3);
        verify(mainService, never()).getWeeklyWalkSummary(anyLong());
    }

    @Test
    void memberHomeHasWeeklyWalkAndPetName() throws Exception {
        WeeklyWalkSummary summary = new WeeklyWalkSummary(3, 5.2, 96, "오늘 2.1km");
        when(mainService.getWeeklyWalkSummary(USER_ID)).thenReturn(summary);
        when(mainService.getPetWith(USER_ID)).thenReturn("보리랑");

        mvc.perform(get("/").with(login()))
                .andExpect(model().attribute("weeklyWalk", summary))
                .andExpect(model().attribute("petWith", "보리랑"));
    }

    // ---------- 로그인 / 반려견 프로필 ----------

    @Test
    void loginPageForGuestButMemberGoesHome() throws Exception {
        mvc.perform(get("/login")).andExpect(view().name("member/login"));
        mvc.perform(get("/login").with(login())).andExpect(redirectedUrl("/"));
    }

    @Test
    void petProfilePage() throws Exception {
        mvc.perform(get("/pet-profile")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/pet-profile").with(login())).andExpect(view().name("dog/profile"));
        mvc.perform(get("/pet-profile").param("id", "1").with(login())).andExpect(view().name("dog/profile"));
        verify(petService).getPetEditView(1L, USER_ID);
    }

    @Test
    void editingOthersPetIs404() throws Exception {
        when(petService.getPetEditView(1L, USER_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mvc.perform(get("/pet-profile").param("id", "1").with(login()))
                .andExpect(status().isNotFound());
    }

    // ---------- 추천 산책로 ----------

    @Test
    void guestCanBrowseCourses() throws Exception {
        Route course = route(3, "한강 코스");
        when(routeService.getCoursesList()).thenReturn(List.of(course));
        when(routeService.getCourseInfo(3L)).thenReturn(course);

        mvc.perform(get("/routes")).andExpect(view().name("course/list"));
        mvc.perform(get("/course-detail")).andExpect(view().name("course/detail"));
        mvc.perform(get("/api/courses/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].courseName").value("한강 코스"));
        mvc.perform(get("/api/courses/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceM").value(2600));
    }

    @Test
    void unknownCourseIs404() throws Exception {
        mvc.perform(get("/api/courses/99")).andExpect(status().isNotFound());
    }

    // ---------- 같이 걷기 게시판 화면 ----------

    @Test
    void guestCanSeeBoardButNotWriteForm() throws Exception {
        mvc.perform(get("/board")).andExpect(view().name("board/list"));
        mvc.perform(get("/course-detail-shared").param("meetingId", "1")).andExpect(view().name("board/detail"));
        mvc.perform(get("/board/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
        mvc.perform(get("/board/new").with(login())).andExpect(view().name("board/form"));
    }

    @Test
    void staticResourcesArePublic() throws Exception {
        mvc.perform(get("/css/style.css")).andExpect(status().isOk());
        mvc.perform(get("/js/app.js")).andExpect(status().isOk());
    }
}
