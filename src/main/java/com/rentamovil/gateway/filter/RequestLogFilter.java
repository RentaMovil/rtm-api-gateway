package com.rentamovil.gateway.filter;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

// Una línea de log por cada petición que pasa por el gateway, ej:
//   GET /users/me -> 200 (12 ms) user=42 corr=3f2a...
// Va justo después del CorrelationIdFilter y antes que los demás, así también registra los
// 401 del JWT, los 429 del rate limit y los 503 de un servicio caído.
// No escribe cuerpos ni headers: nunca aparecen contraseñas ni tokens en el log.
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            //JwtAuthenticationFilter deja el id del usuario como atributo si el token era válido
            Object userId = request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
            log.info("{} {} -> {} ({} ms) user={} corr={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    System.currentTimeMillis() - start,
                    userId != null ? userId : "-",
                    response.getHeader(CorrelationIdFilter.HEADER));
        }
    }

    // El HEALTHCHECK de Docker llama a /health/live cada 15 s: no se registra para no llenar el log
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/health/live".equals(request.getRequestURI());
    }
}
