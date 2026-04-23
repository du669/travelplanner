package com.yourcompany.validator.travelplanner.dto;

public record AiDestinationDraft(
        String city,
        String country,
        Double latitude,
        Double longitude,
        String bestSeason,
        String summary
) {
}
