package com.yourcompany.validator.travelplanner.service;

import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import com.yourcompany.validator.travelplanner.dto.SavedPlanSummary;
import com.yourcompany.validator.travelplanner.entity.AttractionEntity;
import com.yourcompany.validator.travelplanner.entity.DestinationEntity;
import com.yourcompany.validator.travelplanner.entity.TravelPlanEntity;
import com.yourcompany.validator.travelplanner.entity.TravelPlanStopEntity;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;
import com.yourcompany.validator.travelplanner.model.ItineraryDay;
import com.yourcompany.validator.travelplanner.repository.AttractionRepository;
import com.yourcompany.validator.travelplanner.repository.DestinationRepository;
import com.yourcompany.validator.travelplanner.repository.TravelPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TravelPlanningService {
    private final DestinationRepository destinationRepository;
    private final AttractionRepository attractionRepository;
    private final TravelPlanRepository travelPlanRepository;

    public TravelPlanningService(DestinationRepository destinationRepository,
                                 AttractionRepository attractionRepository,
                                 TravelPlanRepository travelPlanRepository) {
        this.destinationRepository = destinationRepository;
        this.attractionRepository = attractionRepository;
        this.travelPlanRepository = travelPlanRepository;
    }

    @Transactional(readOnly = true)
    public List<Destination> getDestinations() {
        return destinationRepository.findAll().stream()
                .map(this::toDestination)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Attraction> getAttractions(String city, List<String> interests) {
        return getAttractionEntities(city, interests).stream()
                .map(this::toAttraction)
                .toList();
    }

    @Transactional
    public PlanResponse createPlan(PlanRequest request) {
        DestinationEntity destination = resolveDestination(request.city());
        int days = Math.max(1, Math.min(request.days(), 7));
        LocalDate startDate = request.startDate() == null ? LocalDate.now() : request.startDate();

        List<AttractionEntity> candidates = getAttractionEntities(destination.getCity(), request.interests());
        if (candidates.isEmpty()) {
            candidates = getAttractionEntities(destination.getCity(), List.of());
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No attractions available for " + destination.getCity());
        }

        TravelPlanEntity plan = new TravelPlanEntity(destination.getCity(), startDate, days);
        List<ItineraryDay> itinerary = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            List<AttractionEntity> dayAttractions = pickForDay(candidates, day);
            for (int index = 0; index < dayAttractions.size(); index++) {
                plan.addStop(new TravelPlanStopEntity(dayAttractions.get(index), day, index + 1));
            }
            List<Attraction> attractionDtos = dayAttractions.stream().map(this::toAttraction).toList();
            itinerary.add(new ItineraryDay(
                    day,
                    "Day " + day + " - " + destination.getCity(),
                    buildTheme(attractionDtos),
                    estimateDistance(attractionDtos),
                    attractionDtos
            ));
        }

        TravelPlanEntity savedPlan = travelPlanRepository.save(plan);
        return new PlanResponse(savedPlan.getId(), toDestination(destination), startDate, days, itinerary);
    }

    @Transactional(readOnly = true)
    public List<SavedPlanSummary> getSavedPlans() {
        return travelPlanRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(plan -> new SavedPlanSummary(
                        plan.getId(),
                        plan.getCity(),
                        plan.getStartDate(),
                        plan.getDays(),
                        plan.getStops().size(),
                        plan.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getSavedPlan(Long id) {
        TravelPlanEntity plan = travelPlanRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Travel plan not found: " + id));
        DestinationEntity destination = resolveDestination(plan.getCity());

        Map<Integer, List<Attraction>> stopsByDay = new TreeMap<>();
        for (TravelPlanStopEntity stop : plan.getStops()) {
            stopsByDay.computeIfAbsent(stop.getDayNumber(), ignored -> new ArrayList<>())
                    .add(toAttraction(stop.getAttraction()));
        }

        List<ItineraryDay> itinerary = stopsByDay.entrySet().stream()
                .map(entry -> new ItineraryDay(
                        entry.getKey(),
                        "Day " + entry.getKey() + " - " + plan.getCity(),
                        buildTheme(entry.getValue()),
                        estimateDistance(entry.getValue()),
                        entry.getValue()
                ))
                .toList();

        return new PlanResponse(plan.getId(), toDestination(destination), plan.getStartDate(), plan.getDays(), itinerary);
    }

    private List<AttractionEntity> getAttractionEntities(String city, List<String> interests) {
        Set<String> normalizedInterests = normalize(interests);
        List<AttractionEntity> source = city == null || city.isBlank()
                ? attractionRepository.findAll()
                : attractionRepository.findByCityIgnoreCase(city);
        return source.stream()
                .filter(attraction -> normalizedInterests.isEmpty() || matchesInterest(attraction, normalizedInterests))
                .sorted(Comparator.comparing(AttractionEntity::getRating).reversed())
                .toList();
    }

    private DestinationEntity resolveDestination(String city) {
        if (city != null && !city.isBlank()) {
            return destinationRepository.findByCityIgnoreCase(city)
                    .orElseGet(() -> destinationRepository.findAll().stream()
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("No destinations available")));
        }
        return destinationRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No destinations available"));
    }

    private List<AttractionEntity> pickForDay(List<AttractionEntity> candidates, int day) {
        int start = ((day - 1) * 2) % candidates.size();
        List<AttractionEntity> selected = new ArrayList<>();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            selected.add(candidates.get((start + i) % candidates.size()));
        }
        return selected;
    }

    private boolean matchesInterest(AttractionEntity attraction, Set<String> interests) {
        Set<String> searchable = new LinkedHashSet<>();
        searchable.add(attraction.getCategory().toLowerCase(Locale.ROOT));
        searchable.addAll(attraction.getTags().stream().map(tag -> tag.toLowerCase(Locale.ROOT)).toList());
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

    private Destination toDestination(DestinationEntity entity) {
        return new Destination(
                entity.getCity(),
                entity.getCountry(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getBestSeason(),
                entity.getSummary()
        );
    }

    private Attraction toAttraction(AttractionEntity entity) {
        return new Attraction(
                entity.getId(),
                entity.getName(),
                entity.getCity(),
                entity.getCategory(),
                entity.getDescription(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getRating(),
                entity.getSuggestedHours(),
                entity.getTags()
        );
    }
}
