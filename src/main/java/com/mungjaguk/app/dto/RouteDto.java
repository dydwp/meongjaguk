package com.mungjaguk.app.dto;

import java.time.LocalDateTime;

public record RouteDto(
    Integer courseId,
    String name,
    String description,
    Long distanceM,
    Integer estimatedMinutes,
    String feature,
    String region,
    Double startLatitude,
    Double startLongitude,
    String thumbnailImg,
    LocalDateTime createdAt) {
}
