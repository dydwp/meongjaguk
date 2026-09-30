package com.mungjaguk.app.security;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

public record OAuthAttributes(
        String provider,
        String providerId,
        String nickname,
        String email,
        String profileImage
) {

    private static final String DEFAULT_NICKNAME = "멍자국회원";

    public static OAuthAttributes of(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId) {
            case "kakao" -> ofKakao(attributes);
            case "naver" -> ofNaver(attributes);
            case "google" -> ofGoogle(attributes);
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "지원하지 않는 로그인 방식입니다: " + registrationId);
        };
    }

    private static OAuthAttributes ofKakao(Map<String, Object> attributes) {
        Map<String, Object> account = asMap(attributes.get("kakao_account"));
        Map<String, Object> profile = asMap(account.get("profile"));
        return new OAuthAttributes(
                "kakao",
                asString(attributes.get("id")),
                nicknameOrDefault(asString(profile.get("nickname"))),
                asString(account.get("email")),
                asString(profile.get("profile_image_url")));
    }

    private static OAuthAttributes ofNaver(Map<String, Object> attributes) {
        Map<String, Object> response = asMap(attributes.get("response"));
        String nickname = asString(response.get("nickname"));
        if (nickname == null || nickname.isBlank()) {
            nickname = asString(response.get("name"));
        }
        return new OAuthAttributes(
                "naver",
                asString(response.get("id")),
                nicknameOrDefault(nickname),
                asString(response.get("email")),
                asString(response.get("profile_image")));
    }

    private static OAuthAttributes ofGoogle(Map<String, Object> attributes) {
        return new OAuthAttributes(
                "google",
                asString(attributes.get("sub")),
                nicknameOrDefault(asString(attributes.get("name"))),
                asString(attributes.get("email")),
                asString(attributes.get("picture")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String nicknameOrDefault(String nickname) {
        return (nickname == null || nickname.isBlank()) ? DEFAULT_NICKNAME : nickname;
    }
}