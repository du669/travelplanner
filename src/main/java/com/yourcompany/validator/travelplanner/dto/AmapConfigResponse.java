package com.yourcompany.validator.travelplanner.dto;

public record AmapConfigResponse(
        boolean enabled,
        String webKey,
        String securityJsCode
) {
}
