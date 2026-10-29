package com.bunny.rtt_api.config;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JacksonSanityTest {

    record SampleRecord(String noteTitle, Instant createdAt, LocalDateTime localTime) {}

    @Test
    void testJacksonClassAvailability() {
        boolean toolsJacksonPresent = false;
        boolean fasterxmlJacksonPresent = false;

        try {
            Class.forName("tools.jackson.databind.ObjectMapper");
            toolsJacksonPresent = true;
        } catch (ClassNotFoundException ignored) {}

        try {
            Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            fasterxmlJacksonPresent = true;
        } catch (ClassNotFoundException ignored) {}

        System.out.println("tools.jackson.databind.ObjectMapper present: " + toolsJacksonPresent);
        System.out.println("com.fasterxml.jackson.databind.ObjectMapper present: " + fasterxmlJacksonPresent);

        assertTrue(toolsJacksonPresent || fasterxmlJacksonPresent, "At least one Jackson version must be available");
    }
}
