package com.mungjaguk.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "member",
       uniqueConstraints = @UniqueConstraint(name = "uk_member_provider",
                                             columnNames = {"provider", "provider_id"}))
public class Member {

    // ===== [1] 필드 = 테이블 컬럼 =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(length = 100)
    private String email;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    // ===== [2] 생성 방법 =====

    protected Member() {}

    public static Member create(String provider, String providerId, String nickname,
                                String email, String profileImage) {
        Member m = new Member();
        m.provider = provider;
        m.providerId = providerId;
        m.nickname = nickname;
        m.email = email;
        m.profileImage = profileImage;
        m.role = Role.USER;
        m.createdAt = LocalDateTime.now();
        m.lastLoginAt = m.createdAt;
        return m;
    }

    public void recordLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    // ===== [3] Getter =====

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public String getProfileImage() { return profileImage; }
    public Role getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}