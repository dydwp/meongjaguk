package com.meongjaguk.app.service;

import com.meongjaguk.app.dto.RouteRecommendRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI 서버(FastAPI) 산책로 추천 호출 - 담당: 박용제
 * 브라우저 대신 Spring 이 서버 안에서 AI 서버를 호출 (배포 환경에서도 같은 주소로 동작, CORS 불필요)
 * 주소: ai.server.url (로컬 기본 http://localhost:8000, Docker 에서는 AI_SERVER_URL=http://ai-server:8000)
 */
@Component
public class AiRouteClient {

    private final RestClient restClient;

    @Autowired
    public AiRouteClient(@Value("${ai.server.url:http://localhost:8000}") String baseUrl) {
        this(RestClient.builder().requestFactory(requestFactory()), baseUrl);
    }

    /** 테스트에서 가짜 AI 서버를 연결할 때 사용 */
    AiRouteClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * 추천 결과 JSON 을 그대로 돌려줌 (화면 JS 가 AI 응답 형식을 그대로 사용하므로 변환하지 않음)
     * AI 서버에 연결할 수 없으면 ResourceAccessException, 오류 응답이면 RestClientResponseException
     */
    public String recommend(RouteRecommendRequest request) {
        return restClient.post()
                .uri("/api/routes/recommend")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        // 추천 개수가 많으면 계산이 오래 걸릴 수 있어 넉넉히
        factory.setReadTimeout(Duration.ofSeconds(60));
        return factory;
    }
}
