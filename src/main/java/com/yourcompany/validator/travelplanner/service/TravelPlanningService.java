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
import com.yourcompany.validator.travelplanner.model.ItineraryDay;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final AiPlanGenerationService aiPlanGenerationService;
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
                    List.of("walk", "nightview", "photography")),
            new Attraction(2L, "Yu Garden", "Shanghai", "culture",
                    "老城厢中的江南古典园林，适合感受传统园林氛围。", 31.2273, 121.4925, 4.6, 2,
                    List.of("culture", "history", "garden")),
            new Attraction(3L, "Shanghai Museum", "Shanghai", "museum",
                    "系统了解中国艺术与历史的优质博物馆。", 31.2303, 121.4737, 4.7, 3,
                    List.of("culture", "museum", "history")),
            new Attraction(4L, "Tianzifang", "Shanghai", "citywalk",
                    "石库门街巷里融合咖啡馆、小店与本地生活气息。", 31.2100, 121.4660, 4.4, 2,
                    List.of("walk", "food", "shopping")),
            new Attraction(5L, "Tokyo Skytree", "Tokyo", "landmark",
                    "俯瞰东京城市景观的知名观景塔。", 35.7101, 139.8107, 4.6, 2,
                    List.of("view", "photography", "cityscape")),
            new Attraction(6L, "Senso-ji", "Tokyo", "culture",
                    "适合第一次到访东京游客的经典寺庙区域。", 35.7148, 139.7967, 4.8, 2,
                    List.of("culture", "history", "walk")),
            new Attraction(7L, "Ueno Park", "Tokyo", "park",
                    "集合公园、博物馆与季节景观的大型休闲区域。", 35.7156, 139.7745, 4.6, 3,
                    List.of("walk", "family", "museum")),
            new Attraction(8L, "Shibuya", "Tokyo", "citywalk",
                    "适合购物、夜生活和感受年轻文化活力的街区。", 35.6595, 139.7005, 4.5, 3,
                    List.of("shopping", "nightlife", "food")),
            new Attraction(9L, "Kuanzhai Alley", "Chengdu", "citywalk",
                    "能体验成都小吃与街巷生活氛围的历史街区。", 30.6664, 104.0499, 4.4, 2,
                    List.of("food", "walk", "culture")),
            new Attraction(10L, "Wuhou Shrine", "Chengdu", "culture",
                    "集三国文化、园林与展陈于一体的人文景点。", 30.6467, 104.0433, 4.6, 2,
                    List.of("history", "culture", "garden")),
            new Attraction(11L, "Jinli Ancient Street", "Chengdu", "food",
                    "适合体验小吃、买伴手礼和感受夜晚热闹氛围的古街。", 30.6458, 104.0420, 4.5, 2,
                    List.of("food", "shopping", "nightview")),
            new Attraction(12L, "Chengdu Research Base of Giant Panda Breeding", "Chengdu", "wildlife",
                    "热门熊猫基地，通常更适合上午前往。", 30.7338, 104.1464, 4.8, 4,
                    List.of("family", "nature", "animal"))
    );

    public TravelPlanningService(AiPlanGenerationService aiPlanGenerationService) {
        this.aiPlanGenerationService = aiPlanGenerationService;
    }

    public List<Destination> getDestinations() {
        return destinations;
    }

    public List<Attraction> getAttractions(String city, List<String> interests) {
        return getCandidateAttractions(city, interests);
    }

    public PlanResponse createPlan(PlanRequest request) {
        Destination destination = resolveDestination(request.city());
        int days = normalizeDays(request.days());
        LocalDate startDate = normalizeStartDate(request.startDate());
        List<Attraction> candidates = resolveCandidateAttractions(destination.city(), request.interests());

        List<DaySelection> selections = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            List<Attraction> dayAttractions = pickForDay(candidates, day);
            selections.add(new DaySelection(day, "第" + day + "天 · " + destination.city(), null, dayAttractions));
        }

        return savePlan(destination, startDate, days, selections);
    }

    public PlanResponse createAiPlan(PlanRequest request) {
        String requestedCity = normalizeRequestedCity(request.city());
        Destination supportedDestination = findSupportedDestination(requestedCity);
        int days = normalizeDays(request.days());
        LocalDate startDate = normalizeStartDate(request.startDate());
        if (supportedDestination != null) {
            List<Attraction> candidates = resolveCandidateAttractions(supportedDestination.city(), request.interests());
            AiPlanDraft draft = aiPlanGenerationService.generatePlan(request, supportedDestination, candidates, days);
            List<DaySelection> selections = buildAiSelections(supportedDestination, candidates, days, draft);
            return savePlan(supportedDestination, startDate, days, selections);
        }

        Destination draftDestination = new Destination(requestedCity, "", 0, 0, "", "");
        AiPlanDraft draft = aiPlanGenerationService.generatePlan(request, draftDestination, List.of(), days);
        Destination generatedDestination = buildGeneratedDestination(requestedCity, draft);
        List<DaySelection> selections = buildGeneratedSelections(generatedDestination, days, draft);
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

        long planId = planIdGenerator.getAndIncrement();
        SavedPlan savedPlan = new SavedPlan(planId, destination, startDate, days, itinerary, LocalDateTime.now());
        savedPlans.put(planId, savedPlan);
        return new PlanResponse(planId, destination, startDate, days, itinerary);
    }

    private Destination resolveDestination(String city) {
        String normalizedCity = normalizeRequestedCity(city);
        Destination destination = findSupportedDestination(normalizedCity);
        if (destination != null) {
            return destination;
        }
        throw new IllegalArgumentException(
                "普通规划暂不支持该目的地：%s。你可以切换到 AI 规划生成自定义目的地景点；当前内置城市有：%s。"
                        .formatted(normalizedCity, supportedCitiesText())
        );
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

    private List<Attraction> resolveCandidateAttractions(String city, List<String> interests) {
        List<Attraction> candidates = getCandidateAttractions(city, interests);
        if (candidates.isEmpty()) {
            candidates = getCandidateAttractions(city, List.of());
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No attractions available for " + city);
        }
        return candidates;
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
        List<DaySelection> selections = new ArrayList<>();
        int maxAttractionsPerDay = Math.min(3, candidates.size());

        for (int day = 1; day <= days; day++) {
            AiPlanDayDraft dayDraft = draftByDay.get(day);
            List<Attraction> selected = new ArrayList<>();

            if (dayDraft != null) {
                addById(dayDraft.attractionIds(), byId, usedAttractionIds, selected);
                addByName(dayDraft.attractionNames(), byName, usedAttractionIds, selected);
                addGeneratedAttractions(dayDraft.attractions(), usedAttractionIds, selected);
            }

            if (selected.size() < maxAttractionsPerDay) {
                fillFromFallbackCandidates(candidates, day, usedAttractionIds, selected, maxAttractionsPerDay);
            }

            usedAttractionIds.addAll(selected.stream().map(Attraction::id).toList());
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
        for (int day = 1; day <= days; day++) {
            AiPlanDayDraft dayDraft = draftByDay.get(day);
            List<Attraction> generatedAttractions = buildGeneratedAttractionsForDay(destination, day, dayDraft);
            selections.add(new DaySelection(
                    day,
                    dayDraft != null && hasText(dayDraft.title()) ? dayDraft.title().trim() : "第" + day + "天 · " + destination.city(),
                    dayDraft == null ? null : dayDraft.theme(),
                    generatedAttractions
            ));
        }
        return selections;
    }

    private List<Attraction> pickForDay(List<Attraction> candidates, int day) {
        int start = ((day - 1) * 2) % candidates.size();
        List<Attraction> selected = new ArrayList<>();
        for (int index = 0; index < Math.min(3, candidates.size()); index++) {
            selected.add(candidates.get((start + index) % candidates.size()));
        }
        return selected;
    }

    private void addById(List<Long> attractionIds,
                         Map<Long, Attraction> byId,
                         Set<Long> usedAttractionIds,
                         List<Attraction> selected) {
        if (attractionIds == null) {
            return;
        }
        for (Long attractionId : attractionIds) {
            tryAddAttraction(byId.get(attractionId), usedAttractionIds, selected, false);
        }
    }

    private void addByName(List<String> attractionNames,
                           Map<String, Attraction> byName,
                           Set<Long> usedAttractionIds,
                           List<Attraction> selected) {
        if (attractionNames == null) {
            return;
        }
        for (String attractionName : attractionNames) {
            if (attractionName == null || attractionName.isBlank()) {
                continue;
            }
            tryAddAttraction(byName.get(attractionName.toLowerCase(Locale.ROOT)), usedAttractionIds, selected, false);
        }
    }

    private void addGeneratedAttractions(List<AiAttractionDraft> drafts,
                                         Set<Long> usedAttractionIds,
                                         List<Attraction> selected) {
        if (drafts == null) {
            return;
        }
        for (AiAttractionDraft draft : drafts) {
            Attraction attraction = toGeneratedAttraction(draft, null, 0, selected.size());
            tryAddAttraction(attraction, usedAttractionIds, selected, false);
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
                List.of("ai生成", "漫步")
        ));
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

        return new Attraction(id, draft.name().trim(), city, category, description, latitude, longitude, rating, suggestedHours, tags);
    }

    private void fillFromFallbackCandidates(List<Attraction> candidates,
                                            int day,
                                            Set<Long> usedAttractionIds,
                                            List<Attraction> selected,
                                            int limit) {
        for (Attraction attraction : pickForDay(candidates, day)) {
            if (selected.size() >= limit) {
                return;
            }
            tryAddAttraction(attraction, usedAttractionIds, selected, false);
        }

        for (Attraction attraction : candidates) {
            if (selected.size() >= limit) {
                return;
            }
            tryAddAttraction(attraction, usedAttractionIds, selected, false);
        }

        for (Attraction attraction : candidates) {
            if (selected.size() >= limit) {
                return;
            }
            tryAddAttraction(attraction, usedAttractionIds, selected, true);
        }
    }

    private void tryAddAttraction(Attraction attraction,
                                  Set<Long> usedAttractionIds,
                                  List<Attraction> selected,
                                  boolean allowReuseAcrossDays) {
        if (attraction == null) {
            return;
        }
        if (selected.stream().anyMatch(existing -> existing.id().equals(attraction.id()))) {
            return;
        }
        if (!allowReuseAcrossDays && usedAttractionIds.contains(attraction.id())) {
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

    private int normalizeDays(int days) {
        return Math.max(1, Math.min(days, 7));
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
