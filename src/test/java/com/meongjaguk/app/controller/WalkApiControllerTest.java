package com.meongjaguk.app.controller;

import com.meongjaguk.app.dto.WalkSaveRequest;
import com.meongjaguk.app.service.WalkService;
import com.meongjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalkApiController.class)
class WalkApiControllerTest extends WebTestSupport {

    @MockitoBean
    WalkService walkService;

    private static final String BODY = """
            {"startedAt": 1700000000000, "endedAt": 1700000600000, "distanceM": 850,
             "points": [{"lat": 37.5, "lng": 127.0, "t": 1700000000000}]}
            """;

    @Test
    void savedWalkIdIsReturned() throws Exception {
        when(walkService.saveCompletedWalk(eq(USER_ID), any())).thenReturn(42L);

        mvc.perform(post("/api/walks").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walkRecordId").value(42));

        ArgumentCaptor<WalkSaveRequest> captor = ArgumentCaptor.forClass(WalkSaveRequest.class);
        verify(walkService).saveCompletedWalk(eq(USER_ID), captor.capture());
        assertEquals(850, captor.getValue().distanceM());
        assertEquals(1, captor.getValue().points().size());
        assertEquals(37.5, captor.getValue().points().get(0).lat());
    }

    @Test
    void invalidWalkIs400WithReason() throws Exception {
        when(walkService.saveCompletedWalk(eq(USER_ID), any()))
                .thenThrow(new IllegalArgumentException("산책 시간이 올바르지 않습니다."));

        mvc.perform(post("/api/walks").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("산책 시간이 올바르지 않습니다."));
    }

    @Test
    void guestWalkIsNotSaved() throws Exception {
        mvc.perform(post("/api/walks").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is3xxRedirection());

        verify(walkService, never()).saveCompletedWalk(anyLong(), any());
    }

    // ---------- 동행 산책 모집글 번호 (추가: 김환중) ----------

    @Test
    void meetingIdInJsonIsPassedToService() throws Exception {
        String body = """
                {"startedAt": 1700000000000, "endedAt": 1700000600000, "distanceM": 850,
                 "points": [{"lat": 37.5, "lng": 127.0, "t": 1700000000000}], "meetingId": 10}
                """;

        mvc.perform(post("/api/walks").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<WalkSaveRequest> captor = ArgumentCaptor.forClass(WalkSaveRequest.class);
        verify(walkService).saveCompletedWalk(eq(USER_ID), captor.capture());
        assertEquals(10L, captor.getValue().meetingId());
        assertEquals(850, captor.getValue().distanceM());
    }

    @Test
    void missingMeetingIdIsNullAndOtherValuesAreKept() throws Exception {
        mvc.perform(post("/api/walks").with(login()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<WalkSaveRequest> captor = ArgumentCaptor.forClass(WalkSaveRequest.class);
        verify(walkService).saveCompletedWalk(eq(USER_ID), captor.capture());
        WalkSaveRequest request = captor.getValue();
        assertNull(request.meetingId());
        assertNull(request.courseId());
        assertEquals(1700000000000L, request.startedAt());
        assertEquals(1700000600000L, request.endedAt());
        assertEquals(850, request.distanceM());
        assertEquals(1, request.points().size());
        assertEquals(37.5, request.points().get(0).lat());
        assertNull(request.recommendedRoute());
        assertNull(request.petIds());
    }
}
