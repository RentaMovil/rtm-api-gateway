package com.rentamovil.gateway.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activa la limpieza periódica de InMemoryRateLimiter.
// El Clock se inyecta para poder simular el paso del tiempo en los tests.
@Configuration
@EnableScheduling
public class RateLimitConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
