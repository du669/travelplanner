package com.yourcompany.validator.travelplanner.dto;

import java.time.LocalDate;
import java.util.List;

public record PlanRequest(
        String city,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        List<String> interests,
        List<ManualDayRequest> manualDays
) {
}
