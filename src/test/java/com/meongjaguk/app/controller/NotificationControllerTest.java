package com.meongjaguk.app.controller;

import com.meongjaguk.app.dto.NotificationView;
import com.meongjaguk.app.service.NotificationService;
import com.meongjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest extends WebTestSupport {

    @MockitoBean
    NotificationService notificationService;

    @Test
    void guestIsSentToLogin() throws Exception {
        mvc.perform(get("/api/notifications"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }

    @Test
    void memberGetsUnreadCountAndItems() throws Exception {
        when(notificationService.countUnread(USER_ID)).thenReturn(2L);
        when(notificationService.getRecent(USER_ID)).thenReturn(List.of(
                new NotificationView(3L, "새 동행 신청이 왔어요", "민준님이 신청했어요.", "/mypage?tab=requests",
                        false, LocalDateTime.of(2026, 9, 30, 9, 0))));

        mvc.perform(get("/api/notifications").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.items[0].notificationId").value(3))
                .andExpect(jsonPath("$.items[0].link").value("/mypage?tab=requests"))
                .andExpect(jsonPath("$.items[0].read").value(false));
    }

    @Test
    void markAllReadNeedsCsrfToken() throws Exception {
        mvc.perform(post("/api/notifications/read").with(login()))
                .andExpect(status().isForbidden());
        verify(notificationService, never()).markAllRead(anyLong());
    }

    @Test
    void markAllRead() throws Exception {
        mvc.perform(post("/api/notifications/read").with(login()).with(csrf()))
                .andExpect(status().isNoContent());
        verify(notificationService).markAllRead(USER_ID);
    }
}
