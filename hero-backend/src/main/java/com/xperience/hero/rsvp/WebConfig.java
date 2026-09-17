package com.xperience.hero.rsvp;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The frontend runs on a different origin from the backend (design: F7, T6), so the
 * boundary has to be configured rather than left implicit. Scoped to the dev origin
 * only — a permissive setting would expose the host view to any origin (design: S6).
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5171", "http://127.0.0.1:5171")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type");
    }
}
