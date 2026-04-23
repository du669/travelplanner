package com.yourcompany.validator.travelplanner.service;

import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;
import com.yourcompany.validator.travelplanner.model.ItineraryDay;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TravelPlanningService {
    private final List<Destination> destinations = List.of(
            new Destination("Paris", "France", 48.8566, 2.3522, "April - June, September - October", "Art, food and classic European streets made for slow walking."),
            new Destination("Shanghai", "China", 31.2304, 121.4737, "March - May, October - November", "A sharp mix of skyline views, river walks, gardens and contemporary culture."),
            new Destination("Chengdu", "China", 30.5728, 104.0668, "March - June, September - November", "Relaxed tea houses, spicy food, pandas and gateways to Sichuan scenery.")
    );

    private final List<Attraction> attractions = List.of(
            new Attraction(1L, "Louvre Museum", "Paris", "Culture", "World-class art collection in a former royal palace.", 48.8606, 2.3376, 4.8, 4, List.of("museum", "art", "history")),
            new Attraction(2L, "Eiffel Tower", "Paris", "Landmark", "Iconic tower with panoramic city views.", 48.8584, 2.2945, 4.7, 2, List.of("view", "landmark", "photo")),
            new Attraction(3L, "Montmartre", "Paris", "Neighborhood", "Hilltop streets, artists, cafes and the Sacre-Coeur skyline.", 48.8867, 2.3431, 4.6, 3, List.of("walk", "food", "photo")),
            new Attraction(4L, "Seine River Walk", "Paris", "Nature", "Scenic riverside route linking bridges and historic quarters.", 48.8589, 2.3470, 4.5, 2, List.of("walk", "relax", "view")),
            new Attraction(5L, "The Bund", "Shanghai", "Landmark", "Historic waterfront facing the Pudong skyline.", 31.2400, 121.4900, 4.8, 2, List.of("view", "photo", "night")),
            new Attraction(6L, "Yu Garden", "Shanghai", "Culture", "Classical garden with pavilions, ponds and old-city lanes.", 31.2272, 121.4921, 4.5, 2, List.of("garden", "history", "walk")),
            new Attraction(7L, "Shanghai Museum", "Shanghai", "Culture", "Chinese art, bronzes, ceramics and calligraphy near People's Square.", 31.2303, 121.4706, 4.6, 3, List.of("museum", "art", "history")),
            new Attraction(8L, "Tianzifang", "Shanghai", "Neighborhood", "Dense alleyways with cafes, small shops and creative studios.", 31.2108, 121.4670, 4.3, 2, List.of("food", "shopping", "walk")),
            new Attraction(9L, "Chengdu Research Base of Giant Panda Breeding", "Chengdu", "Nature", "Morning panda viewing and conservation exhibits.", 30.7383, 104.1470, 4.8, 3, List.of("nature", "family", "photo")),
            new Attraction(10L, "Wide and Narrow Alleys", "Chengdu", "Neighborhood", "Restored lanes for snacks, tea houses and local crafts.", 30.6736, 104.0568, 4.4, 2, List.of("food", "walk", "shopping")),
            new Attraction(11L, "Wuhou Shrine", "Chengdu", "Culture", "Historic temple complex tied to Three Kingdoms culture.", 30.6453, 104.0497, 4.5, 2, List.of("history", "temple", "culture")),
            new Attraction(12L, "People's Park", "Chengdu", "Relax", "Tea houses, gardens and a very local slow afternoon.", 30.6574, 104.0633, 4.4, 2, List.of("tea", "relax", "walk"))
    );

    public List<Destination> getDestinations() {
        return destinations;
    }

    public List<Attraction> getAttractions(String city, List<String> interests) {
        Set<String> normalizedInterests = normalize(interests);
        return attractions.stream()
                .filter(attraction -> city == null || city.isBlank() || attraction.city().equalsIgnoreCase(city))
                .filter(attraction -> normalizedInterests.isEmpty() || matchesInterest(attraction, normalizedInterests))
                .sorted(Comparator.comparing(Attraction::rating).reversed())
                .toList();
    }

    public PlanResponse createPlan(PlanRequest request) {
        String city = request.city() == null || request.city().isBlank() ? destinations.get(0).city() : request.city();
        int days = Math.max(1, Math.min(request.days(), 7));
        LocalDate startDate = request.startDate() == null ? LocalDate.now() : request.startDate();
        Destination destination = destinations.stream()
                .filter(item -> item.city().equalsIgnoreCase(city))
                .findFirst()
                .orElse(destinations.get(0));

        List<Attraction> candidates = getAttractions(destination.city(), request.interests());
        if (candidates.isEmpty()) {
            candidates = getAttractions(destination.city(), List.of());
        }

        List<ItineraryDay> itinerary = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            List<Attraction> dayAttractions = pickForDay(candidates, day);
            itinerary.add(new ItineraryDay(
                    day,
                    "Day " + day + " - " + destination.city(),
                    buildTheme(dayAttractions),
                    estimateDistance(dayAttractions),
                    dayAttractions
            ));
        }

        return new PlanResponse(destination, startDate, days, itinerary);
    }

    private List<Attraction> pickForDay(List<Attraction> candidates, int day) {
        int start = ((day - 1) * 2) % candidates.size();
        List<Attraction> selected = new ArrayList<>();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            selected.add(candidates.get((start + i) % candidates.size()));
        }
        return selected;
    }

    private boolean matchesInterest(Attraction attraction, Set<String> interests) {
        Set<String> searchable = new LinkedHashSet<>();
        searchable.add(attraction.category().toLowerCase(Locale.ROOT));
        searchable.addAll(attraction.tags().stream().map(tag -> tag.toLowerCase(Locale.ROOT)).toList());
        return searchable.stream().anyMatch(interests::contains);
    }

    private Set<String> normalize(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String buildTheme(List<Attraction> dayAttractions) {
        Map<String, Long> categories = dayAttractions.stream()
                .collect(Collectors.groupingBy(Attraction::category, Collectors.counting()));
        return categories.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() + " focused route")
                .orElse("Flexible city route");
    }

    private double estimateDistance(List<Attraction> dayAttractions) {
        if (dayAttractions.size() < 2) {
            return 0;
        }
        double total = 0;
        for (int i = 1; i < dayAttractions.size(); i++) {
            Attraction previous = dayAttractions.get(i - 1);
            Attraction current = dayAttractions.get(i);
            total += distance(previous.latitude(), previous.longitude(), current.latitude(), current.longitude());
        }
        return Math.round(total * 10.0) / 10.0;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
