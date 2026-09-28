package com.mungjaguk.app.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PetDto(
    Long petId,
    Long userId,
    String name,
    String breed,
    LocalDate birthDate,
    String size,
    String gender,
    String profileImage,
    String introduction,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {
}
