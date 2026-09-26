package com.mungjaguk.app.security;

import com.mungjaguk.app.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class LoginUser implements OAuth2User, Serializable {

    private final Long userId;
    private final String nickname;
    private final Role role;
    private final Map<String, Object> attributes;

    public LoginUser(Long userId, String nickname, Role role, Map<String, Object> attributes) {
        this.userId = userId;
        this.nickname = nickname;
        this.role = role;
        this.attributes = attributes;
    }

    public Long getUserId() { return userId; }
    public String getNickname() { return nickname; }
    public Role getRole() { return role; }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getKey()));
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}