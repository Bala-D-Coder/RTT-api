package com.bunny.rtt_api.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void shouldRedactAuthorizationBearerTokenAndCookiesAndQueryKeys() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain filterChain = mock(FilterChain.class);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/ws/transcribe");
        when(request.getQueryString()).thenReturn("key=AIzaSyFakeSecretKey12345&ticket=secretTicketJwtValue");
        when(request.getHeader("Authorization")).thenReturn("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.sensitivePayload");
        when(request.getHeader("Cookie")).thenReturn("sessionId=xyz; refreshToken=secretRefreshToken123");
        when(response.getStatus()).thenReturn(200);

        filter.doFilter(request, response, filterChain);

        assertThat(listAppender.list).isNotEmpty();
        String loggedMessage = listAppender.list.get(0).getFormattedMessage();

        System.out.println("Actual logged message: " + loggedMessage);

        // Assert sensitive tokens are NOT present
        assertThat(loggedMessage).doesNotContain("AIzaSyFakeSecretKey12345");
        assertThat(loggedMessage).doesNotContain("secretTicketJwtValue");
        assertThat(loggedMessage).doesNotContain("sensitivePayload");
        assertThat(loggedMessage).doesNotContain("secretRefreshToken123");

        // Assert [MASKED] replaces them
        assertThat(loggedMessage).contains("[MASKED]");
        assertThat(loggedMessage).contains("key=[MASKED]");
        assertThat(loggedMessage).contains("ticket=[MASKED]");
        assertThat(loggedMessage).contains("Bearer [MASKED]");
        assertThat(loggedMessage).contains("refreshToken=[MASKED]");
    }
}
