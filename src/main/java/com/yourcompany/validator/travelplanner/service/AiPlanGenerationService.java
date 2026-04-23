package com.yourcompany.validator.travelplanner.service;

import com.yourcompany.validator.travelplanner.dto.AiPlanDraft;
import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;

import java.util.List;

public interface AiPlanGenerationService {
    AiPlanDraft generatePlan(PlanRequest request, Destination destination, List<Attraction> candidates, int days);
}
