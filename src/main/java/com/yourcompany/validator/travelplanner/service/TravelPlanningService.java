package com.yourcompany.validator.travelplanner.service;

import com.yourcompany.validator.travelplanner.dto.AiAttractionDraft;
import com.yourcompany.validator.travelplanner.dto.AiDestinationDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanDayDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanDraft;
import com.yourcompany.validator.travelplanner.dto.ManualDayRequest;
import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import com.yourcompany.validator.travelplanner.dto.SavedPlanSummary;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;
import com.yourcompany.validator.travelplanner.model.ItineraryDay;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class TravelPlanningService {
    private static final int MIN_ATTRACTIONS_PER_DAY = 3;
    private static final int RECOMMENDED_ATTRACTIONS_PER_DAY = 4;
    private static final int MAX_ATTRACTIONS_PER_DAY = 5;

    private final AiPlanGenerationService aiPlanGenerationService;
    private final AmapPoiService amapPoiService;
    private final AtomicLong planIdGenerator = new AtomicLong(1);
    private final AtomicLong generatedAttractionIdGenerator = new AtomicLong(10000);
    private final Map<Long, SavedPlan> savedPlans = new ConcurrentHashMap<>();

    private final List<Destination> destinations = List.of(
            new Destination("Shanghai", "中国", 31.2304, 121.4737, "春秋季",
                    "一座兼具都市天际线、历史街区与丰富美食体验的国际化城市。"),
            new Destination("Tokyo", "日本", 35.6762, 139.6503, "春秋季",
                    "融合流行文化、经典街区与高效交通系统的多层次都市。"),
            new Destination("Chengdu", "中国", 30.5728, 104.0668, "春秋季",
                    "节奏松弛、以美食、茶馆和人文景点闻名的西南城市。")
    );

    private final List<Attraction> attractions = List.of(
            new Attraction(1L, "The Bund", "Shanghai", "citywalk",
                    "经典滨江天际线与历史建筑群，适合散步和拍照。", 31.2400, 121.4900, 4.8, 2,
                    List.of("walk", "nightview", "photography"), List.of()),
            new Attraction(2L, "Yu Garden", "Shanghai", "culture",
                    "老城厢中的江南古典园林，适合感受传统园林氛围。", 31.2273, 121.4925, 4.6, 2,
                    List.of("culture", "history", "garden"), List.of()),
            new Attraction(3L, "Shanghai Museum", "Shanghai", "museum",
                    "系统了解中国艺术与历史的优质博物馆。", 31.2303, 121.4737, 4.7, 3,
                    List.of("culture", "museum", "history"), List.of()),
            new Attraction(4L, "Tianzifang", "Shanghai", "citywalk",
                    "石库门街巷里融合咖啡馆、小店与本地生活气息。", 31.2100, 121.4660, 4.4, 2,
                    List.of("walk", "food", "shopping"), List.of()),
            new Attraction(5L, "Tokyo Skytree", "Tokyo", "landmark",
                    "俯瞰东京城市景观的知名观景塔。", 35.7101, 139.8107, 4.6, 2,
                    List.of("view", "photography", "cityscape"), List.of()),
            new Attraction(6L, "Senso-ji", "Tokyo", "culture",
                    "适合第一次到访东京游客的经典寺庙区域。", 35.7148, 139.7967, 4.8, 2,
                    List.of("culture", "history", "walk"), List.of()),
            new Attraction(7L, "Ueno Park", "Tokyo", "park",
                    "集合公园、博物馆与季节景观的大型休闲区域。", 35.7156, 139.7745, 4.6, 3,
                    List.of("walk", "family", "museum"), List.of()),
            new Attraction(8L, "Shibuya", "Tokyo", "citywalk",
                    "适合购物、夜生活和感受年轻文化活力的街区。", 35.6595, 139.7005, 4.5, 3,
                    List.of("shopping", "nightlife", "food"), List.of()),
            new Attraction(9L, "Kuanzhai Alley", "Chengdu", "citywalk",
                    "能体验成都小吃与街巷生活氛围的历史街区。", 30.6664, 104.0499, 4.4, 2,
                    List.of("food", "walk", "culture"), List.of()),
            new Attraction(10L, "Wuhou Shrine", "Chengdu", "culture",
                    "集三国文化、园林与展陈于一体的人文景点。", 30.6467, 104.0433, 4.6, 2,
                    List.of("history", "culture", "garden"), List.of()),
            new Attraction(11L, "Jinli Ancient Street", "Chengdu", "food",
                    "适合体验小吃、买伴手礼和感受夜晚热闹氛围的古街。", 30.6458, 104.0420, 4.5, 2,
                    List.of("food", "shopping", "nightview"), List.of()),
            new Attraction(12L, "Chengdu Research Base of Giant Panda Breeding", "Chengdu", "wildlife",
                    "热门熊猫基地，通常更适合上午前往。", 30.7338, 104.1464, 4.8, 4,
                    List.of("family", "nature", "animal"), List.of())
    );

    public TravelPlanningService(AiPlanGenerationService aiPlanGenerationService,
                                 AmapPoiService amapPoiService) {
        this.aiPlanGenerationService = aiPlanGenerationService;
        this.amapPoiService = amapPoiService;
    }

    public List<Destination> getDestinations() {
        return destinations;
    }

    public List<Attraction> getAttractions(String city, List<String> interests) {
        return getCandidateAttractions(city, interests);
    }

    public PlanResponse createPlan(PlanRequest request) {
        int days = normalizeDays(request);
        LocalDate startDate = normalizeStartDate(request.startDate());
        Destination destination = resolveStandardDestination(request.city(), request.interests());

        if (hasManualRequests(request.manualDays())) {
            List<DaySelection> selections = buildManualSelections(destination, startDate, days, request.manualDays());
            return savePlan(destination, startDate, days, selections);
        }

        List<Attraction> candidates = resolveCandidateAttractions(destination.city(), request.interests(), days);

        List<DaySelection> selections = new ArrayList<>();
        int dailyTarget = determineDailyAttractionTarget(candidates);
        Set<Long> usedAttractionIds = new LinkedHashSet<>();
        Set<String> usedAttractionNames = new LinkedHashSet<>();
        for (int day = 1; day <= days; day++) {
            List<Attraction> dayAttractions = new ArrayList<>();
            fillFromFallbackCandidates(candidates, day, usedAttractionIds, usedAttractionNames, dayAttractions, dailyTarget);
            usedAttractionIds.addAll(dayAttractions.stream().map(Attraction::id).toList());
            usedAttractionNames.addAll(dayAttractions.stream().map(attraction -> normalizeAttractionName(attraction.name())).toList());
            selections.add(new DaySelection(day, "第" + day + "天 · " + destination.city(), null, dayAttractions));
        }

        return savePlan(destination, startDate, days, selections);
    }

    public PlanResponse createAiPlan(PlanRequest request) {
        String requestedCity = normalizeRequestedCity(request.city());
        Destination supportedDestination = findSupportedDestination(requestedCity);
        int days = normalizeDays(request);
        LocalDate startDate = normalizeStartDate(request.startDate());
        if (supportedDestination != null) {
            List<Attraction> candidates = resolveCandidateAttractions(supportedDestination.city(), request.interests(), days);
            AiPlanDraft draft = aiPlanGenerationService.generatePlan(request, supportedDestination, candidates, days);
            List<DaySelection> selections = buildAiSelections(supportedDestination, candidates, days, draft);
            return savePlan(supportedDestination, startDate, days, selections);
        }

        Destination draftDestination = new Destination(requestedCity, "", 0, 0, "", "");
        AiPlanDraft draft = aiPlanGenerationService.generatePlan(request, draftDestination, List.of(), days);
        Destination generatedDestination = buildGeneratedDestination(requestedCity, draft);
        List<DaySelection> selections = buildStreamSelections(generatedDestination, days, draft);
        return savePlan(generatedDestination, startDate, days, selections);
    }

    public PlanResponse previewAiPlanFromDraft(PlanRequest request, AiPlanDraft draft) {
        String requestedCity = normalizeRequestedCity(request.city());
        int days = normalizeDays(request);
        LocalDate startDate = normalizeStartDate(request.startDate());
        Destination generatedDestination = buildGeneratedDestination(requestedCity, draft);
        List<DaySelection> selections = buildGeneratedSelections(generatedDestination, days, draft);
        List<ItineraryDay> itinerary = selections.stream()
                .map(selection -> new ItineraryDay(
                        selection.day(),
                        selection.title(),
                        selection.theme() == null || selection.theme().isBlank()
                                ? buildTheme(selection.attractions())
                                : selection.theme(),
                        estimateDistance(selection.attractions()),
                        selection.attractions()
                ))
                .toList();
        return new PlanResponse(-1L, generatedDestination, startDate, days, itinerary);
    }

    public PlanResponse createAiPlanFromDraft(PlanRequest request, AiPlanDraft draft) {
        String requestedCity = normalizeRequestedCity(request.city());
        int days = normalizeDays(request);
        LocalDate startDate = normalizeStartDate(request.startDate());
        Destination generatedDestination = buildGeneratedDestination(requestedCity, draft);
        List<DaySelection> selections = buildStreamSelections(generatedDestination, days, draft);
        return savePlan(generatedDestination, startDate, days, selections);
    }

    public List<SavedPlanSummary> getSavedPlans() {
        return savedPlans.values().stream()
                .sorted(Comparator.comparing(SavedPlan::createdAt).reversed())
                .limit(20)
                .map(plan -> new SavedPlanSummary(
                        plan.planId(),
                        plan.destination().city(),
                        plan.startDate(),
                        plan.days(),
                        plan.itinerary().stream().mapToInt(day -> day.attractions().size()).sum(),
                        plan.createdAt()
                ))
                .toList();
    }

    public PlanResponse getSavedPlan(Long id) {
        SavedPlan savedPlan = savedPlans.get(id);
        if (savedPlan == null) {
            throw new IllegalArgumentException("Travel plan not found: " + id);
        }
        return new PlanResponse(
                savedPlan.planId(),
                savedPlan.destination(),
                savedPlan.startDate(),
                savedPlan.days(),
                savedPlan.itinerary()
        );
    }

    private PlanResponse savePlan(Destination destination,
                                  LocalDate startDate,
                                  int days,
                                  List<DaySelection> selections) {
        List<ItineraryDay> itinerary = selections.stream()
                .map(selection -> {
                    List<Attraction> enrichedAttractions = ensureCityAttractions(destination.city(), selection.attractions());
                    return new ItineraryDay(
                            selection.day(),
                            selection.title(),
                            selection.theme() == null || selection.theme().isBlank()
                                    ? buildTheme(enrichedAttractions)
                                    : selection.theme(),
                            estimateDistance(enrichedAttractions),
                            enrichedAttractions
                    );
                })
                .toList();

        long planId = planIdGenerator.getAndIncrement();
        SavedPlan savedPlan = new SavedPlan(planId, destination, startDate, days, itinerary, LocalDateTime.now());
        savedPlans.put(planId, savedPlan);
        return new PlanResponse(planId, destination, startDate, days, itinerary);
    }

    private List<Attraction> ensureCityAttractions(String city, List<Attraction> requestedAttractions) {
        List<Attraction> enrichedAttractions = amapPoiService.enrichAttractions(city, requestedAttractions);
        Map<String, Attraction> deduped = new LinkedHashMap<>();
        for (Attraction attraction : enrichedAttractions) {
            deduped.putIfAbsent(normalizeAttractionName(attraction.name()), attraction);
        }

        int targetCount = requestedAttractions == null || requestedAttractions.isEmpty()
                ? MIN_ATTRACTIONS_PER_DAY
                : Math.max(MIN_ATTRACTIONS_PER_DAY, requestedAttractions.size());

        if (deduped.size() < targetCount) {
            List<String> interests = requestedAttractions == null ? List.of() : requestedAttractions.stream()
                    .flatMap(attraction -> attraction.tags().stream())
                    .distinct()
                    .limit(6)
                    .toList();
            List<Attraction> supplemental = amapPoiService.searchSupplementalAttractions(
                    city,
                    interests,
                    deduped.keySet(),
                    targetCount - deduped.size()
            );
            for (Attraction attraction : supplemental) {
                deduped.putIfAbsent(normalizeAttractionName(attraction.name()), attraction);
            }
        }

        return new ArrayList<>(deduped.values());
    }

    private Destination resolveStandardDestination(String city, List<String> interests) {
        String normalizedCity = normalizeRequestedCity(city);
        Destination destination = findSupportedDestination(normalizedCity);
        if (destination != null) {
            return destination;
        }
        List<Attraction> seed = amapPoiService.searchSupplementalAttractions(normalizedCity, interests, Set.of(), 1);
        if (!seed.isEmpty()) {
            Attraction first = seed.get(0);
            return new Destination(
                    normalizedCity,
                    "",
                    first.latitude(),
                    first.longitude(),
                    "",
                    "根据你手动选择的城市与景点愿望清单整理路线。"
            );
        }
        return new Destination(normalizedCity, "", 0, 0, "", "根据你手动选择的城市与景点愿望清单整理路线。");
    }

    private Destination findSupportedDestination(String city) {
        return destinations.stream()
                .filter(destination -> destination.city().equalsIgnoreCase(city))
                .findFirst()
                .orElse(null);
    }

    private List<Attraction> getCandidateAttractions(String city, List<String> interests) {
        Set<String> normalizedInterests = normalize(interests);
        return attractions.stream()
                .filter(attraction -> city == null || city.isBlank() || attraction.city().equalsIgnoreCase(city))
                .filter(attraction -> normalizedInterests.isEmpty() || matchesInterest(attraction, normalizedInterests))
                .sorted(Comparator.comparing(Attraction::rating).reversed())
                .toList();
    }

    private List<Attraction> resolveCandidateAttractions(String city, List<String> interests, int days) {
        List<Attraction> candidates = getCandidateAttractions(city, interests);
        if (candidates.isEmpty()) {
            candidates = getCandidateAttractions(city, List.of());
        }
        int requiredCount = Math.min(days * RECOMMENDED_ATTRACTIONS_PER_DAY, days * MAX_ATTRACTIONS_PER_DAY);
        if (candidates.isEmpty()) {
            candidates = amapPoiService.searchSupplementalAttractions(city, interests, Set.of(), requiredCount);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No attractions available for " + city);
        }

        List<Attraction> enrichedCandidates = amapPoiService.enrichAttractions(city, candidates);
        Map<String, Attraction> deduped = new LinkedHashMap<>();
        for (Attraction attraction : enrichedCandidates) {
            deduped.put(normalizeAttractionName(attraction.name()), attraction);
        }

        if (deduped.size() < requiredCount) {
            List<Attraction> supplemental = amapPoiService.searchSupplementalAttractions(
                    city,
                    interests,
                    deduped.keySet(),
                    requiredCount - deduped.size()
            );
            for (Attraction attraction : supplemental) {
                deduped.putIfAbsent(normalizeAttractionName(attraction.name()), attraction);
            }
        }

        return new ArrayList<>(deduped.values());
    }

    private List<DaySelection> buildAiSelections(Destination destination,
                                                 List<Attraction> candidates,
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

        Map<Long, Attraction> byId = candidates.stream()
                .collect(Collectors.toMap(Attraction::id, attraction -> attraction, (left, right) -> left, LinkedHashMap::new));
        Map<String, Attraction> byName = candidates.stream()
                .collect(Collectors.toMap(
                        attraction -> attraction.name().toLowerCase(Locale.ROOT),
                        attraction -> attraction,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Set<Long> usedAttractionIds = new LinkedHashSet<>();
        Set<String> usedAttractionNames = new LinkedHashSet<>();
        List<DaySelection> selections = new ArrayList<>();
        int maxAttractionsPerDay = determineDailyAttractionTarget(candidates);

        for (int day = 1; day <= days; day++) {
            AiPlanDayDraft dayDraft = draftByDay.get(day);
            List<Attraction> selected = new ArrayList<>();

            if (dayDraft != null) {
                addById(dayDraft.attractionIds(), byId, usedAttractionIds, usedAttractionNames, selected);
                addByName(dayDraft.attractionNames(), byName, usedAttractionIds, usedAttractionNames, selected);
                addGeneratedAttractions(dayDraft.attractions(), usedAttractionIds, usedAttractionNames, selected);
            }

            if (selected.size() < maxAttractionsPerDay) {
                fillFromFallbackCandidates(candidates, day, usedAttractionIds, usedAttractionNames, selected, maxAttractionsPerDay);
            }

            usedAttractionIds.addAll(selected.stream().map(Attraction::id).toList());
            usedAttractionNames.addAll(selected.stream().map(attraction -> normalizeAttractionName(attraction.name())).toList());
            selections.add(new DaySelection(
                    day,
                    dayDraft != null && dayDraft.title() != null && !dayDraft.title().isBlank()
                            ? dayDraft.title()
                            : "第" + day + "天 · " + destination.city(),
                    dayDraft == null ? null : dayDraft.theme(),
                    selected
            ));
        }

        return selections;
    }

    private Destination buildGeneratedDestination(String requestedCity, AiPlanDraft draft) {
        AiDestinationDraft destinationDraft = draft == null ? null : draft.destination();
        String city = hasText(destinationDraft == null ? null : destinationDraft.city()) ? destinationDraft.city().trim() : requestedCity;
        String country = destinationDraft == null ? "" : defaultText(destinationDraft.country());
        double latitude = destinationDraft == null || destinationDraft.latitude() == null ? 0 : destinationDraft.latitude();
        double longitude = destinationDraft == null || destinationDraft.longitude() == null ? 0 : destinationDraft.longitude();
        String bestSeason = destinationDraft == null ? "" : defaultText(destinationDraft.bestSeason());
        String summary = destinationDraft != null && hasText(destinationDraft.summary())
                ? destinationDraft.summary().trim()
                : defaultText(draft == null ? null : draft.summary());
        return new Destination(city, country, latitude, longitude, bestSeason, summary);
    }

    private List<DaySelection> buildGeneratedSelections(Destination destination, int days, AiPlanDraft draft) {
        Map<Integer, AiPlanDayDraft> draftByDay = new LinkedHashMap<>();
        if (draft != null && draft.days() != null) {
            for (AiPlanDayDraft dayDraft : draft.days()) {
                if (dayDraft != null && dayDraft.day() >= 1 && dayDraft.day() <= days) {
                    draftByDay.putIfAbsent(dayDraft.day(), dayDraft);
                }
            }
        }

        List<DaySelection> selections = new ArrayList<>();
        Set<String> usedAttractionNames = new LinkedHashSet<>();
        for (int day = 1; day <= days; day++) {
            AiPlanDayDraft dayDraft = draftByDay.get(day);
            List<Attraction> generatedAttractions = buildGeneratedAttractionsForDay(destination, day, dayDraft).stream()
                    .filter(attraction -> !usedAttractionNames.contains(normalizeAttractionName(attraction.name())))
                    .toList();
            usedAttractionNames.addAll(generatedAttractions.stream().map(attraction -> normalizeAttractionName(attraction.name())).toList());
            selections.add(new DaySelection(
                    day,
                    dayDraft != null && hasText(dayDraft.title()) ? dayDraft.title().trim() : "第" + day + "天 · " + destination.city(),
                    dayDraft == null ? null : dayDraft.theme(),
                    generatedAttractions
            ));
        }
        return selections;
    }

    private List<DaySelection> buildStreamSelections(Destination destination, int days, AiPlanDraft draft) {
        Map<Integer, AiPlanDayDraft> draftByDay = new LinkedHashMap<>();
        if (draft != null && draft.days() != null) {
            for (AiPlanDayDraft dayDraft : draft.days()) {
                if (dayDraft != null && dayDraft.day() >= 1 && dayDraft.day() <= days) {
                    draftByDay.putIfAbsent(dayDraft.day(), dayDraft);
                }
            }
        }

        List<DaySelection> selections = new ArrayList<>();
        Set<String> usedAttractionNames = new LinkedHashSet<>();
        for (int day = 1; day <= days; day++) {
            AiPlanDayDraft dayDraft = draftByDay.get(day);
            List<Attraction> generatedAttractions = new ArrayList<>();
            if (dayDraft != null && dayDraft.attractions() != null) {
                int index = 0;
                for (AiAttractionDraft attractionDraft : dayDraft.attractions()) {
                    Attraction attraction = toGeneratedAttraction(attractionDraft, destination, day, index++);
                    if (attraction == null) {
                        continue;
                    }
                    String normalizedName = normalizeAttractionName(attraction.name());
                    if (usedAttractionNames.contains(normalizedName)) {
                        continue;
                    }
                    usedAttractionNames.add(normalizedName);
                    generatedAttractions.add(attraction);
                }
            }
            selections.add(new DaySelection(
                    day,
                    dayDraft != null && hasText(dayDraft.title()) ? dayDraft.title().trim() : "第" + day + "天 · " + destination.city(),
                    dayDraft == null ? null : dayDraft.theme(),
                    generatedAttractions
            ));
        }
        return selections;
    }

    private List<DaySelection> buildManualSelections(Destination destination,
                                                     LocalDate startDate,
                                                     int days,
                                                     List<ManualDayRequest> manualDays) {
        Map<Integer, ManualDayRequest> manualDayMap = new LinkedHashMap<>();
        if (manualDays != null) {
            for (ManualDayRequest manualDay : manualDays) {
                if (manualDay != null && manualDay.day() >= 1 && manualDay.day() <= days) {
                    manualDayMap.putIfAbsent(manualDay.day(), manualDay);
                }
            }
        }

        List<DaySelection> selections = new ArrayList<>();
        Set<String> usedAttractionNames = new LinkedHashSet<>();
        for (int day = 1; day <= days; day++) {
            ManualDayRequest manualDay = manualDayMap.get(day);
            LocalDate currentDate = startDate.plusDays(day - 1L);
            List<Attraction> requested = buildManualRequestedAttractions(destination.city(), manualDay);
            List<Attraction> selected = ensureCityAttractions(destination.city(), requested).stream()
                    .filter(attraction -> usedAttractionNames.add(normalizeAttractionName(attraction.name())))
                    .toList();

            if (selected.size() < MIN_ATTRACTIONS_PER_DAY) {
                List<Attraction> supplemental = amapPoiService.searchSupplementalAttractions(
                        destination.city(),
                        List.of(),
                        usedAttractionNames,
                        MIN_ATTRACTIONS_PER_DAY - selected.size()
                );
                List<Attraction> merged = new ArrayList<>(selected);
                for (Attraction attraction : supplemental) {
                    String normalizedName = normalizeAttractionName(attraction.name());
                    if (usedAttractionNames.add(normalizedName)) {
                        merged.add(attraction);
                    }
                }
                selected = merged;
            }

            selections.add(new DaySelection(
                    day,
                    "%s · 第%d天".formatted(currentDate, day),
                    selected.isEmpty() ? "根据你的景点意向补全路线" : buildTheme(selected),
                    selected
            ));
        }
        return selections;
    }

    private List<Attraction> buildManualRequestedAttractions(String city, ManualDayRequest manualDay) {
        if (manualDay == null || manualDay.attractions() == null) {
            return List.of();
        }
        List<Attraction> requested = new ArrayList<>();
        for (String attractionName : manualDay.attractions()) {
            if (!hasText(attractionName)) {
                continue;
            }
            requested.add(new Attraction(
                    generatedAttractionIdGenerator.getAndIncrement(),
                    attractionName.trim(),
                    city,
                    "sight",
                    "手动输入的意向景点，系统将尝试匹配真实 POI 并完善顺序与换乘。",
                    0,
                    0,
                    4.4,
                    2,
                    List.of("manual"),
                    List.of()
            ));
        }
        return requested;
    }

    private List<Attraction> pickForDay(List<Attraction> candidates, int day, int target) {
        int start = ((day - 1) * Math.max(1, target - 1)) % candidates.size();
        List<Attraction> selected = new ArrayList<>();
        for (int index = 0; index < Math.min(target, candidates.size()); index++) {
            selected.add(candidates.get((start + index) % candidates.size()));
        }
        return selected;
    }

    private int determineDailyAttractionTarget(List<Attraction> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return MIN_ATTRACTIONS_PER_DAY;
        }
        return Math.min(MAX_ATTRACTIONS_PER_DAY, Math.max(MIN_ATTRACTIONS_PER_DAY,
                Math.min(RECOMMENDED_ATTRACTIONS_PER_DAY, candidates.size())));
    }

    private void addById(List<Long> attractionIds,
                         Map<Long, Attraction> byId,
                         Set<Long> usedAttractionIds,
                         Set<String> usedAttractionNames,
                         List<Attraction> selected) {
        if (attractionIds == null) {
            return;
        }
        for (Long attractionId : attractionIds) {
            tryAddAttraction(byId.get(attractionId), usedAttractionIds, usedAttractionNames, selected, false);
        }
    }

    private void addByName(List<String> attractionNames,
                           Map<String, Attraction> byName,
                           Set<Long> usedAttractionIds,
                           Set<String> usedAttractionNames,
                           List<Attraction> selected) {
        if (attractionNames == null) {
            return;
        }
        for (String attractionName : attractionNames) {
            if (attractionName == null || attractionName.isBlank()) {
                continue;
            }
            tryAddAttraction(byName.get(attractionName.toLowerCase(Locale.ROOT)), usedAttractionIds, usedAttractionNames, selected, false);
        }
    }

    private void addGeneratedAttractions(List<AiAttractionDraft> drafts,
                                         Set<Long> usedAttractionIds,
                                         Set<String> usedAttractionNames,
                                         List<Attraction> selected) {
        if (drafts == null) {
            return;
        }
        for (AiAttractionDraft draft : drafts) {
            Attraction attraction = toGeneratedAttraction(draft, null, 0, selected.size());
            tryAddAttraction(attraction, usedAttractionIds, usedAttractionNames, selected, false);
        }
    }

    private List<Attraction> buildGeneratedAttractionsForDay(Destination destination,
                                                             int day,
                                                             AiPlanDayDraft dayDraft) {
        List<Attraction> results = new ArrayList<>();
        if (dayDraft != null && dayDraft.attractions() != null) {
            int index = 0;
            for (AiAttractionDraft attractionDraft : dayDraft.attractions()) {
                Attraction attraction = toGeneratedAttraction(attractionDraft, destination, day, index++);
                if (attraction != null) {
                    results.add(attraction);
                }
            }
        }
        if (results.size() < MIN_ATTRACTIONS_PER_DAY) {
            addGeneratedFallbackStops(destination, day, results);
        }
        if (!results.isEmpty()) {
            return results;
        }
        return List.of(new Attraction(
                generatedAttractionIdGenerator.getAndIncrement(),
                destination.city() + " 市中心漫步区",
                destination.city(),
                "citywalk",
                "AI 未返回足够细的景点信息，因此先生成一个可展示的核心停留点。",
                destination.latitude(),
                destination.longitude(),
                4.3,
                2,
                List.of("ai生成", "漫步"),
                List.of()
        ));
    }

    private void addGeneratedFallbackStops(Destination destination, int day, List<Attraction> results) {
        List<String> fallbackNames = List.of(
                destination.city() + " 城市地标区",
                destination.city() + " 老城漫步线",
                destination.city() + " 河岸景观带",
                destination.city() + " 本地生活街区",
                destination.city() + " 夜景观景点"
        );
        List<String> fallbackCategories = List.of("landmark", "citywalk", "view", "food", "nightview");

        int index = results.size();
        while (results.size() < MIN_ATTRACTIONS_PER_DAY && index < fallbackNames.size()) {
            results.add(new Attraction(
                    generatedAttractionIdGenerator.getAndIncrement(),
                    fallbackNames.get(index),
                    destination.city(),
                    fallbackCategories.get(index),
                    "为补足每日行程节奏而生成的推荐停留点，适合串联成更完整的一天路线。",
                    destination.latitude() + day * 0.01 + index * 0.005,
                    destination.longitude() + day * 0.01 + index * 0.005,
                    4.4,
                    2,
                    List.of("ai-generated", "fallback"),
                    List.of()
            ));
            index++;
        }
    }

    private Attraction toGeneratedAttraction(AiAttractionDraft draft,
                                             Destination destination,
                                             int day,
                                             int index) {
        if (draft == null || !hasText(draft.name())) {
            return null;
        }
        long id = draft.id() == null ? generatedAttractionIdGenerator.getAndIncrement() : draft.id();
        String city = hasText(draft.city()) ? draft.city().trim() : destination == null ? "" : destination.city();
        String category = hasText(draft.category()) ? draft.category().trim() : "sight";
        String description = hasText(draft.description()) ? draft.description().trim() : "AI 生成景点";
        double fallbackLatitude = destination == null ? 0 : destination.latitude() + day * 0.01 + index * 0.005;
        double fallbackLongitude = destination == null ? 0 : destination.longitude() + day * 0.01 + index * 0.005;
        double latitude = draft.latitude() == null ? fallbackLatitude : draft.latitude();
        double longitude = draft.longitude() == null ? fallbackLongitude : draft.longitude();
        double rating = draft.rating() == null ? 4.4 : draft.rating();
        int suggestedHours = draft.suggestedHours() == null ? 2 : Math.max(1, draft.suggestedHours());
        List<String> tags = draft.tags() == null || draft.tags().isEmpty() ? List.of("ai-generated") : draft.tags();

        return new Attraction(id, draft.name().trim(), city, category, description, latitude, longitude, rating, suggestedHours, tags, List.of());
    }

    private void fillFromFallbackCandidates(List<Attraction> candidates,
                                            int day,
                                            Set<Long> usedAttractionIds,
                                            Set<String> usedAttractionNames,
                                            List<Attraction> selected,
                                            int limit) {
        for (Attraction attraction : pickForDay(candidates, day, limit)) {
            if (selected.size() >= limit) {
                return;
            }
            tryAddAttraction(attraction, usedAttractionIds, usedAttractionNames, selected, false);
        }

        for (Attraction attraction : candidates) {
            if (selected.size() >= limit) {
                return;
            }
            tryAddAttraction(attraction, usedAttractionIds, usedAttractionNames, selected, false);
        }
    }

    private void tryAddAttraction(Attraction attraction,
                                  Set<Long> usedAttractionIds,
                                  Set<String> usedAttractionNames,
                                  List<Attraction> selected,
                                  boolean allowReuseAcrossDays) {
        if (attraction == null) {
            return;
        }
        String normalizedName = normalizeAttractionName(attraction.name());
        if (selected.stream().anyMatch(existing -> existing.id().equals(attraction.id()))) {
            return;
        }
        if (selected.stream().anyMatch(existing -> normalizeAttractionName(existing.name()).equals(normalizedName))) {
            return;
        }
        if (!allowReuseAcrossDays && usedAttractionIds.contains(attraction.id())) {
            return;
        }
        if (!allowReuseAcrossDays && usedAttractionNames.contains(normalizedName)) {
            return;
        }
        selected.add(attraction);
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

    private boolean hasManualRequests(List<ManualDayRequest> manualDays) {
        if (manualDays == null || manualDays.isEmpty()) {
            return false;
        }
        return manualDays.stream()
                .filter(day -> day != null && day.attractions() != null)
                .flatMap(day -> day.attractions().stream())
                .anyMatch(this::hasText);
    }

    private int normalizeDays(PlanRequest request) {
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        if (startDate != null && endDate != null) {
            long span = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            return Math.max(1, Math.min((int) span, 7));
        }
        return Math.max(1, Math.min(request.days(), 7));
    }

    private LocalDate normalizeStartDate(LocalDate startDate) {
        return startDate == null ? LocalDate.now() : startDate;
    }

    private String normalizeRequestedCity(String city) {
        String normalizedCity = city == null ? "" : city.trim();
        if (normalizedCity.isBlank()) {
            throw new IllegalArgumentException("请输入目的地。");
        }
        return normalizedCity;
    }

    private String supportedCitiesText() {
        return destinations.stream()
                .map(Destination::city)
                .collect(Collectors.joining("、"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeAttractionName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultText(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildTheme(List<Attraction> dayAttractions) {
        Map<String, Long> categories = dayAttractions.stream()
                .collect(Collectors.groupingBy(Attraction::category, Collectors.counting()));
        return categories.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> localizeCategory(entry.getKey()) + "主题路线")
                .orElse("轻松城市路线");
    }

    private String localizeCategory(String category) {
        return switch (category == null ? "" : category.toLowerCase(Locale.ROOT)) {
            case "culture" -> "文化";
            case "food" -> "美食";
            case "history" -> "历史";
            case "nature" -> "自然";
            case "walk", "citywalk" -> "漫步";
            case "view", "viewpoint" -> "观景";
            case "museum" -> "博物馆";
            case "garden" -> "园林";
            case "landmark" -> "地标";
            case "park" -> "公园";
            case "wildlife" -> "自然生态";
            case "sight" -> "景点";
            default -> category == null || category.isBlank() ? "综合" : category;
        };
    }

    private double estimateDistance(List<Attraction> dayAttractions) {
        if (dayAttractions.size() < 2) {
            return 0;
        }
        double total = 0;
        for (int index = 1; index < dayAttractions.size(); index++) {
            Attraction previous = dayAttractions.get(index - 1);
            Attraction current = dayAttractions.get(index);
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

    private record DaySelection(
            int day,
            String title,
            String theme,
            List<Attraction> attractions
    ) {
    }

    private record SavedPlan(
            Long planId,
            Destination destination,
            LocalDate startDate,
            int days,
            List<ItineraryDay> itinerary,
            LocalDateTime createdAt
    ) {
    }
}
