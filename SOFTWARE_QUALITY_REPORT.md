# Informe de Revisión de Calidad de Software: rtm-api-gateway

**Fecha:** 27 de octubre de 2026
**Proyecto:** `rtm-api-gateway` (RentaMovil API Gateway)
**Evaluador:** Jules (Software Engineer AI)

---

## 1. Resumen Ejecutivo

El microservicio `rtm-api-gateway` actúa como el punto único de entrada del ecosistema RentaMovil. Su función primordial es el enrutamiento inverso de peticiones HTTP/HTTPS desde clientes (web/móvil) hacia los microservicios backend (`iam`, `fleet-maintenance`, `booking-reservation`, `payment-billing`, `telemetry-gps`), centralizando aspectos transversales de seguridad, control de tráfico, trazabilidad y observabilidad.

**Calificación General de Calidad:** **9.2 / 10 (Excelente)**

### Aspectos Destacados
- **Arquitectura moderna e ingeniosa:** Uso de Spring Cloud Gateway Server WebMVC alineado a la pila imperativa/Servlet de la organización, optimizado con Virtual Threads (Java 21).
- **Seguridad robusta:** Desinfección proactiva de cabeceras sensibles externas (`X-User-Id`, `X-User-Role`), verificación JWT desbalanceada mediante caché JWKS RS256, y protección contra ataques de adivinación/fuerza bruta vía Rate Limiting.
- **Trazabilidad y Observabilidad:** Inyección y propagación de `X-Correlation-Id` en MDC (Mapped Diagnostic Context) y cabeceras de reenvío, acompañado de logging no invasivo y estructurado (`RequestLogFilter`).
- **Resiliencia:** Manejo elegante de fallas aguas arriba (`UpstreamErrorFilter`) convirtiendo excepciones de conexión/timeout en respuestas HTTP estándar de Gateway (503 / 504) estructuradas en JSON.

---

## 2. Evaluación Detallada por Dimensión

### 2.1. Arquitectura y Diseño de Software (Puntaje: 9.5/10)

- **Patrón de API Gateway Centralizado:** Cumple estrictamente con el principio de ocultación de topología interna de red. La configuración en `application.properties` define una tabla de enrutamiento clara con normalización de rutas (`StripPrefix` para `/auth/**`).
- **Spring Cloud Gateway WebMVC:** Opción muy adecuada cuando no se requiere la complejidad ni la reactividad estricta de WebFlux. Se integra de manera natural con el filtro Servlet estándar (`OncePerRequestFilter`).
- **Sincronización Asíncrona con Virtual Threads:** `HealthController` utiliza `Executors.newVirtualThreadPerTaskExecutor()` para consultar en paralelo los endpoints `/health` de los 5 microservicios aguas arriba con tiempos de espera acotados (`completeOnTimeout` a 3s).
- **Separación de Responsabilidades:** Las configuraciones (`CorsConfig`, `RateLimitConfig`), los filtros (`JwtAuthenticationFilter`, `CorrelationIdFilter`, `RateLimitFilter`, `UpstreamErrorFilter`, `RequestLogFilter`), y las estrategias de rate limit (`RateLimiter`, `InMemoryRateLimiter`) están modularizados coherentemente.

---

### 2.2. Código Limpio y Mantenibilidad (Puntaje: 9.0/10)

- **Legibilidad y Comentarios:** El código cuenta con explicaciones claras sobre la intención técnica y decisiones de diseño (ej. razón de `server.forward-headers-strategy=framework`, ordenación explícita `@Order(Ordered.HIGHEST_PRECEDENCE + n)`).
- **Uso Apropriado de Java 21:** Empleo de `record` para DTOs e inmutabilidad de resultados (`RateLimiter.Result`), `List.of()`, streams y Java Clock inyectable.
- **Estructuración en Cadena de Filtros:** La secuencia de filtros de Servlet está estrictamente ordenada:
  1. `CorsFilter` (HIGHEST_PRECEDENCE)
  2. `CorrelationIdFilter` (+1)
  3. `RequestLogFilter` (+2)
  4. `UpstreamErrorFilter` (+3)
  5. `JwtAuthenticationFilter` (+4)
  6. `RateLimitFilter` (+5)

---

### 2.3. Seguridad (Puntaje: 9.2/10)

