package com.rentamovil.gateway.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

// Le pone a cada petición un X-Correlation-Id (o respeta el que traiga) y lo reenvía al servicio
// de atrás y en la respuesta. Con ese id se pueden seguir los logs de una misma petición
// en el gateway, en iam y en los demás servicios.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 64) {
            correlationId = UUID.randomUUID().toString();
        }

        response.setHeader(HEADER, correlationId);
        MDC.put("correlationId", correlationId);
        try {
            chain.doFilter(new WithHeader(request, HEADER, correlationId), response);
        } finally {
            MDC.remove("correlationId");
        }
    }

    // La petición original no se puede modificar: se envuelve para agregarle (o reemplazar) un header
    static class WithHeader extends HttpServletRequestWrapper {

        private final String name;
        private final String value;

        WithHeader(HttpServletRequest request, String name, String value) {
            super(request);
            this.name = name;
            this.value = value;
        }

        @Override
        public String getHeader(String header) {
            return name.equalsIgnoreCase(header) ? value : super.getHeader(header);
        }

        @Override
        public Enumeration<String> getHeaders(String header) {
            return name.equalsIgnoreCase(header) ? Collections.enumeration(java.util.List.of(value)) : super.getHeaders(header);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            java.util.List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(name::equalsIgnoreCase)) {
                names.add(name);
            }
            return Collections.enumeration(names);
        }
    }
}
