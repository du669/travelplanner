package com.yourcompany.validator.travelplanner.dto;

import java.time.LocalDate;
import java.util.List;

public record ManualDayRequest(
        int day,
        LocalDate date,
        List<String> attractions
) {
}
