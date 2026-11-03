package io.github.loadup.pay.security;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pay.service-auth")
public class ServiceAuthProperties {
    private String tokenUri;
    private String clientSecret;
    private boolean allowInsecureHttp;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private Map<String, Registration> registrations = new LinkedHashMap<>();

    public String getTokenUri() {
        return tokenUri;
    }

    public void setTokenUri(String value) {
        tokenUri = value;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String value) {
        clientSecret = value;
    }

    public boolean isAllowInsecureHttp() {
        return allowInsecureHttp;
    }

    public void setAllowInsecureHttp(boolean value) {
        allowInsecureHttp = value;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration value) {
        connectTimeout = value;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration value) {
        readTimeout = value;
    }

    public Map<String, Registration> getRegistrations() {
        return registrations;
    }

    public void setRegistrations(Map<String, Registration> value) {
        registrations = value;
    }

    public record Registration(String clientId, String tenantId, String serviceId, String audience) {}

    @Override
    public String toString() {
        return "ServiceAuthProperties[credentials hidden]";
    }
}
