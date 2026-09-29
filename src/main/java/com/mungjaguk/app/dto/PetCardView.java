package com.mungjaguk.app.dto;

public record PetCardView(
        Long id,
        String name,
        String breed,
        String sizeLabel,
        int ageInYears,
        String profileImage,
        String activityLevelLabel
) {
    public String summaryLine() {
        return breed + " · " + sizeLabel + " · " + ageInYears + "세";
    }
}
