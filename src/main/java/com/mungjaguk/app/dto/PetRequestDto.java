package com.mungjaguk.app.dto;

public record PetRequestDto(
        String name,
        String breed,
        Integer age,
        String size,
        String activityLevel) {
}
