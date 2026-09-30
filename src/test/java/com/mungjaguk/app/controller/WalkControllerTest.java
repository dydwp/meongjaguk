package com.mungjaguk.app.controller;

import com.mungjaguk.app.dto.WalkDetailView;
import com.mungjaguk.app.service.WalkRecordService;
import com.mungjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

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

@WebMvcTest(WalkController.class)
class WalkControllerTest extends WebTestSupport {

    @MockitoBean
    WalkRecordService walkRecordService;

    static final WalkDetailView DETAIL = new WalkDetailView(3L, "추천 코스", "개인 산책", true, true, "공원 산책",
            "거리 · 2.3km", "예상 소요시간 · 약 35분", "보리", "2.4km", "38:12", "2026.09.29 완료");

    @Test
    void walkRecordPageNeedsLogin() throws Exception {
        mvc.perform(get("/walk-record")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/walk-record").with(login())).andExpect(view().name("walk/record"));
    }

    @Test
    void activityDetailShowsMyWalk() throws Exception {
        when(walkRecordService.getDetail(3L, USER_ID)).thenReturn(Optional.of(DETAIL));

        mvc.perform(get("/activity-detail").param("id", "3").with(login()))
                .andExpect(status().isOk())
                .andExpect(view().name("member/activity"))
                .andExpect(model().attribute("detail", DETAIL))
                .andExpect(model().attributeExists("walkPoints", "plannedPoints"))
                .andExpect(model().attribute("selectedPetId", (Object) null));
    }

    @Test
    void othersWalkIs404() throws Exception {
        when(walkRecordService.getDetail(3L, USER_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/activity-detail").param("id", "3").with(login()))
                .andExpect(status().isNotFound());
    }

    @Test
    void petFilterIsKeptOnlyForMyPet() throws Exception {
        when(walkRecordService.getDetail(3L, USER_ID)).thenReturn(Optional.of(DETAIL));
        when(walkRecordService.isMyPet(USER_ID, 9L)).thenReturn(true);

        mvc.perform(get("/activity-detail").param("id", "3").param("petId", "9").with(login()))
                .andExpect(model().attribute("selectedPetId", 9L));
        mvc.perform(get("/activity-detail").param("id", "3").param("petId", "10").with(login()))
                .andExpect(model().attribute("selectedPetId", (Object) null));
    }

    @Test
    void deleteGoesBackToActivityTab() throws Exception {
        mvc.perform(post("/activity-detail/3/delete").with(login()).with(csrf()))
                .andExpect(redirectedUrl("/mypage?tab=activity"))
                .andExpect(flash().attribute("activityMessage", "산책 기록을 삭제했습니다."));

        verify(walkRecordService).deleteMyCompletedWalkRecord(3L, USER_ID);
    }

    @Test
    void deleteKeepsMyPetFilter() throws Exception {
        when(walkRecordService.isMyPet(USER_ID, 9L)).thenReturn(true);

        mvc.perform(post("/activity-detail/3/delete").param("petId", "9").with(login()).with(csrf()))
                .andExpect(redirectedUrl("/mypage?tab=activity&petId=9"));
        mvc.perform(post("/activity-detail/3/delete").param("petId", "10").with(login()).with(csrf()))
                .andExpect(redirectedUrl("/mypage?tab=activity"));
    }

    @Test
    void deletingMissingWalkIs404() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .when(walkRecordService).deleteMyCompletedWalkRecord(3L, USER_ID);

        mvc.perform(post("/activity-detail/3/delete").with(login()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteNeedsCsrf() throws Exception {
        mvc.perform(post("/activity-detail/3/delete").with(login()))
                .andExpect(status().isForbidden());

        verify(walkRecordService, never()).deleteMyCompletedWalkRecord(anyLong(), anyLong());
    }
}
