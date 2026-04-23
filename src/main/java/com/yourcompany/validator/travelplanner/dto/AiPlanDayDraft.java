package com.yourcompany.validator.travelplanner.dto;

import java.util.List;

public record AiPlanDayDraft(
        int day,
        String title,
        String theme,
        List<Long> attractionIds,
        List<String> attractionNames
) {
}
