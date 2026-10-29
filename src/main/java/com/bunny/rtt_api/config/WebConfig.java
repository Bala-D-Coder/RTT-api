package com.bunny.rtt_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProps appProps;

    public WebConfig(AppProps appProps) {
        this.appProps = appProps;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        AppProps.CorsProps cors = appProps.cors();
        String[] allowedOrigins = cors.allowedOrigins().toArray(String[]::new);
        String[] allowedMethods = cors.allowedMethods().toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods(allowedMethods)
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
