package com.yourcompany.validator.travelplanner.service;

import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import com.yourcompany.validator.travelplanner.dto.SavedPlanSummary;
import com.yourcompany.validator.travelplanner.dto.AiPlanDayDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanDraft;
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
import java.util.LinkedHashMap;
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
    private final AiPlanGenerationService aiPlanGenerationService;

    public TravelPlanningService(DestinationRepository destinationRepository,
                                 AttractionRepository attractionRepository,
                                 TravelPlanRepository travelPlanRepository,
                                 AiPlanGenerationService aiPlanGenerationService) {
        this.destinationRepository = destinationRepository;
        this.attractionRepository = attractionRepository;
        this.travelPlanRepository = travelPlanRepository;
        this.aiPlanGenerationService = aiPlanGenerationService;
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
        int days = normalizeDays(request.days());
        LocalDate startDate = normalizeStartDate(request.startDate());

        List<AttractionEntity> candidates = resolveCandidateAttractions(destination.getCity(), request.interests());

        List<DaySelection> selections = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            List<AttractionEntity> dayAttractions = pickForDay(candidates, day);
            selections.add(new DaySelection(
                    day,
                    "Day " + day + " - " + destination.getCity(),
                    null,
                    dayAttractions
            ));
        }

        return savePlan(destination, startDate, days, selections);
    }

    @Transactional
    public PlanResponse createAiPlan(PlanRequest request) {
        DestinationEntity destination = resolveDestination(request.city());
        int days = normalizeDays(request.days());
        LocalDate startDate = normalizeStartDate(request.startDate());
        List<AttractionEntity> candidates = resolveCandidateAttractions(destination.getCity(), request.interests());
        List<Attraction> candidateDtos = candidates.stream().map(this::toAttraction).toList();

        AiPlanDraft draft = aiPlanGenerationService.generatePlan(request, toDestination(destination), candidateDtos, days);
        List<DaySelection> selections = buildAiSelections(destination, candidates, days, draft);

        return savePlan(destination, startDate, days, selections);
    }

    private PlanResponse savePlan(DestinationEntity destination,
                                  LocalDate startDate,
                                  int days,
                                  List<DaySelection> selections) {
        TravelPlanEntity plan = new TravelPlanEntity(destination.getCity(), startDate, days);
        List<ItineraryDay> itinerary = new ArrayList<>(selections.size());
        for (DaySelection selection : selections) {
            for (int index = 0; index < selection.attractions().size(); index++) {
                plan.addStop(new TravelPlanStopEntity(selection.attractions().get(index), selection.day(), index + 1));
            }
            List<Attraction> attractionDtos = selection.attractions().stream().map(this::toAttraction).toList();
            itinerary.add(new ItineraryDay(
                    selection.day(),
                    selection.title(),
                    selection.theme() == null || selection.theme().isBlank() ? buildTheme(attractionDtos) : selection.theme(),
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

    private List<AttractionEntity> resolveCandidateAttractions(String city, List<String> interests) {
        List<AttractionEntity> candidates = getAttractionEntities(city, interests);
        if (candidates.isEmpty()) {
            candidates = getAttractionEntities(city, List.of());
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No attractions available for " + city);
        }
        return candidates;
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

    private List<DaySelection> buildAiSelections(DestinationEntity destination,
                                                 List<AttractionEntity> candidates,
                                                 int days,
                                                 AiPlanDraft draft) {
        Map<Integer, AiPlanDayDraft> draftByDay = new LinkedHashMap<>();
        if (draft != null && draft.days() != null) {
            for (AiPlanDayDraft dayDraft : draft.days()) {
                if (dayDraft != null && dayDraft.day() >= 1 && dayDraft.day() <= days) {
                    draftByDay.putIfAbsent(dayDraft.day(), dayDraft);
                }
            }
        }

        Map<Long, AttractionEntity> byId = candidates.stream()
                .collect(Collectors.toMap(AttractionEntity::getId, attraction -> attraction, (left, right) -> left, LinkedHashMap::new));
        Map<String, AttractionEntity> byName = candidates.stream()
                .collect(Collectors.toMap(
                        attraction -> attraction.getName().toLowerCase(Locale.ROOT),
                        attraction -> attraction,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<Long> usedAttractionIds = new LinkedHashSet<>();
        List<DaySelection> selections = new ArrayList<>();
        int maxAttractionsPerDay = Math.min(3, candidates.size());

        for (int day = 1; day <= days; day++) {
            AiPlanDayDraft dayDraft = draftByDay.get(day);
            List<AttractionEntity> selected = new ArrayList<>();

            if (dayDraft != null) {
                addById(dayDraft.attractionIds(), byId, usedAttractionIds, selected);
                addByName(dayDraft.attractionNames(), byName, usedAttractionIds, selected);
            }

            if (selected.size() < maxAttractionsPerDay) {
                fillFromFallbackCandidates(candidates, day, usedAttractionIds, selected, maxAttractionsPerDay);
            }

            usedAttractionIds.addAll(selected.stream().map(AttractionEntity::getId).toList());
            selections.add(new DaySelection(
                    day,
                    dayDraft != null && dayDraft.title() != null && !dayDraft.title().isBlank()
                            ? dayDraft.title()
                            : "Day " + day + " - " + destination.getCity(),
                    dayDraft == null ? null : dayDraft.theme(),
                    selected
            ));
        }
        return selections;
    }

    private List<AttractionEntity> pickForDay(List<AttractionEntity> candidates, int day) {
        int start = ((day - 1) * 2) % candidates.size();
        List<AttractionEntity> selected = new ArrayList<>();
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            selected.add(candidates.get((start + i) % candidates.size()));
        }
        return selected;
    }

    private void addById(List<Long> attractionIds,
                         Map<Long, AttractionEntity> byId,
                         Set<Long> usedAttractionIds,
                         List<AttractionEntity> selected) {
        if (attractionIds == null) {
            return;
        }
        for (Long attractionId : attractionIds) {
            AttractionEntity attraction = byId.get(attractionId);
            tryAddAttraction(attraction, usedAttractionIds, selected, false);
        }
    }

    private void addByName(List<String> attractionNames,
                           Map<String, AttractionEntity> byName,
                           Set<Long> usedAttractionIds,
                           List<AttractionEntity> selected) {
        if (attractionNames == null) {
            return;
        }
        for (String attractionName : attractionNames) {
            if (attractionName == null || attractionName.isBlank()) {
                continue;
            }
            AttractionEntity attraction = byName.get(attractionName.toLowerCase(Locale.ROOT));
            tryAddAttraction(attraction, usedAttractionIds, selected, false);
        }
    }

    private void fillFromFallbackCandidates(List<AttractionEntity> candidates,
                                            int day,
                                            Set<Long> usedAttractionIds,
                                            List<AttractionEntity> selected,
                                            int limit) {
        for (AttractionEntity attraction : pickForDay(candidates, day)) {
            if (selected.size() >= limit) {
                return;
            }
            tryAddAttraction(attraction, usedAttractionIds, selected, false);
        }

        for (AttractionEntity attraction : candidates) {
            if (selected.size() >= limit) {
                return;
            }
            tryAddAttraction(attraction, usedAttractionIds, selected, false);
        }

        for (AttractionEntity attraction : candidates) {
            if (selected.size() >= limit) {
                return;
            }
            tryAddAttraction(attraction, usedAttractionIds, selected, true);
        }
    }

    private void tryAddAttraction(AttractionEntity attraction,
                                  Set<Long> usedAttractionIds,
                                  List<AttractionEntity> selected,
                                  boolean allowReuseAcrossDays) {
        if (attraction == null) {
            return;
        }
        if (selected.stream().anyMatch(existing -> existing.getId().equals(attraction.getId()))) {
            return;
        }
        if (!allowReuseAcrossDays && usedAttractionIds.contains(attraction.getId())) {
            return;
        }
        selected.add(attraction);
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

    private int normalizeDays(int days) {
        return Math.max(1, Math.min(days, 7));
    }

    private LocalDate normalizeStartDate(LocalDate startDate) {
        return startDate == null ? LocalDate.now() : startDate;
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

    private record DaySelection(
            int day,
            String title,
            String theme,
            List<AttractionEntity> attractions
    ) {
    }
}
