package com.yourcompany.validator.travelplanner.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SavedPlanSummary(
        Long planId,
        String city,
        LocalDate startDate,
        int days,
        int stopCount,
        LocalDateTime createdAt
) {
}
