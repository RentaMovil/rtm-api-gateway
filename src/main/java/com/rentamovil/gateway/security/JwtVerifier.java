package com.rentamovil.gateway.security;

import java.text.ParseException;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

// Verifica los access tokens de iam: firma RS256 con la clave pública estática de iam y vencimiento.
// Es el chequeo "barato" del gateway; cada servicio vuelve a verificar por su cuenta (decisions.md).
@Component
public class JwtVerifier {

    private final RSAKey iamPublicKey;

    public JwtVerifier(RSAKey iamPublicKey) {
        this.iamPublicKey = iamPublicKey;
    }

    // Devuelve los datos del token si es válido: firma, algoritmo y vencimiento (exp).
    public JWTClaimsSet verify(String token) throws InvalidTokenException {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            // Solo RS256: un token con "alg": "none" o HS256 se rechaza
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new RSASSAVerifier(iamPublicKey.toRSAPublicKey()))) {
                throw new InvalidTokenException();
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                throw new InvalidTokenException();
            }

            return claims;
        } catch (ParseException | JOSEException e) {
            throw new InvalidTokenException();
        }
    }

    public static class InvalidTokenException extends Exception {
        public InvalidTokenException() {
            super("Token inválido o vencido");
        }
    }
}
