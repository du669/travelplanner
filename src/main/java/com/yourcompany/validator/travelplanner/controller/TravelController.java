package com.yourcompany.validator.travelplanner.controller;

import com.yourcompany.validator.travelplanner.config.AmapProperties;
import com.yourcompany.validator.travelplanner.dto.AmapConfigResponse;
import com.yourcompany.validator.travelplanner.dto.PlanEditRequest;
import com.yourcompany.validator.travelplanner.dto.PlanOptimizeRequest;
import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import com.yourcompany.validator.travelplanner.dto.PlanRouteResponse;
import com.yourcompany.validator.travelplanner.dto.SavedPlanSummary;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;
import com.yourcompany.validator.travelplanner.service.AmapRoutingService;
import com.yourcompany.validator.travelplanner.service.AiPlanStreamingService;
import com.yourcompany.validator.travelplanner.service.StandardPlanStreamingService;
import com.yourcompany.validator.travelplanner.service.TravelPlanningService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"})
public class TravelController {
    private final TravelPlanningService travelPlanningService;
    private final AmapRoutingService amapRoutingService;
    private final AiPlanStreamingService aiPlanStreamingService;
    private final StandardPlanStreamingService standardPlanStreamingService;
    private final AmapProperties amapProperties;

    public TravelController(TravelPlanningService travelPlanningService,
                            AmapRoutingService amapRoutingService,
                            AiPlanStreamingService aiPlanStreamingService,
                            StandardPlanStreamingService standardPlanStreamingService,
                            AmapProperties amapProperties) {
        this.travelPlanningService = travelPlanningService;
        this.amapRoutingService = amapRoutingService;
        this.aiPlanStreamingService = aiPlanStreamingService;
        this.standardPlanStreamingService = standardPlanStreamingService;
        this.amapProperties = amapProperties;
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

    @PostMapping("/plans/edit")
    public PlanResponse applyEdits(@RequestBody PlanEditRequest request) {
        return travelPlanningService.applyEdits(request);
    }

    @PostMapping("/plans/routes-preview")
    public PlanRouteResponse previewRoutes(@RequestBody PlanEditRequest request) {
        PlanResponse normalizedPlan = travelPlanningService.previewEditedPlan(request);
        return amapRoutingService.buildRoutes(normalizedPlan);
    }

    @PostMapping(value = "/plans/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter planStream(@RequestBody PlanRequest request) {
        return standardPlanStreamingService.streamPlan(request);
    }

    @PostMapping("/ai/plans")
    public PlanResponse aiPlan(@RequestBody PlanRequest request) {
        return travelPlanningService.createAiPlan(request);
    }

    @PostMapping("/ai/plans/optimize")
    public PlanResponse optimizePlan(@RequestBody PlanOptimizeRequest request) {
        return travelPlanningService.optimizeEditedPlan(request);
    }

    @PostMapping(value = "/ai/plans/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiPlanStream(@RequestBody PlanRequest request) {
        return aiPlanStreamingService.streamPlan(request);
    }

    @GetMapping("/plans")
    public List<SavedPlanSummary> savedPlans() {
        return travelPlanningService.getSavedPlans();
    }

    @GetMapping("/map/config")
    public AmapConfigResponse amapConfig() {
        return new AmapConfigResponse(
                amapProperties.getWebKey() != null && !amapProperties.getWebKey().isBlank(),
                amapProperties.getWebKey(),
                amapProperties.getWebSecurityCode()
        );
    }

    @GetMapping("/plans/{id}")
    public PlanResponse savedPlan(@PathVariable Long id) {
        return travelPlanningService.getSavedPlan(id);
    }

    @GetMapping("/plans/{id}/routes")
    public PlanRouteResponse savedPlanRoutes(@PathVariable Long id) {
        return amapRoutingService.buildRoutes(travelPlanningService.getSavedPlan(id));
    }
}
