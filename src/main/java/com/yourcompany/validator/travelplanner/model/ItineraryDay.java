package com.yourcompany.validator.travelplanner.model;

import java.util.List;

public record ItineraryDay(
        int day,
        String title,
        String theme,
        double distanceKm,
        List<Attraction> attractions
) {
}
