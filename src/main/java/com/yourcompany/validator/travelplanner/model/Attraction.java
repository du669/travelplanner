package com.yourcompany.validator.travelplanner.model;

import java.util.List;

public record Attraction(
        Long id,
        String name,
        String city,
        String category,
        String description,
        double latitude,
        double longitude,
        double rating,
        int suggestedHours,
        List<String> tags
) {
}
