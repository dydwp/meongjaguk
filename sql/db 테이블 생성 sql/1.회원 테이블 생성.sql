-- =========================================================
-- 1. 회원
-- users
-- =========================================================

CREATE TABLE users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,

    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(100) NOT NULL,

    email VARCHAR(255) NULL,
    nickname VARCHAR(50) NOT NULL,
    profile_image VARCHAR(500) NULL,

    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id),

    CONSTRAINT uk_users_provider_provider_id
        UNIQUE (provider, provider_id)
);