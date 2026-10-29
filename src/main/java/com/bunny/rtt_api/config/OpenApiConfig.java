package com.bunny.rtt_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RTT Voice Notes API")
                        .description("Real-time Transcription & Voice Notes REST and WebSocket API")
                        .version("1.0.0")
                        .contact(new Contact().name("RTT API Team")));
    }
}
