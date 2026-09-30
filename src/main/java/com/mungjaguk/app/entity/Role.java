package com.mungjaguk.app.entity;

public enum Role {
    USER,
    ADMIN;

    public String getKey() {
        return "ROLE_" + name();
    }
}