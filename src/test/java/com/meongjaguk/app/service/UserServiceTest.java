package com.meongjaguk.app.service;

import com.meongjaguk.app.entity.Role;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.repository.UserRepository;
import com.meongjaguk.app.security.OAuthAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Map;
import java.util.Optional;

import static com.meongjaguk.app.support.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 소셜 로그인 → 회원 조회/가입 */
class UserServiceTest {

    private UserRepository users;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        service = new UserService(users);
    }

    @Test
    void existingMemberLogsInWithoutJoining() {
        User member = user(7L, "용제");
        when(users.findByProviderAndProviderId("kakao", "123")).thenReturn(Optional.of(member));

        assertSame(member, service.loginOrJoin(new OAuthAttributes("kakao", "123", "용제", null, null)));
        verify(users, never()).save(any());
    }

    @Test
    void newMemberJoinsAsActiveUser() {
        when(users.findByProviderAndProviderId("naver", "abc")).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.loginOrJoin(new OAuthAttributes("naver", "abc", "민준", "mj@example.com", "http://img"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User joined = captor.getValue();
        assertEquals("naver", joined.getProvider());
        assertEquals("abc", joined.getProviderId());
        assertEquals("민준", joined.getNickname());
        assertEquals("mj@example.com", joined.getEmail());
        assertEquals(Role.USER, joined.getRole());
        assertEquals("ACTIVE", joined.getStatus());
    }

    @Test
    void unknownMemberIdFails() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.findById(99L));
    }

    // ---------- 소셜 로그인 응답 파싱 (OAuthAttributes) ----------

    @Test
    void kakaoResponseIsParsed() {
        OAuthAttributes attributes = OAuthAttributes.of("kakao", Map.of(
                "id", 123456789L,
                "kakao_account", Map.of(
                        "email", "a@kakao.com",
                        "profile", Map.of("nickname", "카카오회원", "profile_image_url", "http://k/img.png"))));

        assertEquals(new OAuthAttributes("kakao", "123456789", "카카오회원", "a@kakao.com", "http://k/img.png"), attributes);
    }

    @Test
    void naverUsesNameWhenNicknameIsMissing() {
        OAuthAttributes attributes = OAuthAttributes.of("naver", Map.of(
                "response", Map.of("id", "nv-1", "name", "홍길동", "email", "h@naver.com")));

        assertEquals("naver", attributes.provider());
        assertEquals("nv-1", attributes.providerId());
        assertEquals("홍길동", attributes.nickname());
        assertNull(attributes.profileImage());
    }

    @Test
    void googleResponseIsParsed() {
        OAuthAttributes attributes = OAuthAttributes.of("google", Map.of(
                "sub", "g-1", "name", "Google User", "email", "g@gmail.com", "picture", "http://g/pic"));

        assertEquals(new OAuthAttributes("google", "g-1", "Google User", "g@gmail.com", "http://g/pic"), attributes);
    }

    @Test
    void missingNicknameGetsDefault() {
        assertEquals("멍자국회원", OAuthAttributes.of("kakao", Map.of("id", 1)).nickname());
        assertEquals("멍자국회원", OAuthAttributes.of("google", Map.of("sub", "g", "name", " ")).nickname());
    }

    @Test
    void unsupportedProviderIsRejected() {
        assertThrows(OAuth2AuthenticationException.class, () -> OAuthAttributes.of("facebook", Map.of()));
    }
}
