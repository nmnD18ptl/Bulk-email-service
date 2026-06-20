package com.bulkemail.pro.config;

import com.bulkemail.pro.interceptor.ApiRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiRateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(ApiRateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                // Actuator, tracking pixel, and unsubscribe are not rate-limited here
                .excludePathPatterns(
                        "/actuator/**",
                        "/api/track/**",
                        "/api/unsubscribe/**"
                );
    }
}
