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

**Todavía no:** verificación del JWT (`/jwks`) + headers `X-User-Id` / `X-User-Role`, y rate
limiting. Van en las siguientes ramas.

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
