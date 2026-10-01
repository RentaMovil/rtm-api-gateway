package com.rentamovil.gateway.health;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

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

    private final Map<String, String> services = new LinkedHashMap<>();
    private final RestClient restClient;

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

        //Si un servicio no contesta en 2 segundos se marca como caído; el /health no se queda colgado
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, String> upstreams = new LinkedHashMap<>();
        services.forEach((name, url) -> upstreams.put(name, check(url)));

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
