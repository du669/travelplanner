package com.yourcompany.validator.travelplanner.service;

import com.yourcompany.validator.travelplanner.dto.AiAttractionDraft;
import com.yourcompany.validator.travelplanner.dto.AiDestinationDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanDayDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanDraft;
import com.yourcompany.validator.travelplanner.dto.PlanEditRequest;
import com.yourcompany.validator.travelplanner.dto.PlanOptimizeRequest;
import com.yourcompany.validator.travelplanner.dto.ManualDayRequest;
import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import com.yourcompany.validator.travelplanner.dto.SavedPlanSummary;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;
import com.yourcompany.validator.travelplanner.model.ItineraryDay;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TravelPlanningService {
    private static final Pattern INSTRUCTION_DAY_PATTERN = Pattern.compile("第([一二三四五六七1234567])天([^。；\\n]*)");
    private static final int MIN_ATTRACTIONS_PER_DAY = 3;
    private static final int RECOMMENDED_ATTRACTIONS_PER_DAY = 4;
    private static final int MAX_ATTRACTIONS_PER_DAY = 5;

    private final AiPlanGenerationService aiPlanGenerationService;
    private final AmapPoiService amapPoiService;
    private final AtomicLong planIdGenerator = new AtomicLong(1);
    private final AtomicLong generatedAttractionIdGenerator = new AtomicLong(10000);
    private final Map<Long, SavedPlan> savedPlans = new ConcurrentHashMap<>();

    private final List<Destination> destinations = List.of(
            new Destination("\u4e0a\u6d77", "\u4e2d\u56fd", 31.2304, 121.4737, "\u6625\u79cb\u4e24\u5b63", "\u4e0a\u6d77\u662f\u4e2d\u897f\u4ea4\u878d\u7684\u56fd\u9645\u5927\u90fd\u5e02\uff0c\u9002\u5408\u57ce\u5e02\u6f2b\u6b65\u3001\u535a\u7269\u9986\u4e0e\u7f8e\u98df\u4f53\u9a8c\u3002"),
            new Destination("\u4e1c\u4eac", "\u65e5\u672c", 35.6762, 139.6503, "\u6625\u79cb\u4e24\u5b63", "\u4e1c\u4eac\u517c\u5177\u73b0\u4ee3\u90fd\u5e02\u4e0e\u4f20\u7edf\u8857\u533a\u6c14\u8d28\uff0c\u9002\u5408\u9996\u6b21\u6df1\u5ea6\u6e38\u3002"),
            new Destination("\u6210\u90fd", "\u4e2d\u56fd", 30.5728, 104.0668, "\u6625\u79cb\u4e24\u5b63", "\u6210\u90fd\u4ee5\u4f11\u95f2\u751f\u6d3b\u3001\u5ddd\u83dc\u4e0e\u5386\u53f2\u6587\u5316\u95fb\u540d\uff0c\u9002\u5408\u6162\u8282\u594f\u6e38\u73a9\u3002")
    );

    private final List<Attraction> attractions = List.of(
            new Attraction(1L, "\u5916\u6ee9", "\u4e0a\u6d77", "citywalk", "\u53ef\u6b65\u884c\u6b23\u8d4f\u6d66\u6c5f\u4e24\u5cb8\u4e0e\u4e07\u56fd\u5efa\u7b51\u7fa4\u3002", 31.2400, 121.4900, 4.8, 2, List.of("walk", "nightview", "photography"), List.of()),
            new Attraction(2L, "\u8c6b\u56ed", "\u4e0a\u6d77", "culture", "\u5178\u578b\u7684\u6c5f\u5357\u56ed\u6797\uff0c\u9002\u5408\u611f\u53d7\u8001\u57ce\u538c\u5386\u53f2\u98ce\u8c8c\u3002", 31.2273, 121.4925, 4.6, 2, List.of("culture", "history", "garden"), List.of()),
            new Attraction(3L, "\u4e0a\u6d77\u535a\u7269\u9986", "\u4e0a\u6d77", "museum", "\u9986\u85cf\u4e30\u5bcc\uff0c\u9002\u5408\u7cfb\u7edf\u4e86\u89e3\u4e2d\u56fd\u53e4\u4ee3\u827a\u672f\u4e0e\u6587\u7269\u3002", 31.2303, 121.4737, 4.7, 3, List.of("culture", "museum", "history"), List.of()),
            new Attraction(4L, "\u7530\u5b50\u574a", "\u4e0a\u6d77", "citywalk", "\u8f83\u9002\u5408\u6563\u6b65\u62cd\u7167\u3001\u5c0f\u5e97\u63a2\u8bbf\u548c\u8f7b\u677e\u7528\u9910\u3002", 31.2100, 121.4660, 4.4, 2, List.of("walk", "food", "shopping"), List.of()),
            new Attraction(5L, "\u4e1c\u4eac\u6674\u7a7a\u5854", "\u4e1c\u4eac", "landmark", "\u4e1c\u4eac\u4ee3\u8868\u6027\u5730\u6807\uff0c\u9002\u5408\u89c2\u666f\u4e0e\u62cd\u6444\u57ce\u5e02\u5929\u9645\u7ebf\u3002", 35.7101, 139.8107, 4.6, 2, List.of("view", "photography", "cityscape"), List.of()),
            new Attraction(6L, "\u6d45\u8349\u5bfa", "\u4e1c\u4eac", "culture", "\u4f20\u7edf\u6c1b\u56f4\u6d53\u539a\uff0c\u53ef\u7ed3\u5408\u4ef2\u89c1\u4e16\u901a\u4e00\u5e26\u4f11\u95f2\u6e38\u89c8\u3002", 35.7148, 139.7967, 4.8, 2, List.of("culture", "history", "walk"), List.of()),
            new Attraction(7L, "\u4e0a\u91ce\u516c\u56ed", "\u4e1c\u4eac", "park", "\u516c\u56ed\u5185\u9986\u820d\u4e0e\u7eff\u5730\u8f83\u591a\uff0c\u9002\u5408\u5bb6\u5ead\u4e0e\u6162\u901f\u884c\u7a0b\u3002", 35.7156, 139.7745, 4.6, 3, List.of("walk", "family", "museum"), List.of()),
            new Attraction(8L, "\u6da9\u8c37", "\u4e1c\u4eac", "citywalk", "\u9002\u5408\u4f53\u9a8c\u6d41\u884c\u6587\u5316\u3001\u5546\u5708\u6f2b\u6b65\u4e0e\u591c\u95f4\u57ce\u5e02\u6c14\u6c1b\u3002", 35.6595, 139.7005, 4.5, 3, List.of("shopping", "nightlife", "food"), List.of()),
            new Attraction(9L, "\u5bbd\u7a84\u5df7\u5b50", "\u6210\u90fd", "citywalk", "\u9002\u5408\u4f53\u9a8c\u6210\u90fd\u6162\u751f\u6d3b\u4e0e\u7279\u8272\u5c0f\u5403\u3002", 30.6664, 104.0499, 4.4, 2, List.of("food", "walk", "culture"), List.of()),
            new Attraction(10L, "\u6b66\u4faf\u7960", "\u6210\u90fd", "culture", "\u4e09\u56fd\u6587\u5316\u4ee3\u8868\u5730\u6807\uff0c\u9002\u5408\u5386\u53f2\u7231\u597d\u8005\u6df1\u5165\u6e38\u89c8\u3002", 30.6467, 104.0433, 4.6, 2, List.of("history", "culture", "garden"), List.of()),
            new Attraction(11L, "\u9526\u91cc\u53e4\u8857", "\u6210\u90fd", "food", "\u53ef\u8fde\u540c\u6b66\u4faf\u7960\u4e00\u8d77\u5b89\u6392\uff0c\u9002\u5408\u591c\u6e38\u4e0e\u672c\u5730\u7f8e\u98df\u4f53\u9a8c\u3002", 30.6458, 104.0420, 4.5, 2, List.of("food", "shopping", "nightview"), List.of()),
            new Attraction(12L, "\u6210\u90fd\u5927\u718a\u732b\u7e41\u80b2\u7814\u7a76\u57fa\u5730", "\u6210\u90fd", "wildlife", "\u9002\u5408\u5bb6\u5ead\u548c\u52a8\u7269\u7231\u597d\u8005\uff0c\u53ef\u89c2\u5bdf\u5927\u718a\u732b\u65e5\u5e38\u6d3b\u52a8\u3002", 30.7338, 104.1464, 4.8, 4, List.of("family", "nature", "animal"), List.of())
    );

    @Autowired
    public TravelPlanningService(AiPlanGenerationService aiPlanGenerationService,
                                 AmapPoiService amapPoiService) {
        this.aiPlanGenerationService = aiPlanGenerationService;
        this.amapPoiService = amapPoiService;
    }

    public TravelPlanningService(AiPlanGenerationService aiPlanGenerationService) {
        this(aiPlanGenerationService, null);
    }

    public List<Destination> getDestinations() {
        return destinations;
    }

    public List<Attraction> getAttractions(String city, List<String> interests) {
        return getCandidateAttractions(city, interests);
    }

    public PlanResponse createPlan(PlanRequest request) {
        PreparedStandardPlan preparedPlan = prepareStandardPlan(request);
        return savePlan(
                preparedPlan.destination(),
                preparedPlan.startDate(),
                preparedPlan.days(),
                preparedPlan.selections(),
                preparedPlan.allowSupplementalAttractions(),
                preparedPlan.preserveManualInput()
        );
    }
    public PlanResponse previewStandardPlan(PlanRequest request, int completedDays) {
        PreparedStandardPlan preparedPlan = prepareStandardPlan(request);
        int visibleDays = Math.max(1, Math.min(completedDays, preparedPlan.days()));
        return buildPlanResponse(
                -1L,
                preparedPlan.destination(),
                preparedPlan.startDate(),
                preparedPlan.days(),
                new ArrayList<>(preparedPlan.selections().subList(0, visibleDays)),
                preparedPlan.allowSupplementalAttractions(),
                preparedPlan.preserveManualInput()
        );
    }
    public int getPlanDays(PlanRequest request) {
        return normalizeDays(request);
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

    public PlanResponse applyEdits(PlanEditRequest request) {
        EditedPlanContext context = normalizeEditedPlan(request);
        return savePlan(context.destination(), context.startDate(), context.days(), context.selections(), false, true);
    }

    public PlanResponse previewEditedPlan(PlanEditRequest request) {
        EditedPlanContext context = normalizeEditedPlan(request);
        return buildPlanResponse(-1L, context.destination(), context.startDate(), context.days(), context.selections(), false, true);
    }

    public PlanResponse optimizeEditedPlan(PlanOptimizeRequest request) {
        if (request == null || request.plan() == null) {
            throw new IllegalArgumentException("Plan data is required for AI optimization");
        }

        EditedPlanContext context = normalizeEditedPlan(request.plan());
        List<Attraction> candidates = collectCandidateAttractions(
                request.plan().itinerary(),
                context.destination().city(),
                request.interests(),
                context.days()
        );

        AiPlanDraft draft = aiPlanGenerationService.optimizePlan(request, context.destination(), candidates, context.days());
        List<DaySelection> selections = buildAiSelections(context.destination(), candidates, context.days(), draft);
        applyInstructionConstraints(selections, candidates, request.instruction(), context.days());
        return savePlan(context.destination(), context.startDate(), context.days(), selections, false, true);
    }

    private EditedPlanContext normalizeEditedPlan(PlanEditRequest request) {
        if (request == null || request.destination() == null) {
            throw new IllegalArgumentException("Edited plan destination is required");
        }
        int days = Math.max(1, request.days());
        LocalDate startDate = normalizeStartDate(request.startDate());
        Destination destination = normalizeEditedDestination(request.destination(), request.itinerary());
        List<DaySelection> selections = buildSelectionsFromEditedPlan(request.itinerary(), destination, days, startDate);
        return new EditedPlanContext(destination, startDate, days, selections);
    }

    private PlanResponse savePlan(Destination destination,
                                  LocalDate startDate,
                                  int days,
                                  List<DaySelection> selections) {
        return savePlan(destination, startDate, days, selections, true, false);
    }
    private PlanResponse savePlan(Destination destination,
                                  LocalDate startDate,
                                  int days,
                                  List<DaySelection> selections,
                                  boolean allowSupplementalAttractions,
                                  boolean preserveManualInput) {
        long planId = planIdGenerator.getAndIncrement();
        PlanResponse response = buildPlanResponse(
                planId,
                destination,
                startDate,
                days,
                selections,
                allowSupplementalAttractions,
                preserveManualInput
        );
        SavedPlan savedPlan = new SavedPlan(planId, destination, startDate, days, response.itinerary(), LocalDateTime.now());
        savedPlans.put(planId, savedPlan);
        return response;
    }

    private Destination normalizeEditedDestination(Destination requestedDestination, List<ItineraryDay> itinerary) {
        String city = normalizeRequestedCity(requestedDestination.city());
        Destination supportedDestination = findSupportedDestination(city);
        if (supportedDestination != null) {
            return supportedDestination;
        }
        if (hasValidCoordinates(requestedDestination.latitude(), requestedDestination.longitude())) {
            return new Destination(
                    city,
                    defaultText(requestedDestination.country()),
                    requestedDestination.latitude(),
                    requestedDestination.longitude(),
                    defaultText(requestedDestination.bestSeason()),
                    defaultText(requestedDestination.summary())
            );
        }

        Attraction firstValidAttraction = (itinerary == null ? List.<Attraction>of() : itinerary.stream()
                .flatMap(day -> day.attractions().stream())
                .filter(attraction -> hasValidCoordinates(attraction.latitude(), attraction.longitude()))
                .toList()).stream().findFirst().orElse(null);

        if (firstValidAttraction != null) {
            return new Destination(
                    city,
                    defaultText(requestedDestination.country()),
                    firstValidAttraction.latitude(),
                    firstValidAttraction.longitude(),
                    defaultText(requestedDestination.bestSeason()),
                    defaultText(requestedDestination.summary())
            );
        }
        return resolveStandardDestination(city, List.of());
    }

    private boolean hasValidCoordinates(double latitude, double longitude) {
        return Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && Math.abs(latitude) <= 90
                && Math.abs(longitude) <= 180
                && !(latitude == 0 && longitude == 0);
    }

    private List<DaySelection> buildSelectionsFromEditedPlan(List<ItineraryDay> itinerary,
                                                             Destination destination,
                                                             int days,
                                                             LocalDate startDate) {
        Map<Integer, ItineraryDay> dayMap = new LinkedHashMap<>();
        if (itinerary != null) {
            for (ItineraryDay day : itinerary) {
                if (day != null && day.day() >= 1 && day.day() <= days) {
                    dayMap.put(day.day(), day);
                }
            }
        }

        List<DaySelection> selections = new ArrayList<>();
        for (int dayNumber = 1; dayNumber <= days; dayNumber++) {
            ItineraryDay currentDay = dayMap.get(dayNumber);
            String title = currentDay != null && hasText(currentDay.title())
                    ? currentDay.title()
                    : "%s · 第%d天".formatted(startDate.plusDays(dayNumber - 1L), dayNumber);
            String theme = currentDay != null && hasText(currentDay.theme()) ? currentDay.theme() : "";
            List<Attraction> attractionsForDay = currentDay == null || currentDay.attractions() == null
                    ? List.of()
                    : currentDay.attractions();
            selections.add(new DaySelection(dayNumber, title, theme, attractionsForDay));
        }
        return selections;
    }

    private List<Attraction> collectCandidateAttractions(List<ItineraryDay> itinerary,
                                                         String city,
                                                         List<String> interests,
                                                         int days) {
        Map<String, Attraction> deduped = new LinkedHashMap<>();
        if (itinerary != null) {
            for (ItineraryDay day : itinerary) {
                if (day == null || day.attractions() == null) {
                    continue;
                }
                for (Attraction attraction : day.attractions()) {
                    if (attraction == null || !hasText(attraction.name())) {
                        continue;
                    }
                    deduped.putIfAbsent(normalizeAttractionName(attraction.name()), attraction);
                }
            }
        }

        try {
            List<Attraction> supplementalCandidates = resolveCandidateAttractions(city, interests, days);
            for (Attraction attraction : supplementalCandidates) {
                if (attraction == null || !hasText(attraction.name())) {
                    continue;
                }
                deduped.putIfAbsent(normalizeAttractionName(attraction.name()), attraction);
            }
        } catch (Exception ignored) {
        }

        if (deduped.isEmpty()) {
            throw new IllegalStateException("No attractions available for " + city);
        }
        return new ArrayList<>(deduped.values());
    }

    private void applyInstructionConstraints(List<DaySelection> selections,
                                             List<Attraction> candidates,
                                             String instruction,
                                             int days) {
        if (selections == null || selections.isEmpty() || !hasText(instruction)) {
            return;
        }

        Map<Integer, List<String>> requestedByDay = extractRequestedAttractionsByDay(instruction, days);
        if (requestedByDay.isEmpty()) {
            return;
        }

        Map<String, Attraction> candidateByName = new LinkedHashMap<>();
        if (candidates != null) {
            for (Attraction attraction : candidates) {
                if (attraction != null && hasText(attraction.name())) {
                    candidateByName.putIfAbsent(normalizeAttractionName(attraction.name()), attraction);
                }
            }
        }

        for (Map.Entry<Integer, List<String>> entry : requestedByDay.entrySet()) {
            int targetDay = entry.getKey();
            DaySelection targetSelection = selections.stream()
                    .filter(selection -> selection.day() == targetDay)
                    .findFirst()
                    .orElse(null);
            if (targetSelection == null) {
                continue;
            }

            for (String requestedName : entry.getValue()) {
                String normalizedName = normalizeAttractionName(requestedName);
                if (!hasText(normalizedName)) {
                    continue;
                }

                if (targetSelection.attractions().stream()
                        .anyMatch(attraction -> normalizeAttractionName(attraction.name()).equals(normalizedName))) {
                    continue;
                }

                Attraction matched = removeAttractionFromOtherDays(selections, targetDay, normalizedName);
                if (matched == null) {
                    matched = candidateByName.get(normalizedName);
                }
                if (matched == null) {
                    matched = findFuzzyCandidate(candidateByName, normalizedName);
                }
                if (matched == null) {
                    continue;
                }

                targetSelection.attractions().add(0, matched);
            }
        }
    }

    private Map<Integer, List<String>> extractRequestedAttractionsByDay(String instruction, int days) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        Matcher matcher = INSTRUCTION_DAY_PATTERN.matcher(instruction);
        while (matcher.find()) {
            int day = parseChineseDayToken(matcher.group(1));
            if (day < 1 || day > days) {
                continue;
            }
            String clause = matcher.group(2);
            if (!hasText(clause)) {
                continue;
            }

            String normalizedClause = clause
                    .replace("新增", " ")
                    .replace("加入", " ")
                    .replace("添加", " ")
                    .replace("安排", " ")
                    .replace("放到", " ")
                    .replace("放在", " ")
                    .replace("去", " ")
                    .replace("景点", " ")
                    .replace("行程", " ")
                    .replace("路线", " ")
                    .replace("一下", " ")
                    .trim();

            if (!hasText(normalizedClause)) {
                continue;
            }

            List<String> names = List.of(normalizedClause.split("[、,，和及 ]+")).stream()
                    .map(String::trim)
                    .filter(this::hasText)
                    .toList();
            if (names.isEmpty()) {
                continue;
            }
            result.computeIfAbsent(day, ignored -> new ArrayList<>()).addAll(names);
        }
        return result;
    }

    private int parseChineseDayToken(String token) {
        return switch (token) {
            case "一", "1" -> 1;
            case "二", "2" -> 2;
            case "三", "3" -> 3;
            case "四", "4" -> 4;
            case "五", "5" -> 5;
            case "六", "6" -> 6;
            case "七", "7" -> 7;
            default -> -1;
        };
    }

    private Attraction removeAttractionFromOtherDays(List<DaySelection> selections, int targetDay, String normalizedName) {
        for (DaySelection selection : selections) {
            if (selection.day() == targetDay) {
                continue;
            }
            for (int index = 0; index < selection.attractions().size(); index++) {
                Attraction attraction = selection.attractions().get(index);
                if (normalizeAttractionName(attraction.name()).equals(normalizedName)) {
                    selection.attractions().remove(index);
                    return attraction;
                }
            }
        }
        return null;
    }

    private Attraction findFuzzyCandidate(Map<String, Attraction> candidateByName, String normalizedName) {
        for (Map.Entry<String, Attraction> entry : candidateByName.entrySet()) {
            if (entry.getKey().contains(normalizedName) || normalizedName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
    private PlanResponse buildPlanResponse(Long planId,
                                           Destination destination,
                                           LocalDate startDate,
                                           int days,
                                           List<DaySelection> selections,
                                           boolean allowSupplementalAttractions,
                                           boolean preserveManualInput) {
        List<ItineraryDay> itinerary = selections.stream()
                .map(selection -> {
                    List<Attraction> resolvedAttractions = resolveDayAttractions(
                            destination.city(),
                            selection.attractions(),
                            allowSupplementalAttractions,
                            preserveManualInput
                    );
                    return new ItineraryDay(
                            selection.day(),
                            selection.title(),
                            selection.theme() == null || selection.theme().isBlank()
                                    ? buildTheme(resolvedAttractions)
                                    : selection.theme(),
                            estimateDistance(resolvedAttractions),
                            resolvedAttractions
                    );
                })
                .toList();
        return new PlanResponse(planId, destination, startDate, days, itinerary);
    }
    private List<Attraction> ensureCityAttractions(String city, List<Attraction> requestedAttractions) {
        return resolveDayAttractions(city, requestedAttractions, true, false);
    }
    private List<Attraction> resolveDayAttractions(String city,
                                                   List<Attraction> requestedAttractions,
                                                   boolean allowSupplementalAttractions,
                                                   boolean preserveManualInput) {
        if (amapPoiService == null) {
            return requestedAttractions == null ? List.of() : new ArrayList<>(requestedAttractions);
        }
        List<Attraction> enrichedAttractions = amapPoiService.enrichAttractions(city, requestedAttractions);
        Map<String, Attraction> deduped = new LinkedHashMap<>();
        for (Attraction attraction : enrichedAttractions) {
            deduped.putIfAbsent(normalizeAttractionName(attraction.name()), attraction);
        }
        if (preserveManualInput && requestedAttractions != null) {
            for (Attraction attraction : requestedAttractions) {
                deduped.putIfAbsent(normalizeAttractionName(attraction.name()), attraction);
            }
        }
        if (!allowSupplementalAttractions) {
            return new ArrayList<>(deduped.values());
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
        if (amapPoiService == null) {
            return new Destination(normalizedCity, "", 0, 0, "", "");
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
                    "Route suggestions based on the city and attraction wishlist you selected."
            );
        }
        return new Destination(normalizedCity, "", 0, 0, "", "Route suggestions based on the city and attraction wishlist you selected.");
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
            candidates = amapPoiService == null
                    ? List.of()
                    : amapPoiService.searchSupplementalAttractions(city, interests, Set.of(), requiredCount);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No attractions available for " + city);
        }

        List<Attraction> enrichedCandidates = amapPoiService == null ? candidates : amapPoiService.enrichAttractions(city, candidates);
        Map<String, Attraction> deduped = new LinkedHashMap<>();
        for (Attraction attraction : enrichedCandidates) {
            deduped.put(normalizeAttractionName(attraction.name()), attraction);
        }

        if (deduped.size() < requiredCount) {
            List<Attraction> supplemental = amapPoiService == null ? List.of() : amapPoiService.searchSupplementalAttractions(
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

    private PreparedStandardPlan prepareStandardPlan(PlanRequest request) {
        int days = normalizeDays(request);
        LocalDate startDate = normalizeStartDate(request.startDate());
        Destination destination = resolveStandardDestination(request.city(), request.interests());
        boolean manualMode = hasManualRequests(request.manualDays());
        boolean allowSupplementalAttractions = Boolean.TRUE.equals(request.allowSupplementalAttractions());
        if (manualMode) {
            return new PreparedStandardPlan(
                    destination,
                    startDate,
                    days,
                    buildManualSelections(destination, startDate, days, request.manualDays()),
                    allowSupplementalAttractions,
                    true
            );
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
            selections.add(new DaySelection(day, "\u7b2c " + day + " \u5929 \u00b7 " + destination.city(), null, dayAttractions));
        }
        return new PreparedStandardPlan(destination, startDate, days, selections, true, false);
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
                            ? dayDraft.title() : "\u7b2c " + day + " \u5929 \u00b7 " + destination.city(),
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
                    dayDraft != null && hasText(dayDraft.title()) ? dayDraft.title().trim() : "\u7b2c " + day + " \u5929 \u00b7 " + destination.city(),
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
                    dayDraft != null && hasText(dayDraft.title()) ? dayDraft.title().trim() : "\u7b2c " + day + " \u5929 \u00b7 " + destination.city(),
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
            List<Attraction> selected = requested.stream()
                    .filter(attraction -> usedAttractionNames.add(normalizeAttractionName(attraction.name())))
                    .toList();
            selections.add(new DaySelection(
                    day,
                    "%s \u00b7 \u7b2c%d\u5929".formatted(currentDate, day),
                    selected.isEmpty() ? "\u7b49\u5f85\u586b\u5199\u5f53\u5929\u666f\u70b9" : buildTheme(selected),
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
                    "\u7528\u6237\u624b\u586b\u666f\u70b9\uff0c\u7cfb\u7edf\u4f1a\u5c3d\u91cf\u5339\u914d\u771f\u5b9e POI\uff0c\u5e76\u8865\u5145\u6392\u5e8f\u4e0e\u4ea4\u901a\u4fe1\u606f\u3002",
                    0,
                    0,
                    4.4,
                    2,
                    List.of("\u624b\u586b"),
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
                destination.city() + " \u57ce\u5e02\u6f2b\u6b65",
                destination.city(),
                "citywalk",
                "\u0041\u0049 \u751f\u6210\u8fc7\u7a0b\u4e2d\u6682\u672a\u8865\u5168\u8be6\u60c5\u65f6\u7684\u515c\u5e95\u666f\u70b9\u3002",
                destination.latitude(),
                destination.longitude(),
                4.3,
                2,
                List.of("ai-generated", "walk"),
                List.of()
        ));
    }

    private void addGeneratedFallbackStops(Destination destination, int day, List<Attraction> results) {
        List<String> fallbackNames = List.of(
                destination.city() + " \u57ce\u5e02\u5730\u6807\u533a",
                destination.city() + " \u8001\u57ce\u6f2b\u6b65\u7ebf",
                destination.city() + " \u6cb3\u5cb8\u666f\u89c2\u5e26",
                destination.city() + " \u672c\u5730\u7f8e\u98df\u8857",
                destination.city() + " \u591c\u666f\u89c2\u666f\u70b9"
        );
        List<String> fallbackCategories = List.of("landmark", "citywalk", "view", "food", "nightview");

        int index = results.size();
        while (results.size() < MIN_ATTRACTIONS_PER_DAY && index < fallbackNames.size()) {
            results.add(new Attraction(
                    generatedAttractionIdGenerator.getAndIncrement(),
                    fallbackNames.get(index),
                    destination.city(),
                    fallbackCategories.get(index),
                    "\u7528\u4e8e\u8865\u8db3\u5f53\u5929\u8def\u7ebf\u7684\u515c\u5e95\u666f\u70b9\u3002",
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
        String description = hasText(draft.description()) ? draft.description().trim() : "\u0041\u0049 \u751f\u6210\u666f\u70b9";
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
            throw new IllegalArgumentException("Please enter a destination city.");
        }
        return switch (normalizedCity.toLowerCase(Locale.ROOT)) {
            case "shanghai" -> "\u4e0a\u6d77";
            case "tokyo" -> "\u4e1c\u4eac";
            case "chengdu" -> "\u6210\u90fd";
            case "beijing" -> "\u5317\u4eac";
            case "guangzhou" -> "\u5e7f\u5dde";
            case "shenzhen" -> "\u6df1\u5733";
            case "hangzhou" -> "\u676d\u5dde";
            default -> normalizedCity;
        };
    }

    private String supportedCitiesText() {
        return destinations.stream()
                .map(Destination::city)
                .collect(Collectors.joining(", "));
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
                .map(entry -> localizeCategory(entry.getKey()) + "\u4e3b\u9898\u8def\u7ebf")
                .orElse("\u8f7b\u677e\u57ce\u5e02\u8def\u7ebf");
    }

    private String localizeCategory(String category) {
        return switch (category == null ? "" : category.toLowerCase(Locale.ROOT)) {
            case "culture" -> "\u6587\u5316";
            case "food" -> "\u7f8e\u98df";
            case "history" -> "\u5386\u53f2";
            case "nature" -> "\u81ea\u7136";
            case "walk", "citywalk" -> "\u6f2b\u6b65";
            case "view", "viewpoint" -> "\u89c2\u666f";
            case "museum" -> "\u535a\u7269\u9986";
            case "garden" -> "\u56ed\u6797";
            case "landmark" -> "\u5730\u6807";
            case "park" -> "\u516c\u56ed";
            case "wildlife" -> "\u81ea\u7136\u751f\u6001";
            case "sight" -> "\u666f\u70b9";
            default -> category == null || category.isBlank() ? "\u7efc\u5408" : category;
        };
    }

    private double estimateDistance(List<Attraction> dayAttractions) {
        List<Attraction> validAttractions = dayAttractions.stream()
                .filter(attraction -> hasValidCoordinates(attraction.latitude(), attraction.longitude()))
                .toList();
        if (validAttractions.size() < 2) {
            return 0;
        }
        double total = 0;
        for (int index = 1; index < validAttractions.size(); index++) {
            Attraction previous = validAttractions.get(index - 1);
            Attraction current = validAttractions.get(index);
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

    private record PreparedStandardPlan(
            Destination destination,
            LocalDate startDate,
            int days,
            List<DaySelection> selections,
            boolean allowSupplementalAttractions,
            boolean preserveManualInput
    ) {
    }

    private record EditedPlanContext(
            Destination destination,
            LocalDate startDate,
            int days,
            List<DaySelection> selections
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

