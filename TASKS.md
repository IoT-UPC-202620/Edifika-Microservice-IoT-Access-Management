# TASKS — Plan 2: IoT Access Management (H2)

## Task 1 — Esqueleto del servicio desde la plantilla Payment   [x] hecha
- Rama: feature/H2-scaffold
- Paso actual: Completado
- Archivos tocados: pom.xml, Dockerfile, .env.example, .gitignore, README.md, application.properties, EdifikaMicroserviceIoTAccessManagementApplication.java, shared/**, infrastructure/authorization/sfs/**, GlobalExceptionHandler.java, ErrorResource.java, IotDeviceController.java, ExternalServiceUnavailableException.java, TestcontainersConfiguration.java, JwtTestTokens.java, ContractAssertions.java, ApplicationContextIT.java
- Tests: `mvnw.cmd -B verify` → 2/2 tests OK (ApplicationContextIT)
- Último commit: 68f7777 chore: scaffold IoT Access Management from Payment template (H2)
- Métricas: —
- Bloqueos / notas: —

## Task 2 — Infraestructura de mensajería (topología, sobre, publicadores e idempotencia)   [x] hecha
- Rama: feature/H2-messaging
- Paso actual: Completado
- Archivos tocados: pom.xml, IntegrationEnvelope.java, RabbitMqConfiguration.java, IntegrationEventPublisher.java, EdgeCommandPublisher.java, RabbitIntegrationEventPublisher.java, RabbitEdgeCommandPublisher.java, ProcessedEvent.java, ProcessedEventRepository.java, ProcessedEventGuard.java, RabbitMqConfigurationIT.java, ProcessedEventGuardIT.java, contracts/**
- Tests: `mvnw.cmd -B verify` → 5/5 tests OK (RabbitMqConfigurationIT, ProcessedEventGuardIT, ApplicationContextIT)
- Último commit: feat(messaging): RabbitMQ topology, integration envelope publishers and idempotency guard (H2)
- Métricas: —
- Bloqueos / notas: —

## Task 3 — Gateways y dispositivos   [x] hecha
- Rama: feature/H2-devices
- Paso actual: Completado
- Archivos tocados: EdgeGateway.java, IotDevice.java, DeviceCode.java, RgbColor.java, GatewayStatus.java, DeviceStatus.java, DeviceType.java, DeviceCapability.java, AccessMode.java, CreateGatewayCommand.java, RotateGatewayApiKeyCommand.java, CreateDeviceCommand.java, UpdateDeviceCommand.java, SetDeviceMaintenanceCommand.java, DecommissionDeviceCommand.java, GetAllGatewaysQuery.java, GetGatewayByIdQuery.java, GetDevicesQuery.java, GetDeviceByIdQuery.java, DeviceRegisteredEvent.java, DeviceUpdatedEvent.java, DeviceDecommissionedEvent.java, GatewayNotFoundException.java, DeviceNotFoundException.java, DuplicateGatewayCodeException.java, DuplicateDeviceCodeException.java, EdgeGatewayRepository.java, IotDeviceRepository.java, GatewayCommandService.java, GatewayQueryService.java, DeviceCommandService.java, DeviceQueryService.java, GatewayCommandServiceImpl.java, GatewayQueryServiceImpl.java, DeviceCommandServiceImpl.java, DeviceQueryServiceImpl.java, EdgeSyncNotifier.java, NoOpEdgeSyncNotifier.java, DeviceRegistryIntegrationEventHandler.java, EdgeGatewayController.java, IotDeviceController.java, resources/**, transform/**, DeviceCodeTest.java, RgbColorTest.java, EdgeGatewayTest.java, IotDeviceTest.java, GatewayCommandServiceImplTest.java, DeviceCommandServiceImplTest.java, IotDeviceControllerIT.java
- Tests: `mvnw.cmd -B verify` → 10/10 tests OK (6 unit suites, 4 IT suites: IotDeviceControllerIT, RabbitMqConfigurationIT, ProcessedEventGuardIT, ApplicationContextIT)
- Último commit: feat(devices): gateway and ESP32 device registry with API keys and registry events (H2)
- Métricas: —
- Bloqueos / notas: —

## Task 4 — Credenciales RFID   [x] hecha
- Rama: feature/H2-credentials
- Paso actual: Completado
- Archivos tocados: AccessCredential.java, RfidUid.java, CredentialStatus.java, SuspensionReason.java, CredentialOrigin.java, CreateCredentialCommand.java, ChangeCredentialStatusCommand.java, UpsertEdgeCredentialCommand.java, GetCredentialsQuery.java, GetCredentialsByUserQuery.java, GetCredentialByIdQuery.java, CredentialCreatedEvent.java, DuplicateRfidUidException.java, CredentialNotFoundException.java, AccessCredentialRepository.java, CredentialCommandService.java, CredentialQueryService.java, CredentialCommandServiceImpl.java, CredentialQueryServiceImpl.java, CreateCredentialResource.java, ChangeCredentialStatusResource.java, CredentialResource.java, CreateCredentialCommandFromResourceAssembler.java, CredentialResourceFromEntityAssembler.java, AccessCredentialController.java, RfidUidTest.java, AccessCredentialTest.java, CredentialCommandServiceImplTest.java, AccessCredentialControllerIT.java
- Tests: `mvnw.cmd -B verify` → 46/46 tests OK (33 unit tests, 13 ITs across AccessCredentialControllerIT, IotDeviceControllerIT, RabbitMqConfigurationIT, ProcessedEventGuardIT, ApplicationContextIT)
- Último commit: feat(credentials): RFID credentials with debt suspension and edge upsert (H2)
- Métricas: —
- Bloqueos / notas: —

## Task 5 — Sync del edge y notificación de cambios   [ ] pendiente
- Rama: feature/H2-edge-sync
- Paso actual: pendiente
- Archivos tocados: —
- Tests: —
- Último commit: —
- Métricas: —
- Bloqueos / notas: —

## Task 6 — Estado del residente y verificación de deudas (ACL hacia Residential y Payment)   [ ] pendiente
- Rama: feature/H2-debt-check
- Paso actual: pendiente
- Archivos tocados: —
- Tests: —
- Último commit: —
- Métricas: —
- Bloqueos / notas: —

## Task 7 — Reservas de puerta, autorizados y rol   [ ] pendiente
- Rama: feature/H2-door-reservations
- Paso actual: pendiente
- Archivos tocados: —
- Tests: —
- Último commit: —
- Métricas: —
- Bloqueos / notas: —

## Task 8 — Consumidor de eventos del edge   [ ] pendiente
- Rama: feature/H2-edge-events
- Paso actual: pendiente
- Archivos tocados: —
- Tests: —
- Último commit: —
- Métricas: —
- Bloqueos / notas: —

## Task 9 — Comandos al nodo (LED RGB)   [ ] pendiente
- Rama: feature/H2-device-control
- Paso actual: pendiente
- Archivos tocados: —
- Tests: —
- Último commit: —
- Métricas: —
- Bloqueos / notas: —

## Task 10 — Vistas del residente y auditoría   [ ] pendiente
- Rama: feature/H2-resident-views
- Paso actual: pendiente
- Archivos tocados: —
- Tests: —
- Último commit: —
- Métricas: —
- Bloqueos / notas: —

## Task 11 — Cierre de H2   [ ] pendiente
- Rama: feature/H2-docs
- Paso actual: pendiente
- Archivos tocados: —
- Tests: —
- Último commit: —
- Métricas: —
- Bloqueos / notas: —
