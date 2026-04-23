package com.yourcompany.validator.travelplanner.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.validator.travelplanner.config.AiModelProperties;
import com.yourcompany.validator.travelplanner.dto.AiPlanDraft;
import com.yourcompany.validator.travelplanner.dto.PlanRequest;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.Destination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiCompatibleAiPlanGenerationService implements AiPlanGenerationService {
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final AiModelProperties properties;

    public OpenAiCompatibleAiPlanGenerationService(RestClient.Builder restClientBuilder,
                                                   ObjectMapper objectMapper,
                                                   AiModelProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public AiPlanDraft generatePlan(PlanRequest request, Destination destination, List<Attraction> candidates, int days) {
        validateConfiguration();
        boolean hasLocalCandidates = candidates != null && !candidates.isEmpty();

        RestClient client = buildClient();
        ChatCompletionRequest payload = new ChatCompletionRequest(
                properties.getModel(),
                List.of(
                        new ChatMessage("system", buildSystemPrompt(days, hasLocalCandidates)),
                        new ChatMessage("user", buildUserPrompt(request, destination, candidates, days, hasLocalCandidates))
                ),
                properties.getTemperature()
        );

        String responseBody;
        try {
            responseBody = client.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize AI request payload", exception);
        } catch (RestClientResponseException exception) {
            String body = exception.getResponseBodyAsString();
            throw new IllegalStateException("AI provider error: " + exception.getStatusCode() +
                    (StringUtils.hasText(body) ? " - " + body : ""));
        } catch (RestClientException exception) {
            throw new IllegalStateException("Failed to call AI provider: " + exception.getMessage(), exception);
        }

        ChatCompletionResponse response;
        try {
            response = objectMapper.readValue(responseBody, ChatCompletionResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse AI provider response: " + responseBody, exception);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("AI model returned an empty response");
        }

        String content = response.choices().get(0).message() == null ? null : response.choices().get(0).message().content();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("AI model returned empty content");
        }

        try {
            return objectMapper.readValue(extractJson(content), AiPlanDraft.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse AI plan JSON", exception);
        }
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

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(properties.getTimeout().toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);

        return restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(trimTrailingSlash(properties.getBaseUrl()))
                .build();
    }

    private String buildSystemPrompt(int days, boolean hasLocalCandidates) {
        return """
                你是一个旅行规划引擎。
                只返回 JSON，不要输出 Markdown 代码块，不要输出任何 JSON 之外的解释。
                所有面向用户展示的文本字段都必须使用简体中文，包括 title、summary、theme、description、bestSeason、attractionNames。
                按照下面的 JSON 结构返回：
                {
                  "title": "string",
                  "summary": "string",
                  "destination": {
                    "city": "string",
                    "country": "string",
                    "latitude": 0,
                    "longitude": 0,
                    "bestSeason": "string",
                    "summary": "string"
                  },
                  "days": [
                    {
                      "day": 1,
                      "title": "string",
                      "theme": "string",
                      "attractionIds": [1, 2, 3],
                      "attractionNames": ["optional fallback names"],
                      "attractions": [
                        {
                          "id": 1,
                          "name": "string",
                          "city": "string",
                          "category": "string",
                          "description": "string",
                          "latitude": 0,
                          "longitude": 0,
                          "rating": 4.6,
                          "suggestedHours": 2,
                          "tags": ["string"]
                        }
                      ]
                    }
                  ]
                }

                规则：
                - 必须严格输出 %d 个 day 对象。
                - 每天包含 1 到 3 个景点。
                - destination 中要补全用户请求城市的摘要信息。
                - 如果提供了候选景点，只能从候选景点中选择，并优先填写 attractionIds。
                - 如果没有提供候选景点，你需要自行生成该目的地的真实感景点，并为每个景点提供大致经纬度。
                - attractionNames 可以作为候选兜底。
                - 行程风格要符合用户兴趣偏好。
                - 所有说明文字都用自然、地道、简洁的中文表达。
                """.formatted(days) + (hasLocalCandidates
                ? "\n候选景点列表就是本次可选景点的事实依据。"
                : "\n当前没有本地景点库，请你自行生成该城市可游玩的景点。");
    }

    private String buildUserPrompt(PlanRequest request, Destination destination, List<Attraction> candidates, int days, boolean hasLocalCandidates) {
        try {
            String candidateJson = objectMapper.writeValueAsString(candidates.stream()
                    .map(attraction -> {
                        Map<String, Object> candidate = new LinkedHashMap<>();
                        candidate.put("id", attraction.id());
                        candidate.put("name", attraction.name());
                        candidate.put("category", attraction.category());
                        candidate.put("rating", attraction.rating());
                        candidate.put("suggestedHours", attraction.suggestedHours());
                        candidate.put("tags", attraction.tags());
                        return candidate;
                    })
                    .toList());
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("city", destination.city());
            requestPayload.put("country", destination.country());
            requestPayload.put("bestSeason", destination.bestSeason());
            requestPayload.put("summary", destination.summary());
            requestPayload.put("startDate", request.startDate() == null ? null : request.startDate().toString());
            requestPayload.put("days", days);
            requestPayload.put("interests", request.interests());
            String requestJson = objectMapper.writeValueAsString(requestPayload);
            if (hasLocalCandidates) {
                return """
                        请基于下面的候选景点规划这次旅行。

                        旅行请求：
                        %s

                        候选景点：
                        %s
                        """.formatted(requestJson, candidateJson);
            }
            return """
                    请为一个本地数据库尚未覆盖的自定义目的地规划旅行。

                    旅行请求：
                    %s

                    当前没有这个城市的本地候选景点。
                    请你自行生成目的地摘要和详细景点。
                    每个生成的景点都必须包含大致经纬度，方便前端渲染地图。
                    """.formatted(requestJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to build AI prompt payload", exception);
        }
    }

    private String extractJson(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                trimmed = trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }

        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
    }

    private String trimTrailingSlash(String baseUrl) {
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            double temperature
    ) {
    }

    private record ChatMessage(
            String role,
            String content
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(
            List<ChatChoice> choices
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatChoice(
            ChatMessageResponse message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatMessageResponse(
            String content
    ) {
    }
}
