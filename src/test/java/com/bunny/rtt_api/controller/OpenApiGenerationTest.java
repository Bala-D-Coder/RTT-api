package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiGenerationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateOpenApiSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertThat(content).isNotBlank();
        assertThat(content).contains("\"openapi\":");
        assertThat(content).contains("RTT Voice Notes API");

        // Write target/openapi.json for verification and CI artifact export (G-4, OQ-2)
        Path targetDir = Paths.get("target");
        if (Files.exists(targetDir)) {
            Files.writeString(targetDir.resolve("openapi.json"), content);
        }

        System.out.println("=== SpringDoc 3.1.1 on Spring Boot 4.1.2 SUCCESS ===");
        System.out.println("Published OpenAPI spec length: " + content.length() + " bytes");
    }
}
