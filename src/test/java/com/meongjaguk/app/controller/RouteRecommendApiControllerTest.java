package com.meongjaguk.app.controller;

import com.meongjaguk.app.dto.RouteRecommendRequest;
import com.meongjaguk.app.service.AiRouteClient;
import com.meongjaguk.app.support.WebTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteRecommendApiController.class)
class RouteRecommendApiControllerTest extends WebTestSupport {

    @MockitoBean
    AiRouteClient aiRouteClient;

    private static final String BODY = """
            {"latitude": 37.5444, "longitude": 127.0374, "top_k": 6}
            """;

    @Test
    void guestGetsAiRecommendationWithoutCsrfToken() throws Exception {
        String aiResponse = """
                {"latitude":37.5444,"longitude":127.0374,"count":1,"routes":[{"title":"서울숲 반려견 산책 코스"}]}
                """;
        when(aiRouteClient.recommend(any())).thenReturn(aiResponse);

        mvc.perform(post("/api/routes/recommend")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.routes[0].title").value("서울숲 반려견 산책 코스"));

        ArgumentCaptor<RouteRecommendRequest> captor = ArgumentCaptor.forClass(RouteRecommendRequest.class);
        verify(aiRouteClient).recommend(captor.capture());
        assertEquals(37.5444, captor.getValue().latitude());
        assertEquals(127.0374, captor.getValue().longitude());
        assertEquals(6, captor.getValue().top_k());
    }

    @Test
    void invalidCountIs400AndAiServerIsNotCalled() throws Exception {
        mvc.perform(post("/api/routes/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": 37.5, \"longitude\": 127.0, \"top_k\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("추천 개수는 1~100개까지 요청할 수 있습니다."));

        verify(aiRouteClient, never()).recommend(any());
    }

    @Test
    void missingLocationIs400() throws Exception {
        mvc.perform(post("/api/routes/recommend")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"top_k\": 3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("위치 값이 올바르지 않습니다."));
    }

    @Test
    void aiServerDownIs503() throws Exception {
        when(aiRouteClient.recommend(any())).thenThrow(new ResourceAccessException("Connection refused"));

        mvc.perform(post("/api/routes/recommend")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("산책로 추천 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."));
    }
}
