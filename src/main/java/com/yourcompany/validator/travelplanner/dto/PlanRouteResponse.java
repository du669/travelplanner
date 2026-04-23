package com.yourcompany.validator.travelplanner.dto;

import java.util.List;

public record PlanRouteResponse(
        Long planId,
        List<DayRoute> days
) {
    public record DayRoute(
            int day,
            double distanceMeters,
            double durationSeconds,
            List<RoutePoint> polyline,
            List<RouteLeg> legs
    ) {
    }

    public record RouteLeg(
            int fromIndex,
            int toIndex,
            String fromName,
            String toName,
            String mode,
            String lineName,
            boolean subway,
            double distanceMeters,
            double durationSeconds,
            List<RoutePoint> polyline,
            List<RouteStep> steps
    ) {
    }

    public record RouteStep(
            String mode,
            String instruction,
            String lineName,
            boolean subway,
            double distanceMeters,
            double durationSeconds
    ) {
    }

    public record RoutePoint(
            double longitude,
            double latitude
    ) {
    }
}
