package com.yourcompany.validator.travelplanner.service;

import com.yourcompany.validator.travelplanner.dto.AiAttractionDraft;
import com.yourcompany.validator.travelplanner.dto.AiDestinationDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanDayDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanDraft;
import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import com.yourcompany.validator.travelplanner.dto.SavedPlanSummary;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelPlanningServiceTest {

    @Test
    void shouldCreateStandardPlanFromInMemoryData() {
        TravelPlanningService service = new TravelPlanningService((request, destination, candidates, days) -> {
            throw new UnsupportedOperationException("AI service should not be called");
        });

        PlanResponse response = service.createPlan(new PlanRequest("Shanghai", LocalDate.of(2026, 5, 1), 2, List.of("walk")));

        assertEquals("上海", response.destination().city());
        assertEquals(2, response.days());
        assertEquals(2, response.itinerary().size());
        assertFalse(response.itinerary().get(0).attractions().isEmpty());
    }

    @Test
    void shouldValidateAndFallbackWhenAiDraftIsIncomplete() {
        AiPlanGenerationService fakeAi = (PlanRequest request, Destination destination, List<Attraction> candidates, int days) ->
                new AiPlanDraft(
                        "Tokyo smart route",
                        "AI-generated structure",
                        new AiDestinationDraft("Tokyo", "Japan", 35.6762, 139.6503, "Spring and Autumn", "Tokyo summary"),
                        List.of(
                                new AiPlanDayDraft(1, "Arrival Day", "culture", List.of(6L), List.of(), List.of()),
                                new AiPlanDayDraft(2, "City Energy", "citywalk", List.of(999L), List.of("Shibuya"), List.of())
                        )
                );

        TravelPlanningService service = new TravelPlanningService(fakeAi);
        PlanResponse response = service.createAiPlan(new PlanRequest("Tokyo", LocalDate.of(2026, 5, 2), 2, List.of()));

        assertEquals("东京", response.destination().city());
        assertEquals(2, response.itinerary().size());
        assertEquals("Arrival Day", response.itinerary().get(0).title());
        assertTrue(response.itinerary().get(0).attractions().stream().anyMatch(attraction -> attraction.id().equals(6L)));
        List<SavedPlanSummary> savedPlans = service.getSavedPlans();
        assertEquals(1, savedPlans.size());
        assertNotNull(service.getSavedPlan(response.planId()));
    }

    @Test
    void shouldGenerateCustomDestinationAttractionsFromAi() {
        AiPlanGenerationService fakeAi = (PlanRequest request, Destination destination, List<Attraction> candidates, int days) ->
                new AiPlanDraft(
                        "Singapore 2-day highlights",
                        "Compact first-time itinerary",
                        new AiDestinationDraft("Singapore", "Singapore", 1.3521, 103.8198, "February to April",
                                "A clean and energetic city-state with gardens, food, and waterfront views."),
                        List.of(
                                new AiPlanDayDraft(
                                        1,
                                        "Gardens and Bay",
                                        "landmarks",
                                        List.of(),
                                        List.of(),
                                        List.of(
                                                new AiAttractionDraft(2001L, "Gardens by the Bay", "Singapore", "garden",
                                                        "Supertrees, domes, and bayfront walks.", 1.2816, 103.8636, 4.8, 3,
                                                        List.of("nature", "view", "walk")),
                                                new AiAttractionDraft(2002L, "Marina Bay Sands SkyPark", "Singapore", "viewpoint",
                                                        "Iconic observation deck over Marina Bay.", 1.2834, 103.8607, 4.7, 2,
                                                        List.of("view", "photography"))
                                        )
                                ),
                                new AiPlanDayDraft(
                                        2,
                                        "Culture and Food",
                                        "food",
                                        List.of(),
                                        List.of(),
                                        List.of(
                                                new AiAttractionDraft(2003L, "Chinatown", "Singapore", "citywalk",
                                                        "Historic district with temples and street food.", 1.2838, 103.8439, 4.6, 3,
                                                        List.of("food", "culture", "walk"))
                                        )
                                )
                        )
                );

        TravelPlanningService service = new TravelPlanningService(fakeAi);
        PlanResponse response = service.createAiPlan(new PlanRequest("Singapore", LocalDate.of(2026, 6, 1), 2, List.of("food", "walk")));

        assertEquals("Singapore", response.destination().city());
        assertEquals(2, response.itinerary().size());
        assertTrue(response.itinerary().get(0).attractions().stream().anyMatch(attraction -> "Gardens by the Bay".equals(attraction.name())));
        assertTrue(response.itinerary().get(1).attractions().stream().anyMatch(attraction -> "Chinatown".equals(attraction.name())));
        assertTrue(response.itinerary().stream().flatMap(day -> day.attractions().stream()).allMatch(attraction -> attraction.latitude() != 0));
    }
}
