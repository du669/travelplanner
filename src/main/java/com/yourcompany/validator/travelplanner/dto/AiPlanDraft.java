package com.yourcompany.validator.travelplanner.dto;

import java.util.List;

public record AiPlanDraft(
        String title,
        String summary,
        AiDestinationDraft destination,
        List<AiPlanDayDraft> days
) {
}
