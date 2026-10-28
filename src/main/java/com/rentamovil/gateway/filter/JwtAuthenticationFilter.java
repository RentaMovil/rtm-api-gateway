package com.rentamovil.gateway.filter;

import java.io.IOException;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nimbusds.jwt.JWTClaimsSet;
import com.rentamovil.gateway.security.JwtVerifier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

// Verifica el access token y le pasa al servicio de atrás quién es el usuario:
// X-User-Id (claim sub) y X-User-Role (claim role).
//
// - Rutas públicas de iam: no se verifica nada (el frontend manda el token vencido al pedir /auth/refresh).
// - Resto: si viene un token tiene que ser válido (si no, 401). Si no viene, pasa sin X-User-* y el
//   servicio decide si ese endpoint es público (ej. el catálogo de vehículos).
// - Siempre se borran los X-User-* que vengan de afuera: solo el gateway los puede poner.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    // Para que RequestLogFilter pueda mostrar quién hizo la petición
    public static final String USER_ID_ATTRIBUTE = "rentamovil.userId";
    private static final String BEARER = "Bearer ";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/health/**",
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/password/**",
            "/auth/email/**");

    private final JwtVerifier jwtVerifier;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MutableHeadersRequest forwarded = new MutableHeadersRequest(request);
        forwarded.removeHeader(USER_ID);
        forwarded.removeHeader(USER_ROLE);

        //El preflight de CORS (OPTIONS) nunca trae token
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || isPublic(request.getRequestURI())) {
            chain.doFilter(forwarded, response);
            return;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)) {
            chain.doFilter(forwarded, response);
            return;
        }

        try {
            JWTClaimsSet claims = jwtVerifier.verify(authorization.substring(BEARER.length()));
            forwarded.putHeader(USER_ID, claims.getSubject());
            forwarded.putHeader(USER_ROLE, claims.getStringClaim("role"));
            request.setAttribute(USER_ID_ATTRIBUTE, claims.getSubject());
        } catch (JwtVerifier.InvalidTokenException | java.text.ParseException e) {
            writeError(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token inválido o vencido");
            return;
        }

        chain.doFilter(forwarded, response);
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
