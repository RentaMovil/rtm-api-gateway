# rtm-api-gateway

**Punto único de entrada** de RentaMovil. El frontend (web y móvil) solo habla con el gateway; el
gateway reenvía cada petición al servicio que corresponde según la ruta.
Documentación: `rtm-docs/09-microservices/services/01-api-gateway/` y
`rtm-docs/07-api/contracts/openapi/api-gateway.yaml`.

| | |
|---|---|
| Stack | Java 21 · Spring Boot **4.0** · Spring Cloud Gateway (WebMVC) 2025.1 · Maven |
| Puerto local | 8080 |
| Base de datos | ninguna (no guarda nada) |

> **Boot 4.0 y no 4.1** como `rtm-iam`: Spring Cloud Gateway 2025.1 está hecho para Boot 4.0.
> Cada servicio es independiente, así que pueden usar versiones distintas.

## Tabla de enrutamiento

| El cliente llama a | Va a | Ejemplo |
|---|---|---|
| `/auth/**` | `iam`, **quitando `/auth`** | `/auth/login` → iam `/login` · `/auth/password/forgot` → iam `/password/forgot` · `/auth/jwks` → iam `/jwks` |
| `/users/**` | `iam`, igual | `/users/me` → iam `/users/me` |
| `/vehicles/**`, `/branches/**` | `fleet-maintenance` (:3002) | |
| `/reservations/**`, `/rentals/**`, `/notifications/**` | `booking-reservation` (:3003) | |
| `/payments/**`, `/invoices/**`, `/bank-accounts/**` | `payment-billing` (:3004) | |
| `/tracking/**` | `telemetry-gps` (:3005) | |

Las rutas están en `src/main/resources/application.properties`.

## Qué hace además de reenviar

- **CORS:** permite llamadas desde el navegador solo a los orígenes de `CORS_ALLOWED_ORIGINS`
  (en local, Vite: `http://localhost:5173`). Otros sitios reciben 403.
- **`X-Correlation-Id`:** le pone un id a cada petición (o respeta el que traiga), lo reenvía al
  servicio y lo devuelve en la respuesta. Sirve para seguir una petición en los logs de todos los
  servicios.
- **Servicio caído:** si el servicio de destino no está corriendo responde **503**
  `SERVICE_UNAVAILABLE`; si no contesta a tiempo, **504** `UPSTREAM_TIMEOUT`.
- **`GET /health`:** estado del gateway y de cada servicio (`ok` / `degraded` / `down`). Responde
  503 solo si `iam` está caído (sin iam nadie puede iniciar sesión).

## Verificación del JWT

`JwtAuthenticationFilter` + `JwtVerifier` verifican el access token (firma RS256 con la clave
pública de iam y vencimiento) y le pasan al servicio de atrás:

- `X-User-Id`: el `user_id` (claim `sub`)
- `X-User-Role`: `CLIENT`, `ADMIN` o `SUPER_ADMIN`

| Caso | Qué hace el gateway |
|---|---|
| Ruta pública de iam (`/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/jwks`, `/auth/password/**`) o `/health` | No verifica nada (para renovar se manda el token **ya vencido**) |
| Trae token válido | Reenvía con `X-User-Id` y `X-User-Role` |
| Trae token inválido, alterado o vencido | **401** `INVALID_TOKEN`, no reenvía |
| No trae token | Reenvía **sin** `X-User-*`: el servicio decide si ese endpoint es público |
| Trae `X-User-*` desde afuera | **Se borran siempre**: solo el gateway los puede poner |

Las claves se descargan de `iam /jwks` y quedan en caché: no se llama a iam en cada petición, y si
iam cambia de clave se vuelven a pedir solas.

**Importante para cada servicio:** `X-User-*` es un atajo, no la fuente de verdad. Cada servicio
**vuelve a verificar el token** por su cuenta antes de autorizar una operación
(`01-api-gateway/decisions.md`).

## Rate limiting

| Rutas | Límite (por defecto) | Se cuenta por |
|---|---|---|
| `/auth/login`, `/auth/register`, `/auth/password/**` | 10 cada 5 min | IP |
| Todo lo demás | 100 por minuto | `X-User-Id` si hay sesión, si no IP |
| `/health` y preflight `OPTIONS` | sin límite | — |

Al pasarse responde **429** `RATE_LIMITED` con `Retry-After` (segundos que faltan). Cada respuesta
trae `X-RateLimit-Limit` y `X-RateLimit-Remaining`. Los límites se cambian en `.env`
(`RATE_LIMIT_*`).

Los contadores están **en memoria** (`InMemoryRateLimiter`): se reinician si se reinicia el
gateway y no se comparten entre varios gateways. Para eso la doc pide Redis: se haría otra
implementación de la interfaz `RateLimiter` sin tocar el filtro.

## Uso local

```bash
# 1. Configuración (una sola vez). Los valores por defecto ya sirven para local.
cp .env.example .env

# 2. Levantar primero SQL Server (rtm-iam-db: docker compose up -d) e iam (rtm-iam: mvn spring-boot:run)

# 3. Levantar el gateway
mvn spring-boot:run

# 4. Probar
curl http://localhost:8080/health
curl -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"identifier":"mgarcia","password":"SecurePass123!"}'
```

## Ramas

`feat/`, `fix/`, `chore/` → PR a `develop` (Squash and Merge). Ver
`rtm-docs/00-governance/git-conventions.md`.
