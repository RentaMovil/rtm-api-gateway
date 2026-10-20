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
    void validTokenAddsXUserIdAndXUserRole() throws Exception {
        when(jwtVerifier.verify("good")).thenReturn(new JWTClaimsSet.Builder().subject("42").claim("role", "CLIENT").build());
        MockHttpServletRequest request = request("/vehicles", "Bearer good");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
        assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("42");
        assertThat(forwarded.getHeader("X-User-Role")).isEqualTo("CLIENT");
    }

    @Test
    void invalidTokenReturns401AndDoesNotForward() throws Exception {
        when(jwtVerifier.verify("bad")).thenThrow(new JwtVerifier.InvalidTokenException());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/users/me", "Bearer bad"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void stripsXUserHeadersComingFromOutside() throws Exception {
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
    void publicRoutesDoNotVerifyTheToken() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        // The frontend sends the expired token when it requests /auth/refresh: it must not be rejected
        filter.doFilter(request("/auth/refresh", "Bearer expired"), new MockHttpServletResponse(), chain);

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
