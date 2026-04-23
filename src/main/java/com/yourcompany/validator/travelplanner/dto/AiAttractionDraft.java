package com.yourcompany.validator.travelplanner.dto;

import java.util.List;

public record AiAttractionDraft(
        Long id,
        String name,
        String city,
        String category,
        String description,
        Double latitude,
        Double longitude,
        Double rating,
        Integer suggestedHours,
        List<String> tags
) {
}
