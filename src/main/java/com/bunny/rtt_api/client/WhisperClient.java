package com.bunny.rtt_api.client;

import com.bunny.rtt_api.config.AppProps;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WhisperClient {

    private static final Logger log = LoggerFactory.getLogger(WhisperClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public record WhisperResponse(String text, String language, double duration) {}

    public WhisperClient(AppProps appProps, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        String baseUrl = appProps.whisper() != null && appProps.whisper().url() != null
                ? appProps.whisper().url()
                : "http://127.0.0.1:8001";
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public WhisperResponse transcribe(byte[] pcm16Bytes) {
        try {
            String responseBody = restClient.post()
                    .uri("/transcribe")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(pcm16Bytes)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null) {
                return new WhisperResponse("", "en", 0.0);
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String text = root.path("text").asText("");
            String language = root.path("language").asText("en");
            double duration = root.path("duration").asDouble(0.0);

            return new WhisperResponse(text, language, duration);
        } catch (Exception e) {
            log.error("Whisper sidecar call failed: {}", e.getMessage());
            return new WhisperResponse("", "en", 0.0);
        }
    }
}