- **Sanitización de Cabeceras Internas:** `JwtAuthenticationFilter` remueve incondicionalmente cualquier cabecera `X-User-Id` o `X-User-Role` enviada por clientes externos antes de cualquier procesamiento, previniendo suplantación de identidad (Header Injection / Pre-authentication Bypassing).
- **Verificación Asimétrica de JWT (RS256):** Usa `JwtVerifier` respaldado por `nimbus-jose-jwt` y la fuente JWKS de IAM. Solo permite algoritmo RS256, rechazando firmas simétricas (HS256) o nulas (`alg: none`).
- **Configuración Restrictiva de CORS:** Expone de forma explícita los orígenes permitidos desde variables de entorno (`CORS_ALLOWED_ORIGINS`), con soporte preflight directo y control de credenciales.
- **Protección de Red:** El diseño delega la recepción directa de tráfico a NGINX como reverse proxy, utilizando `server.forward-headers-strategy=framework` para resolver IPs reales del cliente sin confiar a ciegas en clientes no autenticados.

---

### 2.4. Manejo de Errores, Bugs Potenciales y Resiliencia (Puntaje: 9.0/10)

- **Captura de Excepciones Aguas Arriba:** `UpstreamErrorFilter` intercepta `ConnectException` y `SocketTimeoutException` para evitar el escape de StackTraces de Java o respuestas HTTP 500 genéricas, devolviendo JSON estandarizado en formato `{ "error": "...", "message": "..." }`.
- **Estrategia de Rate Limiting:** `InMemoryRateLimiter` utiliza `ConcurrentHashMap.compute` atómico por clave para garantizar thread-safety en entornos multihilo concurrentes. Incluye limpieza programada periódica `@Scheduled` para mitigar consumo desmedido de memoria por acumulación de llaves.
- **Health Check Jerárquico:** Distingue entre salud liveness (`/health/live`) para orchestradores de contenedores y readiness/health degradado (`/health`), asignando un estatus crítico (503) únicamente si el servicio fundamental `iam` está fuera de servicio.

---

### 2.5. Pruebas Unitarias y Automatización (Puntaje: 8.8/10)

- **Resultados de Pruebas:** 10/10 pruebas unitarias pasando satisfactoriamente sin fallos ni regresiones.
- **Calidad de Pruebas:**
  - `JwtAuthenticationFilterTest`: Valida adición de cabeceras `X-User-*`, rechazo de tokens inválidos (401), desinfección de cabeceras externas inyectadas y exclusión de verificación para rutas públicas (`/auth/refresh`).
  - `CorrelationIdFilterTest`: Valida generación autónoma y preservación de cabeceras de correlación existentes.
  - `InMemoryRateLimiterTest`: Valida límites de ventanas de tiempo, reajustes de contadores con reloj simulado `MutableClock` y aislamiento por clave.
- **Pipeline CI/CD:** Integración en `.github/workflows/ci.yml` ejecutando `mvn test` en JDK 21 Temurin en cada PR y Push.

---

## 3. Hallazgos y Oportunidades de Mejora (Recomendaciones)

### 🔴 Recomendaciones de Arquitectura y Seguridad
1. **Migración Futura de Rate Limiter a Redis (Entornos Distribuidos):**
   - *Hallazgo:* `InMemoryRateLimiter` mantiene contadores en la JVM. Si el Gateway se escala horizontalmente a múltiples réplicas detrás de un balanceador de carga, los conteos no se comparten entre nodos.
   - *Recomendación:* Mantener la interfaz `RateLimiter` y crear una implementación basada en Spring Data Redis (`RedisRateLimiter`) utilizando scripts Lua para atrocidad al desplegar múltiples instancias en producción.

2. **Mitigación de Advertencias de Agentes Dinámicos en Mockito / JVM:**
   - *Hallazgo:* Durante las pruebas se emite la advertencia `ByteBuddy/Mockito dynamic agent loading`.
   - *Recomendación:* Configurar `mockito-subclass` o adjuntar ByteBuddy agent en la configuración de Surefire dentro del `pom.xml` para compatibilidad futura con JDKs posteriores a Java 21.

### 🟡 Recomendaciones de Manejo de Errores y Cobertura
3. **Ampliación de Pruebas para `UpstreamErrorFilter` y `HealthController`:**
   - *Hallazgo:* Aunque el comportamiento de estos componentes es limpio y funcional, actualmente no cuentan con una suite de test unitario explícito en `src/test/java`.
   - *Recomendación:* Crear `UpstreamErrorFilterTest` y `HealthControllerTest` utilizando `MockMvc` / `RestClient` mocks para asegurar que las respuestas 503/504 mantengan su contrato bajo cualquier cambio de refactorización.

---

## 4. Conclusión

El repositorio `rtm-api-gateway` presenta un nivel muy alto de calidad técnica, cumpliendo con los estándares modernos de desarrollo backend en Java 21 y Spring Boot 4.0. Las decisiones de arquitectura están ampliamente justificadas, la seguridad fue tratada prioritariamente mediante sanitización estricta y limitación de tasa, y el manejo de errores garantiza una experiencia consistente para las aplicaciones cliente del ecosistema RentaMovil.
