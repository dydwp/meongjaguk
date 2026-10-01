package com.meongjaguk.app.service;

import com.meongjaguk.app.dto.RouteRecommendRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** AI 서버 대신 가짜 서버(MockRestServiceServer)로 요청 주소·본문과 응답 전달을 확인 */
class AiRouteClientTest {

    private MockRestServiceServer aiServer;
    private AiRouteClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        aiServer = MockRestServiceServer.bindTo(builder).build();
        client = new AiRouteClient(builder, "http://ai-server:8000");
    }

    @Test
    void sendsLocationToAiServerAndReturnsResponseAsIs() {
        String aiResponse = "{\"count\":1,\"routes\":[{\"title\":\"서울숲 반려견 산책 코스\"}]}";
        aiServer.expect(requestTo("http://ai-server:8000/api/routes/recommend"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"latitude\":37.5444,\"longitude\":127.0374,\"top_k\":3}"))
                .andRespond(withSuccess(aiResponse, MediaType.APPLICATION_JSON));

        String result = client.recommend(new RouteRecommendRequest(37.5444, 127.0374, 3));

        assertEquals(aiResponse, result);
        aiServer.verify();
    }

    @Test
    void aiServerErrorIsThrown() {
        aiServer.expect(requestTo("http://ai-server:8000/api/routes/recommend"))
                .andRespond(withServerError());

        assertThrows(RestClientResponseException.class,
                () -> client.recommend(new RouteRecommendRequest(37.5, 127.0, 3)));
    }
}
