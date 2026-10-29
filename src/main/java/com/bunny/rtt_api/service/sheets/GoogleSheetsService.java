package com.bunny.rtt_api.service.sheets;

import com.bunny.rtt_api.config.AppProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class GoogleSheetsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsService.class);

    private final AppProps appProps;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GoogleSheetsService(AppProps appProps, ObjectMapper objectMapper) {
        this.appProps = appProps;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean dispatch(Map<String, Object> payload) {
        String webhookUrl = appProps.sheets() != null ? appProps.sheets().webhookUrl() : null;
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Google Sheets Webhook URL is not configured. Set GOOGLE_SHEETS_WEBHOOK_URL in .env to sync.");
            return false;
        }

        String targetUrl = webhookUrl.trim();
        if (targetUrl.contains("script.google.com") && !targetUrl.endsWith("/exec")) {
            targetUrl = targetUrl.replaceAll("/+$", "") + "/exec";
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully dispatched payload to Google Sheets webhook (HTTP {})", response.statusCode());
                return true;
            } else {
                log.warn("Google Sheets webhook returned HTTP {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to post payload to Google Sheets webhook: {}", e.getMessage(), e);
            return false;
        }
    }
}
