package com.mungjaguk.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ===== [1] 페이지별 접근 권한 =====
            .authorizeHttpRequests(auth -> auth
                // 정적 파일, 에러 페이지
                .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico", "/error").permitAll()
                // 비회원도 볼 수 있는 화면
                .requestMatchers("/", "/login").permitAll()
                .requestMatchers("/routes", "/course-detail").permitAll()
                .requestMatchers("/board", "/course-detail-shared").permitAll()
                // 나머지는 전부 로그인 필요
                .anyRequest().authenticated()
            )

            // ===== [2] 소셜 로그인 =====
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?error")
            )

            // ===== [3] 로그아웃 =====
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }
}