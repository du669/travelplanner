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

        RestClient client = buildClient();
        ChatCompletionRequest payload = new ChatCompletionRequest(
                properties.getModel(),
                List.of(
                        new ChatMessage("system", buildSystemPrompt(days)),
                        new ChatMessage("user", buildUserPrompt(request, destination, candidates, days))
                ),
                properties.getTemperature()
        );

        ChatCompletionResponse response = client.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(ChatCompletionResponse.class);

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

    private String buildSystemPrompt(int days) {
        return """
                You are a travel planning engine.
                Return JSON only. Do not use Markdown code fences.
                Follow this schema exactly:
                {
                  "title": "string",
                  "summary": "string",
                  "days": [
                    {
                      "day": 1,
                      "title": "string",
                      "theme": "string",
                      "attractionIds": [1, 2, 3],
                      "attractionNames": ["optional fallback names"]
                    }
                  ]
                }

                Rules:
                - Output exactly %d day objects.
                - Each day should contain 1 to 3 attractions.
                - Only select attractions from the provided candidate list.
                - Prefer attractionIds and include attractionNames as a fallback.
                - Make the plan realistic and aligned with the user's interests.
                """.formatted(days);
    }

    private String buildUserPrompt(PlanRequest request, Destination destination, List<Attraction> candidates, int days) {
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
            requestPayload.put("startDate", request.startDate());
            requestPayload.put("days", days);
            requestPayload.put("interests", request.interests());
            String requestJson = objectMapper.writeValueAsString(requestPayload);
            return """
                    Plan this trip using the candidate attractions below.

                    Trip request:
                    %s

                    Candidate attractions:
                    %s
                    """.formatted(requestJson, candidateJson);
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
