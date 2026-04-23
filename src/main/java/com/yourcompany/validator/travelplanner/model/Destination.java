package com.yourcompany.validator.travelplanner.model;

public record Destination(
        String city,
        String country,
        double latitude,
        double longitude,
        String bestSeason,
        String summary
) {
}
