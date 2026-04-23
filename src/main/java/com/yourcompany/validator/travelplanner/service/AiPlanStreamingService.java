package com.yourcompany.validator.travelplanner.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.validator.travelplanner.config.AiModelProperties;
import com.yourcompany.validator.travelplanner.dto.AiAttractionDraft;
import com.yourcompany.validator.travelplanner.dto.AiDestinationDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanDayDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanDraft;
import com.yourcompany.validator.travelplanner.dto.AiPlanStreamEvent;
import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AiPlanStreamingService {
    private final ObjectMapper objectMapper;
    private final AiModelProperties properties;
    private final TravelPlanningService travelPlanningService;

    public AiPlanStreamingService(ObjectMapper objectMapper,
                                  AiModelProperties properties,
                                  TravelPlanningService travelPlanningService) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.properties = properties;
        this.travelPlanningService = travelPlanningService;
    }

    public SseEmitter streamPlan(PlanRequest request) {
        validateConfiguration();
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                send(emitter, "status", 5, "已开始连接 AI，准备逐步生成行程。", null, null);
                AiDraftAccumulator accumulator = new AiDraftAccumulator(request.city(), request.days());
                streamFromProvider(request, emitter, accumulator);
                AiPlanDraft draft = accumulator.toDraft();
                PlanResponse finalPlan = travelPlanningService.createAiPlanFromDraft(request, draft);
                send(emitter, "complete", 100, "AI 行程生成完成。", null, finalPlan);
                emitter.complete();
            } catch (Exception exception) {
                try {
                    send(emitter, "error", 100, exception.getMessage(), null, null);
                } catch (Exception ignored) {
                }
                emitter.completeWithError(exception);
            }
        });

        return emitter;
    }

    private void streamFromProvider(PlanRequest request,
                                    SseEmitter emitter,
                                    AiDraftAccumulator accumulator) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();

        String payload = objectMapper.writeValueAsString(Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", buildSystemPrompt(request.days())),
                        Map.of("role", "user", "content", buildUserPrompt(request))
                ),
                "temperature", properties.getTemperature(),
                "stream", true
        ));

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(properties.getBaseUrl()) + "/chat/completions"))
                .timeout(properties.getTimeout())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.io.InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 400) {
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("AI provider error: " + response.statusCode() + (StringUtils.hasText(body) ? " - " + body : ""));
        }

        StringBuilder contentBuffer = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }

                String data = line.substring(5).trim();
                if (data.isBlank()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }

                JsonNode root = objectMapper.readTree(data);
                JsonNode delta = root.path("choices").isArray() && !root.path("choices").isEmpty()
                        ? root.path("choices").get(0).path("delta")
                        : null;
                if (delta == null || delta.isMissingNode()) {
                    continue;
                }

                String content = readText(delta, "content");
                if (!StringUtils.hasText(content)) {
                    continue;
                }

                contentBuffer.append(content);
                lineBuffer.append(content);
                emitParsedLines(request, emitter, accumulator, lineBuffer);
            }
        }

        emitRemainingLine(request, emitter, accumulator, lineBuffer);
    }

    private void emitParsedLines(PlanRequest request,
                                 SseEmitter emitter,
                                 AiDraftAccumulator accumulator,
                                 StringBuilder lineBuffer) throws Exception {
        int newlineIndex;
        while ((newlineIndex = lineBuffer.indexOf("\n")) >= 0) {
            String candidate = lineBuffer.substring(0, newlineIndex).trim();
            lineBuffer.delete(0, newlineIndex + 1);
            if (candidate.isBlank()) {
                continue;
            }
            if (candidate.startsWith("```")) {
                continue;
            }
            processLine(request, emitter, accumulator, candidate);
        }
    }

    private void emitRemainingLine(PlanRequest request,
                                   SseEmitter emitter,
                                   AiDraftAccumulator accumulator,
                                   StringBuilder lineBuffer) throws Exception {
        String candidate = lineBuffer.toString().trim();
        if (!candidate.isBlank() && !candidate.startsWith("```")) {
            processLine(request, emitter, accumulator, candidate);
        }
    }

    private void processLine(PlanRequest request,
                             SseEmitter emitter,
                             AiDraftAccumulator accumulator,
                             String candidate) throws Exception {
        JsonNode node;
        try {
            node = objectMapper.readTree(candidate);
        } catch (JsonProcessingException exception) {
            return;
        }

        String type = readText(node, "type");
        if (!StringUtils.hasText(type)) {
            return;
        }

        accumulator.accept(type, node);
        PlanResponse preview = travelPlanningService.previewAiPlanFromDraft(request, accumulator.toDraft());
        send(emitter, "preview", accumulator.progress(), accumulator.message(), preview, null);
    }

    private void send(SseEmitter emitter,
                      String type,
                      int progress,
                      String message,
                      PlanResponse preview,
                      PlanResponse plan) throws Exception {
        emitter.send(SseEmitter.event()
                .name(type)
                .data(new AiPlanStreamEvent(type, progress, message, preview, plan), MediaType.APPLICATION_JSON));
    }

    private void validateConfiguration() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("AI planning is disabled. Set travel.ai.enabled=true to enable it.");
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("Missing AI API key. Configure travel.ai.api-key.");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("Missing AI base URL. Configure travel.ai.base-url.");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalStateException("Missing AI model name. Configure travel.ai.model.");
        }
    }

    private String buildSystemPrompt(int days) {
        return """
                你是一个旅行规划助手。
                你必须使用流式输出，并且每次输出都只能是一行合法 JSON，不要输出 Markdown，不要输出解释。
                按如下顺序输出：
                1. 先输出 1 行 meta
                2. 然后按天输出 1 行 day
                3. 每个 day 后立即输出该天的 3 到 5 个 spot
                4. 不要预设示例景点，不要使用模板占位景点，必须根据当前城市实时生成。

                每一行 JSON 必须是单行，字段如下：
                {"type":"meta","title":"...","summary":"...","city":"...","country":"...","bestSeason":"...","destinationSummary":"..."}
                {"type":"day","day":1,"title":"...","theme":"..."}
                {"type":"spot","day":1,"name":"...","city":"...","category":"...","description":"...","latitude":0,"longitude":0,"rating":4.5,"suggestedHours":2,"tags":["..."]}

                规则：
                - 必须输出 %d 天。
                - 每个 day 下输出 3 到 5 个真实景点。
                - 所有面向用户的文本都必须是简体中文。
                - 景点必须属于对应城市。
                - 经纬度尽量给出真实值。
                - 每输出一个景点就换行，便于前端实时更新。
                """.formatted(days);
    }

    private String buildUserPrompt(PlanRequest request) {
        return """
                请为以下旅行请求逐步生成行程。
                城市：%s
                天数：%d
                出发日期：%s
                兴趣偏好：%s
                """.formatted(
                request.city(),
                request.days(),
                request.startDate(),
                request.interests() == null ? List.of() : request.interests()
        );
    }

    private String trimTrailingSlash(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return "";
        }
        return field.asText("");
    }

    private static final class AiDraftAccumulator {
        private final String requestedCity;
        private final int totalDays;
        private final Map<Integer, MutableDay> days = new LinkedHashMap<>();
        private String title = "";
        private String summary = "";
        private String country = "";
        private String bestSeason = "";
        private String destinationSummary = "";
        private String city = "";
        private int spotCount = 0;
        private String message = "AI 正在思考中...";

        private AiDraftAccumulator(String requestedCity, int totalDays) {
            this.requestedCity = requestedCity;
            this.totalDays = Math.max(1, totalDays);
        }

        private void accept(String type, JsonNode node) {
            switch (type) {
                case "meta" -> {
                    title = read(node, "title");
                    summary = read(node, "summary");
                    city = read(node, "city");
                    country = read(node, "country");
                    bestSeason = read(node, "bestSeason");
                    destinationSummary = read(node, "destinationSummary");
                    message = "已生成目的地摘要，开始拆分每天行程。";
                }
                case "day" -> {
                    int dayNumber = node.path("day").asInt();
                    MutableDay day = days.computeIfAbsent(dayNumber, MutableDay::new);
                    day.title = read(node, "title");
                    day.theme = read(node, "theme");
                    message = "第 " + dayNumber + " 天的主题已生成。";
                }
                case "spot" -> {
                    int dayNumber = node.path("day").asInt();
                    MutableDay day = days.computeIfAbsent(dayNumber, MutableDay::new);
                    day.spots.add(new AiAttractionDraft(
                            null,
                            read(node, "name"),
                            read(node, "city"),
                            read(node, "category"),
                            read(node, "description"),
                            node.path("latitude").isNumber() ? node.path("latitude").asDouble() : null,
                            node.path("longitude").isNumber() ? node.path("longitude").asDouble() : null,
                            node.path("rating").isNumber() ? node.path("rating").asDouble() : null,
                            node.path("suggestedHours").isInt() ? node.path("suggestedHours").asInt() : 2,
                            readTags(node.path("tags"))
                    ));
                    spotCount++;
                    message = "已生成第 " + dayNumber + " 天的第 " + day.spots.size() + " 个景点：" + read(node, "name");
                }
                default -> {
                }
            }
        }

        private int progress() {
            int expectedSpots = Math.max(totalDays * 4, 1);
            int base = StringUtils.hasText(title) ? 15 : 5;
            int spotProgress = Math.min(80, (spotCount * 80) / expectedSpots);
            return Math.min(95, base + spotProgress);
        }

        private String message() {
            return message;
        }

        private AiPlanDraft toDraft() {
            List<AiPlanDayDraft> draftDays = new ArrayList<>();
            for (int day = 1; day <= totalDays; day++) {
                MutableDay mutableDay = days.getOrDefault(day, new MutableDay(day));
                draftDays.add(new AiPlanDayDraft(
                        day,
                        StringUtils.hasText(mutableDay.title) ? mutableDay.title : "第" + day + "天",
                        mutableDay.theme,
                        List.of(),
                        List.of(),
                        List.copyOf(mutableDay.spots)
                ));
            }

            return new AiPlanDraft(
                    title,
                    summary,
                    new AiDestinationDraft(
                            StringUtils.hasText(city) ? city : requestedCity,
                            country,
                            null,
                            null,
                            bestSeason,
                            destinationSummary
                    ),
                    draftDays
            );
        }

        private static String read(JsonNode node, String fieldName) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                return "";
            }
            return node.path(fieldName).asText("");
        }

        private static List<String> readTags(JsonNode tagsNode) {
            if (tagsNode == null || !tagsNode.isArray()) {
                return List.of();
            }
            List<String> tags = new ArrayList<>();
            for (JsonNode tag : tagsNode) {
                if (tag.isTextual() && !tag.asText().isBlank()) {
                    tags.add(tag.asText());
                }
            }
            return tags;
        }

        private static final class MutableDay {
            private final int day;
            private String title = "";
            private String theme = "";
            private final List<AiAttractionDraft> spots = new ArrayList<>();

            private MutableDay(int day) {
                this.day = day;
            }
        }
    }
}
