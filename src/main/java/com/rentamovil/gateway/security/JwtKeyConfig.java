package com.rentamovil.gateway.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * Carga al arrancar SOLO la clave pública RS256 de iam.
 *
 * <p>El gateway nunca firma tokens, así que no necesita la clave privada.
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAKey iamPublicKey(@Value("${app.gateway.jwt.public-key}") String publicKeyText) throws Exception {

        if (publicKeyText.isBlank()) {
            throw new IllegalStateException("Falta APP_GATEWAY_JWT_PUBLIC_KEY en el .env");
        }

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new X509EncodedKeySpec(decode(publicKeyText)));

        return new RSAKey.Builder(publicKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID("rentamovil-iam")
                .build();
    }

    /** Acepta el PEM completo o solo el texto base64 en una línea (así cabe en el .env). */
    private byte[] decode(String keyText) {
        String base64 = keyText.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
