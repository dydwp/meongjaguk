package com.meongjaguk.app.dto;

public record PetEditView(
    Long id,
    String name,
    String breed,
    Integer age,
    String size,
    String activityLevel,
    String profileImage) {
}
