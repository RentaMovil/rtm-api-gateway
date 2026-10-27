package com.rentamovil.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

class UpstreamErrorFilterTest {

    private final UpstreamErrorFilter filter = new UpstreamErrorFilter();

    @Test
    void normalRequestPassesThroughWithoutError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void socketTimeoutExceptionReturns504GatewayTimeout() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new ServletException(new SocketTimeoutException("Read timed out"));
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(504);
        assertThat(response.getContentAsString()).contains("UPSTREAM_TIMEOUT");
        assertThat(response.getContentAsString()).contains("El servicio tardó demasiado en responder");
    }

    @Test
    void connectExceptionReturns503ServiceUnavailable() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new ServletException(new ConnectException("Connection refused"));
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("SERVICE_UNAVAILABLE");
        assertThat(response.getContentAsString()).contains("El servicio no está disponible. Intenta más tarde");
    }

    @Test
    void rethrowsExceptionWhenResponseIsAlreadyCommitted() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCommitted(true);

        FilterChain chain = (req, res) -> {
            throw new ServletException(new ConnectException("Connection refused"));
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class)
                .hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    void rethrowsOtherGenericExceptions() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/vehicles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new RuntimeException("Unexpected error");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected error");
    }
}
