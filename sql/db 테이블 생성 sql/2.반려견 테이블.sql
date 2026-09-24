-- =========================================================
-- 2. 반려견
-- pets
-- =========================================================

CREATE TABLE pets (
    pet_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,

    name VARCHAR(50) NOT NULL,
    breed VARCHAR(50) NULL,
    birth_date DATE NULL,
    size VARCHAR(20) NULL,
    gender VARCHAR(20) NULL,

    profile_image VARCHAR(500) NULL,
    introduction VARCHAR(500) NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (pet_id),

    CONSTRAINT fk_pets_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);