package com.rentamovil.gateway.ratelimit;

import java.time.Duration;

// Cuenta peticiones por clave (IP o usuario) dentro de una ventana de tiempo.
// Hoy la implementación es en memoria; si un día hay varios gateways se cambia por una con Redis
// (la doc lo pide así) sin tocar el filtro.
public interface RateLimiter {

    Result tryConsume(String key, int limit, Duration window);

    // allowed = si puede pasar; remaining = cuántas le quedan; retryAfterSeconds = cuánto esperar si no
    record Result(boolean allowed, int remaining, long retryAfterSeconds) {
    }
}
