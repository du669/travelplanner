package com.yourcompany.validator.travelplanner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "travel.map.amap")
public class AmapProperties {
    private String webKey;
    private String webSecurityCode;
    private String webServiceKey;

    public String getWebKey() {
        return webKey;
    }

    public void setWebKey(String webKey) {
        this.webKey = webKey;
    }

    public String getWebSecurityCode() {
        return webSecurityCode;
    }

    public void setWebSecurityCode(String webSecurityCode) {
        this.webSecurityCode = webSecurityCode;
    }

    public String getWebServiceKey() {
        return webServiceKey;
    }

    public void setWebServiceKey(String webServiceKey) {
        this.webServiceKey = webServiceKey;
    }
}
