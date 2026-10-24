# Edifika - Microservice IoT Access Management

Microservicio Spring Boot que gestiona los ESP32 y sus comunicaciones: gateways, nodos, tarjetas RFID, reservas de puerta con autorizados, verificación de deudas, órdenes al nodo (LED RGB), auditoría de accesos, sincronización de caché para edge gateways y procesamiento idempotente de eventos AMQP.

## Requisitos y Tecnologías

- **Java:** 21 (LTS)
- **Framework:** Spring Boot 4.0.6
- **Base de Datos:** PostgreSQL 17 (vía Spring Data JPA / Hibernate)
- **Mensajería:** Spring AMQP + RabbitMQ 4
- **Seguridad:** Spring Security con JWT (jjwt 0.12.3) y autenticación por API Key para edge gateways
- **Documentación OpenAPI:** springdoc-openapi 3.0.3
- **Testing:** JUnit 5, Mockito, Testcontainers (PostgreSQL & RabbitMQ), WireMock 3.13.1, NetworkNT JSON Schema Validator 1.5.6

> **Nota sobre MessageConverter:** Se utiliza `Jackson2JsonMessageConverter` provisto por Spring AMQP configurado con JavaTimeModule y serialización ISO-8601 de fechas en UTC (`Instant`).

---

## Configuración y Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|---|---|---|
| `PORT` | Puerto HTTP del microservicio | `8089` |
| `DATABASE_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/access_db` |
| `DATABASE_USERNAME` | Usuario de base de datos | `postgres` |
| `DATABASE_PASSWORD` | Contraseña de base de datos | `postgres` |
| `RABBITMQ_HOST` | Host del broker RabbitMQ | `localhost` |
| `RABBITMQ_PORT` | Puerto AMQP de RabbitMQ | `5672` |
| `RABBITMQ_USERNAME` | Usuario de RabbitMQ | `guest` |
| `RABBITMQ_PASSWORD` | Contraseña de RabbitMQ | `guest` |
| `RABBITMQ_VIRTUAL_HOST` | Virtual host RabbitMQ | `/` |
| `JWT_SECRET` | Clave secreta HMAC para validar JWT de usuarios | `development-secret-key-at-least-256-bits-long-for-jwt` |
| `INTEGRATION_RESIDENTIAL_URL` | URL base del microservicio Residential | `http://localhost:8081` |
| `INTEGRATION_PAYMENT_URL` | URL base del microservicio Payment | `http://localhost:8084` |
| `INTEGRATION_DEBT_CHECK_ENABLED` | Activa consulta de morosidad a Residential/Payment | `true` |
| `INTEGRATION_DEBT_CHECK_INTERVAL_MINUTES` | Frecuencia de verificación programada | `5` |
| `INTEGRATION_TIMEZONE` | Zona horaria para corte de deudas | `America/Lima` |
| `INTEGRATION_TIMEOUT_SECONDS` | Timeout de llamadas REST externas (ACL) | `5` |
| `RESERVATION_MAX_HOURS` | Ventana máxima de una reserva de puerta | `12` |
| `DEVICE_COMMAND_TTL_SECONDS` | Tiempo de vida para expiración de órdenes al nodo | `30` |

---

## Ejecución

### Modo Desarrollo

```powershell
.\mvnw.cmd spring-boot:run
```

O en Linux / macOS:

```bash
./mvnw spring-boot:run
```

### OpenAPI / Swagger

Una vez iniciado el microservicio:
- **Swagger UI:** `http://localhost:8089/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8089/v3/api-docs`

---

## Topología de Mensajería (RabbitMQ)

### Exchanges
- `edifika.events` (`topic`, durable): bus unificado de eventos de dominio e integración.
- `edifika.edge.commands` (`topic`, durable): canal de órdenes hacia los edge gateways.
- `edifika.dlx` (`topic`, durable): Dead Letter Exchange.

### Colas y Enlaces
- `access.edge-events` (durable):
  - Configurada con `x-dead-letter-exchange: edifika.dlx` y `x-dead-letter-routing-key: access.edge-events`.
  - Suscrita a `edifika.events` con las routing keys:
    - `edge.access-attempt`
    - `edge.device-status`
    - `edge.credential-changed`
    - `edge.command-result`
- `access.edge-events.dlq` (durable): enlazada a `edifika.dlx` con routing key `access.edge-events`.

