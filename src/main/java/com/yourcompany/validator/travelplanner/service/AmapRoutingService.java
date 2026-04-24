package com.yourcompany.validator.travelplanner.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourcompany.validator.travelplanner.config.AmapProperties;
import com.yourcompany.validator.travelplanner.dto.PlanResponse;
import com.yourcompany.validator.travelplanner.dto.PlanRouteResponse;
import com.yourcompany.validator.travelplanner.model.Attraction;
import com.yourcompany.validator.travelplanner.model.ItineraryDay;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AmapRoutingService {
    private static final String AMAP_WEB_SERVICE_BASE_URL = "https://restapi.amap.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AmapProperties amapProperties;

    public AmapRoutingService(RestClient.Builder restClientBuilder,
                              ObjectMapper objectMapper,
                              AmapProperties amapProperties) {
        this.restClient = restClientBuilder.baseUrl(AMAP_WEB_SERVICE_BASE_URL).build();
        this.objectMapper = objectMapper;
        this.amapProperties = amapProperties;
    }

    public PlanRouteResponse buildRoutes(PlanResponse plan) {
        validateConfiguration();

        List<PlanRouteResponse.DayRoute> dayRoutes = new ArrayList<>();
        for (ItineraryDay day : plan.itinerary()) {
            dayRoutes.add(buildDayRoute(plan.destination().city(), day));
        }
        return new PlanRouteResponse(plan.planId(), dayRoutes);
    }

    private PlanRouteResponse.DayRoute buildDayRoute(String city, ItineraryDay day) {
        List<Attraction> attractions = day.attractions().stream()
                .filter(this::hasValidCoordinates)
                .toList();
        if (attractions == null || attractions.isEmpty()) {
            return new PlanRouteResponse.DayRoute(day.day(), 0, 0, List.of(), List.of());
        }
        if (attractions.size() == 1) {
            Attraction attraction = attractions.get(0);
            return new PlanRouteResponse.DayRoute(
                    day.day(),
                    0,
                    0,
                    List.of(new PlanRouteResponse.RoutePoint(attraction.longitude(), attraction.latitude())),
                    List.of()
            );
        }

        List<PlanRouteResponse.RouteLeg> legs = new ArrayList<>();
        List<PlanRouteResponse.RoutePoint> dayPolyline = new ArrayList<>();
        double totalDistance = 0;
        double totalDuration = 0;

        for (int index = 0; index < attractions.size() - 1; index++) {
            Attraction from = attractions.get(index);
            Attraction to = attractions.get(index + 1);
            PlanRouteResponse.RouteLeg leg = buildTransitLeg(city, index, from, to);
            legs.add(leg);
            totalDistance += leg.distanceMeters();
            totalDuration += leg.durationSeconds();
            dayPolyline.addAll(leg.polyline());
        }

        return new PlanRouteResponse.DayRoute(day.day(), totalDistance, totalDuration, dedupePolyline(dayPolyline), legs);
    }

    private PlanRouteResponse.RouteLeg buildTransitLeg(String city, int index, Attraction from, Attraction to) {
        String responseBody;
        try {
            responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/direction/transit/integrated")
                            .queryParam("origin", toLngLat(from))
                            .queryParam("destination", toLngLat(to))
                            .queryParam("city", city)
                            .queryParam("strategy", 0)
                            .queryParam("nightflag", 0)
                            .queryParam("extensions", "all")
                            .queryParam("key", amapProperties.getWebServiceKey())
                            .build())
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("AMap transit API error: " + exception.getStatusCode() + " - " + exception.getResponseBodyAsString());
        } catch (RestClientException exception) {
            throw new IllegalStateException("Failed to call AMap transit API: " + exception.getMessage(), exception);
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode transitsNode = root.path("route").path("transits");
            if (!"1".equals(readText(root, "status")) || !transitsNode.isArray() || transitsNode.isEmpty()) {
                return buildWalkingFallbackLeg(index, from, to);
            }

            JsonNode transit = transitsNode.get(0);
            List<PlanRouteResponse.RouteStep> steps = new ArrayList<>();
            List<PlanRouteResponse.RoutePoint> polyline = new ArrayList<>();
            String lineName = "";
            boolean subway = false;

            JsonNode segmentsNode = transit.path("segments");
            if (segmentsNode.isArray()) {
                for (JsonNode segment : segmentsNode) {
                    appendWalking(segment.path("walking"), steps, polyline);

                    JsonNode busLinesNode = segment.path("bus").path("buslines");
                    if (busLinesNode.isArray()) {
                        for (JsonNode busLine : busLinesNode) {
                            String currentLineName = simplifyLineName(readText(busLine, "name"));
                            boolean currentSubway = isSubway(busLine);
                            if (!StringUtils.hasText(lineName)) {
                                lineName = currentLineName;
                            }
                            subway = subway || currentSubway;
                            steps.add(new PlanRouteResponse.RouteStep(
                                    currentSubway ? "地铁" : "公交",
                                    buildBusInstruction(busLine, currentSubway),
                                    currentLineName,
                                    currentSubway,
                                    parseDouble(readText(busLine, "distance")),
                                    parseDouble(readText(busLine, "duration"))
                            ));
                            polyline.addAll(parsePolyline(readText(busLine, "polyline")));
                        }
                    }
                }
            }

            String mode;
            if (steps.stream().anyMatch(step -> "地铁".equals(step.mode()))) {
                mode = "地铁";
            } else if (steps.stream().anyMatch(step -> "公交".equals(step.mode()))) {
                mode = "公交";
            } else {
                mode = "步行";
            }

            if (polyline.isEmpty()) {
                polyline = List.of(
                        new PlanRouteResponse.RoutePoint(from.longitude(), from.latitude()),
                        new PlanRouteResponse.RoutePoint(to.longitude(), to.latitude())
                );
            }

            return new PlanRouteResponse.RouteLeg(
                    index,
                    index + 1,
                    from.name(),
                    to.name(),
                    mode,
                    lineName,
                    subway,
                    parseDouble(readText(transit, "distance")),
                    parseDouble(readText(transit, "duration")),
                    dedupePolyline(polyline),
                    steps
            );
        } catch (Exception exception) {
            return buildWalkingFallbackLeg(index, from, to);
        }
    }

    private PlanRouteResponse.RouteLeg buildWalkingFallbackLeg(int index, Attraction from, Attraction to) {
        List<PlanRouteResponse.RoutePoint> polyline = List.of(
                new PlanRouteResponse.RoutePoint(from.longitude(), from.latitude()),
                new PlanRouteResponse.RoutePoint(to.longitude(), to.latitude())
        );
        double distanceMeters = estimateDistanceMeters(from, to);
        double durationSeconds = Math.max(600, distanceMeters / 1.2);
        return new PlanRouteResponse.RouteLeg(
                index,
                index + 1,
                from.name(),
                to.name(),
                "步行",
                "",
                false,
                distanceMeters,
                durationSeconds,
                polyline,
                List.of(new PlanRouteResponse.RouteStep("步行", "步行前往下一个景点", "", false, distanceMeters, durationSeconds))
        );
    }

    private void appendWalking(JsonNode walking,
                               List<PlanRouteResponse.RouteStep> steps,
                               List<PlanRouteResponse.RoutePoint> polyline) {
        if (walking == null || walking.isMissingNode() || walking.isNull()) {
            return;
        }

        JsonNode walkingSteps = walking.path("steps");
        if (walkingSteps.isArray()) {
            for (JsonNode step : walkingSteps) {
                if (StringUtils.hasText(readText(step, "instruction"))) {
                    steps.add(new PlanRouteResponse.RouteStep(
                            "步行",
                            readText(step, "instruction"),
                            "",
                            false,
                            parseDouble(readText(step, "distance")),
                            parseDouble(readText(step, "duration"))
                    ));
                }
                polyline.addAll(parsePolyline(readText(step, "polyline")));
            }
        } else if (StringUtils.hasText(readText(walking, "distance"))) {
            steps.add(new PlanRouteResponse.RouteStep(
                    "步行",
                    "步行接驳",
                    "",
                    false,
                    parseDouble(readText(walking, "distance")),
                    parseDouble(readText(walking, "duration"))
            ));
        }
    }

    private List<PlanRouteResponse.RoutePoint> parsePolyline(String polyline) {
        if (!StringUtils.hasText(polyline)) {
            return List.of();
        }
        List<PlanRouteResponse.RoutePoint> points = new ArrayList<>();
        for (String pair : polyline.split(";")) {
            String[] parts = pair.split(",");
            if (parts.length != 2) {
                continue;
            }
            try {
                points.add(new PlanRouteResponse.RoutePoint(
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1])
                ));
            } catch (NumberFormatException ignored) {
            }
        }
        return points;
    }

    private List<PlanRouteResponse.RoutePoint> dedupePolyline(List<PlanRouteResponse.RoutePoint> source) {
        List<PlanRouteResponse.RoutePoint> results = new ArrayList<>();
        PlanRouteResponse.RoutePoint last = null;
        for (PlanRouteResponse.RoutePoint point : source) {
            if (last != null && last.longitude() == point.longitude() && last.latitude() == point.latitude()) {
                continue;
            }
            results.add(point);
            last = point;
        }
        return results;
    }

    private String buildBusInstruction(JsonNode busLine, boolean subway) {
        String departure = readNestedText(busLine, "departure_stop", "name");
        String arrival = readNestedText(busLine, "arrival_stop", "name");
        String line = simplifyLineName(readText(busLine, "name"));
        String prefix = subway ? "乘坐地铁" : "乘坐公交";
        if (StringUtils.hasText(departure) && StringUtils.hasText(arrival)) {
            return prefix + line + "，从" + departure + "到" + arrival;
        }
        return prefix + line;
    }

    private String simplifyLineName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        int idx = name.indexOf('(');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private boolean isSubway(JsonNode busLine) {
        String type = readText(busLine, "type").toLowerCase(Locale.ROOT);
        String name = readText(busLine, "name").toLowerCase(Locale.ROOT);
        return type.contains("地铁") || name.contains("地铁") || name.contains("号线");
    }

    private String toLngLat(Attraction attraction) {
        return attraction.longitude() + "," + attraction.latitude();
    }

    private boolean hasValidCoordinates(Attraction attraction) {
        if (attraction == null) {
            return false;
        }
        return Math.abs(attraction.longitude()) <= 180
                && Math.abs(attraction.latitude()) <= 90
                && !(attraction.longitude() == 0 && attraction.latitude() == 0);
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(amapProperties.getWebServiceKey())) {
            throw new IllegalStateException("Missing AMap web service key. Configure travel.map.amap.web-service-key.");
        }
    }

    private double parseDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private double estimateDistanceMeters(Attraction from, Attraction to) {
        double earthRadius = 6371000;
        double dLat = Math.toRadians(to.latitude() - from.latitude());
        double dLon = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(from.latitude())) * Math.cos(Math.toRadians(to.latitude()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String readText(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode field = node.path(fieldName);
        if (field.isMissingNode() || field.isNull()) {
            return "";
        }
        return field.isValueNode() ? field.asText("") : "";
    }

    private String readNestedText(JsonNode node, String objectFieldName, String valueFieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode nested = node.path(objectFieldName);
        if (nested.isObject()) {
            return readText(nested, valueFieldName);
        }
        return "";
    }
}
