package com.meongjaguk.app.config;

import com.meongjaguk.app.security.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/", "/login").permitAll()
                        // 추가(박용제): Docker·AWS 상태 확인 주소는 로그인 없이 (상태 UP/DOWN 만 응답)
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers("/routes", "/course-detail").permitAll()
                        .requestMatchers("/board", "/course-detail-shared").permitAll()
                        // 추가: 추천 산책로 조회 API는 비회원도 볼 수 있게
                        .requestMatchers(HttpMethod.GET, "/api/courses/**").permitAll()
                        // 추가(김환중): 산책로 게시판 목록/상세/댓글 조회 API는 비회원도 볼 수 있게
                        .requestMatchers(HttpMethod.GET, "/api/meetings/**").permitAll()
                        // 추가(박용제): AI 산책로 추천 중계 API는 비회원도 사용
                        .requestMatchers(HttpMethod.POST, "/api/routes/recommend").permitAll()
                        // 추가(박용제): 추천 산책로 등록 화면/게시글 등록 API는 로그인 회원만
                        .requestMatchers("/board/new").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/meetings").authenticated()
                        .anyRequest().authenticated())
                // AI 추천 API는 저장·변경이 없는 조회용이라 CSRF 토큰 없이 호출 (비회원 화면에서도 사용)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/routes/recommend"))
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"));

        return http.build();
    }
}
