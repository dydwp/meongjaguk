package com.meongjaguk.app.controller;

import com.meongjaguk.app.service.CompanionService;
import com.meongjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanionController.class)
class CompanionControllerTest extends WebTestSupport {

    @MockitoBean
    CompanionService companionService;

    @Test
    void applyReturnsPending() throws Exception {
        mvc.perform(post("/api/meetings/10/applications").with(login()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(companionService).apply(10L, USER_ID);
    }

    @Test
    void guestCannotApply() throws Exception {
        mvc.perform(post("/api/meetings/10/applications").with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(companionService, never()).apply(anyLong(), anyLong());
    }

    @Test
    void businessRuleViolationIs409WithMessage() throws Exception {
        doThrow(new IllegalStateException("정원이 다 차서 신청할 수 없어요.")).when(companionService).apply(10L, USER_ID);

        mvc.perform(post("/api/meetings/10/applications").with(login()).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("정원이 다 차서 신청할 수 없어요."));
    }

    @Test
    void missingBoardIs404() throws Exception {
        doThrow(new NoSuchElementException("모집 정보를 찾을 수 없어요.")).when(companionService).apply(99L, USER_ID);

        mvc.perform(post("/api/meetings/99/applications").with(login()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelReturns204() throws Exception {
        mvc.perform(delete("/api/meetings/10/applications").with(login()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(companionService).cancel(10L, USER_ID);
    }

    @Test
    void cancelWithoutRequestIs404() throws Exception {
        doThrow(new NoSuchElementException("신청 내역이 없어요.")).when(companionService).cancel(10L, USER_ID);

        mvc.perform(delete("/api/meetings/10/applications").with(login()).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("신청 내역이 없어요."));
    }
}
