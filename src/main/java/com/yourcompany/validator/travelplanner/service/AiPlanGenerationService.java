package com.yourcompany.validator.travelplanner.service;

import com.yourcompany.validator.travelplanner.dto.AiPlanDraft;
import com.yourcompany.validator.travelplanner.dto.PlanOptimizeRequest;
import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;

import java.util.List;

public interface AiPlanGenerationService {
    AiPlanDraft generatePlan(PlanRequest request, Destination destination, List<Attraction> candidates, int days);

    default AiPlanDraft optimizePlan(PlanOptimizeRequest request,
                                     Destination destination,
                                     List<Attraction> candidates,
                                     int days) {
        PlanRequest fallbackRequest = new PlanRequest(
                destination.city(),
                request.plan() == null ? null : request.plan().startDate(),
                null,
                days,
                request.interests(),
                List.of(),
                false
        );
        return generatePlan(fallbackRequest, destination, candidates, days);
    }
}
