package com.rentamovil.gateway.filter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.rentamovil.gateway.ratelimit.RateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

// Rate limiting (security-rules.md A07 y decisions.md del gateway):
// - Rutas sensibles (login, registro, recuperar contraseña): pocos intentos por IP.
// - Resto: límite general por usuario (X-User-Id, ya verificado por JwtAuthenticationFilter) o por IP.
// Va después del filtro del JWT para saber quién es el usuario.
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<String> SENSITIVE_PATHS = List.of("/auth/login", "/auth/register", "/auth/password/**");

    private final RateLimiter rateLimiter;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final int sensitiveLimit;
    private final Duration sensitiveWindow;
    private final int generalLimit;
    private final Duration generalWindow;

    public RateLimitFilter(RateLimiter rateLimiter,
                           @Value("${app.gateway.rate-limit.sensitive.limit}") int sensitiveLimit,
                           @Value("${app.gateway.rate-limit.sensitive.window}") Duration sensitiveWindow,
                           @Value("${app.gateway.rate-limit.general.limit}") int generalLimit,
                           @Value("${app.gateway.rate-limit.general.window}") Duration generalWindow) {
        this.rateLimiter = rateLimiter;
        this.sensitiveLimit = sensitiveLimit;
        this.sensitiveWindow = sensitiveWindow;
        this.generalLimit = generalLimit;
        this.generalWindow = generalWindow;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        //El preflight de CORS y el /health no cuentan
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || "/health".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        //La IP es la de quien se conecta. No se usa X-Forwarded-For: lo puede inventar el cliente.
        String ip = request.getRemoteAddr();
        RateLimiter.Result result;
        int limit;
        if (isSensitive(path)) {
            limit = sensitiveLimit;
            result = rateLimiter.tryConsume("sensitive:" + ip + ":" + path, limit, sensitiveWindow);
        } else {
            String userId = request.getHeader(JwtAuthenticationFilter.USER_ID);
            String key = userId != null ? "user:" + userId : "ip:" + ip;
            limit = generalLimit;
            result = rateLimiter.tryConsume("general:" + key, limit, generalWindow);
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));

        if (!result.allowed()) {
            log.warn("Rate limit superado en {} desde {}", path, ip);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Demasiados intentos. Espera "
                    + result.retryAfterSeconds() + " segundos\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isSensitive(String path) {
        return SENSITIVE_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
