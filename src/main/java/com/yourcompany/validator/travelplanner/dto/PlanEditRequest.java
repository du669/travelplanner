package com.yourcompany.validator.travelplanner.dto;

import com.yourcompany.validator.travelplanner.model.Destination;
import com.yourcompany.validator.travelplanner.model.ItineraryDay;

import java.time.LocalDate;
import java.util.List;

public record PlanEditRequest(
        Long planId,
        Destination destination,
        LocalDate startDate,
        int days,
        List<ItineraryDay> itinerary
) {
}
