package com.yourcompany.validator.travelplanner.dto;

import java.time.LocalDate;
import java.util.List;

public record PlanRequest(
        String city,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        List<String> interests,
        List<ManualDayRequest> manualDays,
        Boolean allowSupplementalAttractions
) {
    public PlanRequest(String city, LocalDate startDate, int days, List<String> interests) {
        this(city, startDate, null, days, interests, List.of(), false);
    }

    public PlanRequest(String city,
                       LocalDate startDate,
                       LocalDate endDate,
                       int days,
                       List<String> interests,
                       List<ManualDayRequest> manualDays) {
        this(city, startDate, endDate, days, interests, manualDays, false);
    }
}
