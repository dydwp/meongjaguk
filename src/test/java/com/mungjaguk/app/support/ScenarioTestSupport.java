package com.mungjaguk.app.support;

import com.jayway.jsonpath.JsonPath;
import com.mungjaguk.app.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

/**
 * 시나리오 테스트 공통 설정
 * - 전체 스프링 + H2 + 실제 보안 설정으로 HTTP 요청을 순서대로 보냄
 * - 요청마다 커밋되므로 테스트 시작 전에 DB를 비움
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ScenarioTestSupport {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected TestDataFactory data;

    @BeforeEach
    void cleanDatabase() {
        data.cleanAll();
    }

    protected static RequestPostProcessor as(User user) {
        return oauth2Login().oauth2User(Fixtures.loginUser(user.getUserId(), user.getNickname()));
    }

    protected static String body(MvcResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    protected static <T> T json(MvcResult result, String path) {
        return JsonPath.read(body(result), path);
    }
}
