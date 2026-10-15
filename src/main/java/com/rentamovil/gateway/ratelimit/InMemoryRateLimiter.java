package com.rentamovil.gateway.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Ventana fija: cada clave tiene un contador que arranca en la primera petición y se reinicia al
// terminar la ventana (ej. 10 intentos cada 5 minutos).
// Limitación: los contadores viven en este proceso. Si se reinicia el gateway vuelven a 0, y con
// varios gateways cada uno cuenta por su lado. Para eso estaría Redis.
@Component
public class InMemoryRateLimiter implements RateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Result tryConsume(String key, int limit, Duration window) {
        Instant now = clock.instant();

        //compute es atómico por clave: dos peticiones al mismo tiempo no se pisan el contador
        Window current = windows.compute(key, (k, existing) -> {
            if (existing == null || !now.isBefore(existing.endsAt())) {
                return new Window(now.plus(window), 1);
            }
            return new Window(existing.endsAt(), existing.count() + 1);
        });

        if (current.count() <= limit) {
            return new Result(true, limit - current.count(), 0);
        }
        long retryAfter = Math.max(1, Duration.between(now, current.endsAt()).toSeconds());
        return new Result(false, 0, retryAfter);
    }

    // Cada minuto borra las ventanas vencidas para que el mapa no crezca sin fin
    @Scheduled(fixedRate = 60_000)
    public void removeExpiredWindows() {
        Instant now = clock.instant();
        windows.values().removeIf(window -> !now.isBefore(window.endsAt()));
    }

    int trackedKeys() {
        return windows.size();
    }

    private record Window(Instant endsAt, int count) {
    }
}
