package com.yourcompany.validator.travelplanner.dto;

import java.util.List;

public record PlanOptimizeRequest(
        PlanEditRequest plan,
        List<String> interests,
        String instruction
) {
}