### Eventos Publicados
- A `edifika.events`:
  - `access.physical-access-granted` (intento de acceso concedido)
  - `access.physical-access-denied` (intento de acceso denegado)
  - `iot.device-registered` (nodo IoT creado)
  - `iot.device-updated` (nodo IoT modificado)
  - `iot.device-decommissioned` (nodo IoT desmantelado)
  - `iot.access-mode-changed` (modo de acceso cambiado: `FREE_ACCESS` <-> `RESERVED`)
- A `edifika.edge.commands`:
  - `<gatewayCode>.sync-requested` (solicitud al gateway de invalidar caché e invocar sync)
  - `<gatewayCode>.device-command` (orden enviada al ESP32, ej. fijar color LED RGB)

---

## Endpoints Principales

### 1. Gateways y Dispositivos
- `POST /api/v1/iot/gateways` — Registrar edge gateway.
- `GET /api/v1/iot/gateways` — Listar gateways.
- `GET /api/v1/iot/gateways/{id}` — Detalle de gateway.
- `POST /api/v1/iot/gateways/{id}/rotate-api-key` — Rotar API Key.
- `POST /api/v1/iot/devices` — Registrar dispositivo IoT (puerta, torniquete, sensor).
- `GET /api/v1/iot/devices` — Listar dispositivos IoT.
- `GET /api/v1/iot/devices/{id}` — Detalle de dispositivo IoT.
- `PUT /api/v1/iot/devices/{id}` — Actualizar dispositivo IoT.
- `PATCH /api/v1/iot/devices/{id}/maintenance` — Alternar estado de mantenimiento.
- `DELETE /api/v1/iot/devices/{id}` — Desmantelar dispositivo (DECOMMISSIONED).

### 2. Sincronización del Edge Gateway
- `GET /api/v1/edge/sync?since={version}` — Snapshot completo o diferencial para el gateway autenticado con cabeceras `X-Gateway-Id` y `Authorization: Bearer <apiKey>`.

### 3. Credenciales RFID
- `POST /api/v1/access/credentials` — Registrar credencial RFID.
- `GET /api/v1/access/credentials` — Listar credenciales con filtros.
- `PATCH /api/v1/access/credentials/{id}/status` — Cambiar estado (`ACTIVE`, `SUSPENDED`, `REVOKED`, `LOST`).

### 4. Reservas de Puerta y Autorizaciones
- `POST /api/v1/access/reservations` — Crear reserva de puerta.
- `GET /api/v1/access/reservations` — Listar reservas con filtros temporales.
- `GET /api/v1/access/reservations/{id}` — Detalle de reserva.
- `DELETE /api/v1/access/reservations/{id}` — Cancelar reserva.
- `GET /api/v1/access/devices/{id}/slots` — Obtener disponibilidad y franjas de reserva.
- `POST /api/v1/access/reservations/{id}/authorizations` — Agregar invitado a la reserva.
- `DELETE /api/v1/access/reservations/{id}/authorizations/{userId}` — Revocar invitado.

### 5. Control de Dispositivos (LED RGB)
- `PUT /api/v1/iot/devices/{id}/led` — Enviar orden de color LED RGB (requiere reserva activa).
- `GET /api/v1/iot/device-commands/{commandId}` — Consultar estado del comando (`PENDING`, `OK`, `FAILED`, `EXPIRED`).

### 6. Vistas del Residente y Auditoría
- `GET /api/v1/access/me/doors` — Resumen de puertas del residente (`canEnter`, `canReserve`, `canChangeLed`).
- `GET /api/v1/access/me/credentials` — Credenciales RFID activas del residente (UID enmascarado).
- `GET /api/v1/access/me/attempts` — Historial de intentos de acceso del residente (tope 50).
- `GET /api/v1/access/attempts` — Auditoría global de accesos para administradores (tope 500).

### 7. Verificación de Morosidad
- `GET /api/v1/access/residents/{userId}/status` — Consultar estado de morosidad en caché.
- `POST /api/v1/access/residents/{userId}/debt-check` — Forzar verificación de morosidad con servicios externos.

---

## Pruebas

### Pruebas Unitarias
Ejecuta las pruebas unitarias sin dependencias externas:
```powershell
.\mvnw.cmd test
```

### Pruebas de Integración y Contratos
Utilizan Testcontainers (PostgreSQL 17 y RabbitMQ 4) y WireMock:
```powershell
.\mvnw.cmd -B verify
```

### Prueba de Rendimiento / Latencia
Mide el p95 de latencia en la creación secuencial de 20 credenciales (`p95 < 300 ms`):
```powershell
.\mvnw.cmd -B verify -Dit.test=CreateCredentialLatencyIT
```
