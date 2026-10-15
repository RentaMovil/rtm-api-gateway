package com.rentamovil.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.nimbusds.jwt.JWTClaimsSet;
import com.rentamovil.gateway.security.JwtVerifier;

import jakarta.servlet.http.HttpServletRequest;

class JwtAuthenticationFilterTest {

    private final JwtVerifier jwtVerifier = mock(JwtVerifier.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtVerifier);

    @Test
    void tokenValidoAgregaXUserIdYXUserRole() throws Exception {
        when(jwtVerifier.verify("bueno")).thenReturn(new JWTClaimsSet.Builder().subject("42").claim("role", "CLIENT").build());
        MockHttpServletRequest request = request("/vehicles", "Bearer bueno");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("42");
        assertThat(forwarded.getHeader("X-User-Role")).isEqualTo("CLIENT");
    }

    @Test
    void tokenInvalidoDa401YNoReenvia() throws Exception {
        when(jwtVerifier.verify("malo")).thenThrow(new JwtVerifier.InvalidTokenException());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/users/me", "Bearer malo"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void borraLosXUserQueVienenDeAfuera() throws Exception {
        MockHttpServletRequest request = request("/vehicles", null);
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-User-Role", "SUPER_ADMIN");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isNull();
        assertThat(forwarded.getHeader("X-User-Role")).isNull();
    }

    @Test
    void enRutasPublicasNoVerificaElToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        //El frontend manda el token vencido cuando pide /auth/refresh: no se debe rechazar
        filter.doFilter(request("/auth/refresh", "Bearer vencido"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(jwtVerifier, never()).verify(any());
    }

    private MockHttpServletRequest request(String path, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }
}
