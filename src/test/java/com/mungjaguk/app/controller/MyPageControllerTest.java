package com.mungjaguk.app.controller;

import com.mungjaguk.app.dto.MeetingRequestView;
import com.mungjaguk.app.dto.MyCompanionRequestView;
import com.mungjaguk.app.service.CompanionService;
import com.mungjaguk.app.service.MyPageService;
import com.mungjaguk.app.service.PetService;
import com.mungjaguk.app.service.UserService;
import com.mungjaguk.app.service.WalkRecordService;
import com.mungjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static com.mungjaguk.app.support.Fixtures.user;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MyPageController.class)
class MyPageControllerTest extends WebTestSupport {

    @MockitoBean UserService userService;
    @MockitoBean PetService petService;
    @MockitoBean WalkRecordService walkRecordService;
    @MockitoBean MyPageService myPageService;
    @MockitoBean CompanionService companionService;

    @BeforeEach
    void givenMember() {
        when(userService.findById(USER_ID)).thenReturn(user(USER_ID, "용제"));
    }

    @Test
    void guestIsSentToLogin() throws Exception {
        mvc.perform(get("/mypage")).andExpect(status().is3xxRedirection());
    }

    @Test
    void profileHeaderIsFilled() throws Exception {
        mvc.perform(get("/mypage").with(login()))
                .andExpect(status().isOk())
                .andExpect(view().name("member/mypage"))
                .andExpect(model().attribute("nickname", "용제"))
                .andExpect(model().attribute("avatarInitial", "용"))
                .andExpect(model().attribute("joinedLabel", "2026.09 가입 · 카카오 계정"))
                .andExpect(model().attribute("activeTab", "pets"));
    }

    @Test
    void unknownTabFallsBackToPets() throws Exception {
        mvc.perform(get("/mypage").param("tab", "hack").with(login()))
                .andExpect(model().attribute("activeTab", "pets"));
        mvc.perform(get("/mypage").param("tab", "requests").with(login()))
                .andExpect(model().attribute("activeTab", "requests"));
    }

    @Test
    void requestsAreSplitIntoCurrentAndPast() throws Exception {
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        when(myPageService.getMeetingRequests(USER_ID)).thenReturn(List.of(
                received(1L, future, "PENDING"),
                received(2L, future, "ACCEPTED"),
                received(3L, past, "PENDING")));
        when(myPageService.getMyCompanionRequests(USER_ID)).thenReturn(List.of(
                sent(4L, future, "PENDING"),
                sent(5L, past, "ACCEPTED")));

        mvc.perform(get("/mypage").param("tab", "requests").with(login()))
                .andExpect(model().attribute("meetingRequests", hasSize(2)))
                .andExpect(model().attribute("pastMeetingRequests", hasSize(1)))
                .andExpect(model().attribute("pendingMeetingRequestCount", 1L))
                .andExpect(model().attribute("myCompanionRequests", contains(sent(4L, future, "PENDING"))))
                .andExpect(model().attribute("pastMyCompanionRequests", contains(sent(5L, past, "ACCEPTED"))));
    }

    @Test
    void activityTabFiltersByMyPet() throws Exception {
        when(walkRecordService.isMyPet(USER_ID, 9L)).thenReturn(true);

        mvc.perform(get("/mypage").param("tab", "activity").param("petId", "9").with(login()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedPetId", 9L));

        verify(walkRecordService).getMyWalkHistory(USER_ID, 9L);
    }

    @Test
    void othersPetFilterRedirectsToAllActivity() throws Exception {
        when(walkRecordService.isMyPet(USER_ID, 9L)).thenReturn(false);

        mvc.perform(get("/mypage").param("tab", "activity").param("petId", "9").with(login()))
                .andExpect(redirectedUrl("/mypage?tab=activity"));
    }

    @Test
    void petIdIsIgnoredOutsideActivityTab() throws Exception {
        mvc.perform(get("/mypage").param("tab", "pets").param("petId", "9").with(login()))
                .andExpect(model().attributeDoesNotExist("selectedPetId"));

        verify(walkRecordService, never()).isMyPet(anyLong(), anyLong());
    }

    // ---------- 수락 / 거절 ----------

    @Test
    void acceptShowsSuccessMessage() throws Exception {
        mvc.perform(post("/mypage/requests/5/accept").with(login()).with(csrf()))
                .andExpect(redirectedUrl("/mypage?tab=requests"))
                .andExpect(flash().attribute("successMessage", "참여 신청을 수락했습니다."));

        verify(companionService).acceptForHost(5L, USER_ID);
    }

    @Test
    void acceptFailureShowsReason() throws Exception {
        doThrow(new IllegalStateException("모집 정원이 가득 찼습니다.")).when(companionService).acceptForHost(5L, USER_ID);

        mvc.perform(post("/mypage/requests/5/accept").with(login()).with(csrf()))
                .andExpect(redirectedUrl("/mypage?tab=requests"))
                .andExpect(flash().attribute("errorMessage", "모집 정원이 가득 찼습니다."))
                .andExpect(flash().attributeCount(1));
    }

    @Test
    void rejectShowsRejectMessage() throws Exception {
        mvc.perform(post("/mypage/requests/5/reject").with(login()).with(csrf()))
                .andExpect(redirectedUrl("/mypage?tab=requests"))
                .andExpect(flash().attribute("rejectMessage", "참여 신청을 거절했습니다."));

        verify(companionService).rejectForHost(5L, USER_ID);
    }

    @Test
    void notHostIsForbidden() throws Exception {
        doThrow(new AccessDeniedException("권한 없음")).when(companionService).rejectForHost(5L, USER_ID);

        mvc.perform(post("/mypage/requests/5/reject").with(login()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptWithoutCsrfIsBlocked() throws Exception {
        mvc.perform(post("/mypage/requests/5/accept").with(login()))
                .andExpect(status().isForbidden());

        verify(companionService, never()).acceptForHost(anyLong(), anyLong());
    }

    private static MeetingRequestView received(long id, LocalDateTime at, String status) {
        return new MeetingRequestView(id, 10L, "저녁 산책", "민준", null, at.toLocalDate(), at.toLocalTime(), status);
    }

    private static MyCompanionRequestView sent(long id, LocalDateTime at, String status) {
        return new MyCompanionRequestView(id, 20L, "주말 산책", "한강 코스", "서연", at.toLocalDate(), at.toLocalTime(), status);
    }
}
