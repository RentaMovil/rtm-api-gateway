package com.rentamovil.gateway.filter;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

// Si el servicio de destino está apagado o no responde, el gateway contesta 503/504 con el formato
// de error estándar, en vez de un 500 genérico (el cliente sabe que es temporal y puede reintentar).
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class UpstreamErrorFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | RuntimeException e) {
            if (response.isCommitted()) {
                throw e;
            }
            if (hasCause(e, SocketTimeoutException.class)) {
                log.warn("El servicio de {} no respondió a tiempo", request.getRequestURI());
                write(response, HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "El servicio tardó demasiado en responder");
            } else if (hasCause(e, ConnectException.class)) {
                log.warn("El servicio de {} no está disponible", request.getRequestURI());
                write(response, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "El servicio no está disponible. Intenta más tarde");
            } else {
                throw e;
            }
        }
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
