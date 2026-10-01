package com.meongjaguk.app.support;

import com.meongjaguk.app.config.SecurityConfig;
import com.meongjaguk.app.security.CustomOAuth2UserService;
import com.meongjaguk.app.service.PetImageStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

/**
 * @WebMvcTest 공통 설정 (컨트롤러 / 화면 테스트)
 * - 실제 SecurityConfig 를 올려서 로그인·CSRF 규칙까지 함께 검증
 * - 서비스는 각 테스트에서 @MockitoBean 으로 대신함
 */
@ActiveProfiles("test")
// CI 등 OS 환경변수(SPRING_DATASOURCE_* 등)보다 테스트 설정(H2)을 우선 적용
@TestPropertySource(locations = "classpath:application-test.properties")
@Import({SecurityConfig.class, PetImageStorage.class})
public abstract class WebTestSupport {

    protected static final long USER_ID = 7L;

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected CustomOAuth2UserService customOAuth2UserService;

    /** 7번 회원 "용제"로 로그인한 요청 */
    protected static RequestPostProcessor login() {
        return login(USER_ID, "용제");
    }

    protected static RequestPostProcessor login(long userId, String nickname) {
        return oauth2Login().oauth2User(Fixtures.loginUser(userId, nickname));
    }
}
