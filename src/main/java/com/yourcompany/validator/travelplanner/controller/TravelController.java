package com.yourcompany.validator.travelplanner.controller;

import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;
import com.yourcompany.validator.travelplanner.service.TravelPlanningService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class TravelController {
    private final TravelPlanningService travelPlanningService;

    public TravelController(TravelPlanningService travelPlanningService) {
        this.travelPlanningService = travelPlanningService;
    }

    @GetMapping("/destinations")
    public List<Destination> destinations() {
        return travelPlanningService.getDestinations();
    }

    @GetMapping("/attractions")
    public List<Attraction> attractions(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) List<String> interests
    ) {
        return travelPlanningService.getAttractions(city, interests);
    }

    @PostMapping("/plans")
    public PlanResponse plan(@RequestBody PlanRequest request) {
        return travelPlanningService.createPlan(request);
    }
}
