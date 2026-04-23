package com.yourcompany.validator.travelplanner.dto;

public record AiPlanStreamEvent(
        String type,
        int progress,
        String message,
        PlanResponse preview,
        PlanResponse plan
) {
}
