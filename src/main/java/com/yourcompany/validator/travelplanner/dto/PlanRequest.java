package com.yourcompany.validator.travelplanner.dto;

import java.time.LocalDate;
import java.util.List;

public record PlanRequest(
        String city,
        LocalDate startDate,
        int days,
        List<String> interests
) {
}
