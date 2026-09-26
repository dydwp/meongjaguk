package com.mungjaguk.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(name = "uk_users_provider_provider_id",   // 변경: 팀원 SQL과 같은 이름
                                             columnNames = {"provider", "provider_id"}))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Role role;

    @Column(nullable = false, length = 20)
    private String status;

    // 삭제: lastLoginAt 필드

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)   // 변경: 팀원 SQL처럼 NOT NULL
    private LocalDateTime updatedAt;

    protected User() {}

    public static User create(String provider, String providerId, String nickname,
                              String email, String profileImage) {
        User user = new User();
        user.provider = provider;
        user.providerId = providerId;
        user.nickname = nickname;
        user.email = email;
        user.profileImage = profileImage;
        user.role = Role.USER;
        user.status = "ACTIVE";
        // 삭제: user.lastLoginAt = LocalDateTime.now();
        return user;
    }

    // 삭제: recordLogin() 메서드

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getUserId() { return userId; }
    public String getProvider() { return provider; }
    public String getProviderId() { return providerId; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getProfileImage() { return profileImage; }
    public Role getRole() { return role; }
    public String getStatus() { return status; }
    // 삭제: getLastLoginAt()
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}