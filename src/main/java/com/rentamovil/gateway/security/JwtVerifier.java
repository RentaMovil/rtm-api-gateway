package com.rentamovil.gateway.security;

import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

// Verifica los access tokens de iam: firma RS256 con la clave pública de iam (/jwks) y vencimiento.
// Es el chequeo "barato" del gateway; cada servicio vuelve a verificar por su cuenta (decisions.md).
@Component
public class JwtVerifier {

    private final DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();

    public JwtVerifier(@Value("${app.gateway.services.iam}") String iamUrl) throws MalformedURLException {
        // Las claves se piden a iam y se guardan en caché (no se llama a /jwks en cada petición).
        // Si llega un token con un kid desconocido (iam cambió de clave) se vuelven a pedir solas.
        JWKSource<SecurityContext> keys = JWKSourceBuilder
                .create(URI.create(iamUrl + "/jwks").toURL())
                .build();

        // Solo RS256: un token con "alg": "none" o HS256 se rechaza
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
    }

    // Devuelve los datos del token si es válido. El processor revisa firma y vencimiento (exp).
    public JWTClaimsSet verify(String token) throws InvalidTokenException, KeysUnavailableException {
        try {
            return processor.process(token, null);
        } catch (KeySourceException e) {
            throw new KeysUnavailableException(e);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new InvalidTokenException();
        }
    }

    public static class InvalidTokenException extends Exception {
        public InvalidTokenException() {
            super("Token inválido o vencido");
        }
    }

    // No se pudo descargar /jwks (iam caído): no es culpa del token
    public static class KeysUnavailableException extends Exception {
        public KeysUnavailableException(Throwable cause) {
            super("No se pudieron obtener las claves de iam", cause);
        }
    }
}
