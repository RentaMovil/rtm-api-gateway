package com.rentamovil.gateway.health;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

// GET /health del gateway: dice si el gateway está arriba y pregunta el /health de cada servicio.
// Solo iam es crítico (sin iam nadie puede iniciar sesión): si iam está caído responde 503.
@RestController
public class HealthController {

    private static final long MAX_WAIT_SECONDS = 3;

    private final Map<String, String> services = new LinkedHashMap<>();
    private final RestClient restClient;
    // Hilos virtuales (Java 21): baratos, uno por servicio en cada chequeo
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public HealthController(@Value("${app.gateway.services.iam}") String iam,
                            @Value("${app.gateway.services.fleet-maintenance}") String fleet,
                            @Value("${app.gateway.services.booking-reservation}") String booking,
                            @Value("${app.gateway.services.payment-billing}") String payment,
                            @Value("${app.gateway.services.telemetry-gps}") String telemetry) {
        services.put("iam", iam);
        services.put("fleet-maintenance", fleet);
        services.put("booking-reservation", booking);
        services.put("payment-billing", payment);
        services.put("telemetry-gps", telemetry);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    // Solo dice "el gateway está vivo", sin preguntar a nadie. Es el que usa el HEALTHCHECK de Docker:
    // que un servicio de atrás esté caído no significa que el gateway esté enfermo.
    @GetMapping("/health/live")
    public Map<String, String> live() {
        return Map.of("status", "ok");
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        //Se pregunta a todos al mismo tiempo: tarda lo del más lento, no la suma de todos.
        //Dentro de Docker averiguar que un servicio NO existe puede tardar varios segundos.
        Map<String, CompletableFuture<String>> checks = new LinkedHashMap<>();
        services.forEach((name, url) -> checks.put(name, CompletableFuture
                .supplyAsync(() -> check(url), executor)
                .completeOnTimeout("down", MAX_WAIT_SECONDS, TimeUnit.SECONDS)));

        Map<String, String> upstreams = new LinkedHashMap<>();
        checks.forEach((name, future) -> upstreams.put(name, future.join()));

        boolean iamUp = "ok".equals(upstreams.get("iam"));
        boolean allUp = upstreams.values().stream().allMatch("ok"::equals);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allUp ? "ok" : "degraded");
        body.put("timestamp", Instant.now().toString());
        body.put("upstreams", upstreams);

        return ResponseEntity.status(iamUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    // ok = respondió 200, degraded = respondió pero con error (ej. 503 sin base), down = no respondió
    private String check(String baseUrl) {
        try {
            return restClient.get().uri(baseUrl + "/health")
                    .exchange((request, response) -> response.getStatusCode().is2xxSuccessful() ? "ok" : "degraded");
        } catch (Exception e) {
            return "down";
        }
    }
}
