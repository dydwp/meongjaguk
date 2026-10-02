package com.meongjaguk.app.dto;

public record PetRequestDto(
        String name,
        String breed,
        Integer age,
        String size,
        String activityLevel) {
}
