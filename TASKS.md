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
- Último commit: 0febc4a feat(messaging): RabbitMQ topology, integration envelope publishers and idempotency guard (H2)
- Métricas: —
- Bloqueos / notas: —

## Task 3 — Gateways y dispositivos   [x] hecha
- Rama: feature/H2-devices
- Paso actual: Completado
- Archivos tocados: EdgeGateway.java, IotDevice.java, DeviceCode.java, RgbColor.java, GatewayStatus.java, DeviceStatus.java, DeviceType.java, DeviceCapability.java, AccessMode.java, CreateGatewayCommand.java, RotateGatewayApiKeyCommand.java, CreateDeviceCommand.java, UpdateDeviceCommand.java, SetDeviceMaintenanceCommand.java, DecommissionDeviceCommand.java, GetAllGatewaysQuery.java, GetGatewayByIdQuery.java, GetDevicesQuery.java, GetDeviceByIdQuery.java, DeviceRegisteredEvent.java, DeviceUpdatedEvent.java, DeviceDecommissionedEvent.java, GatewayNotFoundException.java, DeviceNotFoundException.java, DuplicateGatewayCodeException.java, DuplicateDeviceCodeException.java, EdgeGatewayRepository.java, IotDeviceRepository.java, GatewayCommandService.java, GatewayQueryService.java, DeviceCommandService.java, DeviceQueryService.java, GatewayCommandServiceImpl.java, GatewayQueryServiceImpl.java, DeviceCommandServiceImpl.java, DeviceQueryServiceImpl.java, EdgeSyncNotifier.java, NoOpEdgeSyncNotifier.java, DeviceRegistryIntegrationEventHandler.java, EdgeGatewayController.java, IotDeviceController.java, resources/**, transform/**, DeviceCodeTest.java, RgbColorTest.java, EdgeGatewayTest.java, IotDeviceTest.java, GatewayCommandServiceImplTest.java, DeviceCommandServiceImplTest.java, IotDeviceControllerIT.java
- Tests: `mvnw.cmd -B verify` → 10/10 tests OK (6 unit suites, 4 IT suites: IotDeviceControllerIT, RabbitMqConfigurationIT, ProcessedEventGuardIT, ApplicationContextIT)
- Último commit: 142e76c feat(devices): gateway and ESP32 device registry with API keys and registry events (H2)
- Métricas: —
- Bloqueos / notas: —

## Task 4 — Credenciales RFID   [x] hecha
- Rama: feature/H2-credentials
- Paso actual: Completado
- Archivos tocados: AccessCredential.java, RfidUid.java, CredentialStatus.java, SuspensionReason.java, CredentialOrigin.java, CreateCredentialCommand.java, ChangeCredentialStatusCommand.java, UpsertEdgeCredentialCommand.java, GetCredentialsQuery.java, GetCredentialsByUserQuery.java, GetCredentialByIdQuery.java, CredentialCreatedEvent.java, DuplicateRfidUidException.java, CredentialNotFoundException.java, AccessCredentialRepository.java, CredentialCommandService.java, CredentialQueryService.java, CredentialCommandServiceImpl.java, CredentialQueryServiceImpl.java, CreateCredentialResource.java, ChangeCredentialStatusResource.java, CredentialResource.java, CreateCredentialCommandFromResourceAssembler.java, CredentialResourceFromEntityAssembler.java, AccessCredentialController.java, RfidUidTest.java, AccessCredentialTest.java, CredentialCommandServiceImplTest.java, AccessCredentialControllerIT.java
- Tests: `mvnw.cmd -B verify` → 46/46 tests OK (33 unit tests, 13 ITs across AccessCredentialControllerIT, IotDeviceControllerIT, RabbitMqConfigurationIT, ProcessedEventGuardIT, ApplicationContextIT)
- Último commit: f004677 feat(credentials): RFID credentials with debt suspension and edge upsert (H2)
- Métricas: —
- Bloqueos / notas: —

## Task 5 — Sync del edge y notificación de cambios   [x] hecha
- Rama: feature/H2-edge-sync
- Paso actual: Completado
- Archivos tocados: EdgeCredentialItem.java, EdgeDeviceItem.java, EdgeReservationItem.java, EdgeSyncResource.java, EdgeSnapshotService.java, EdgeSyncNotifier.java, EdgeSyncNotifierImpl.java, EdgeGatewayAuthenticationFilter.java, EdgeSyncController.java, SecurityConfig.java, EdgeSnapshotServiceTest.java, EdgeSyncControllerIT.java
- Tests: `mvnw.cmd -B verify -Dit.test=EdgeSyncControllerIT` → PASS (7/7 tests)
- Último commit: feat(edge-sync): gateway-authenticated cache snapshot and sync-requested orders (H2)
- Métricas: Gateway authentication por API Key, snapshot completo y diferencial (incremental vacío en coincidencia de versión), sync-requested publicado tras commit.
- Bloqueos / notas: —

## Task 6 — Estado del residente y verificación de deudas (ACL hacia Residential y Payment)   [x] hecha
- Rama: feature/H2-debt-check
- Paso actual: Completado
- Archivos tocados: ResidentStatus.java, ResidentStatusRepository.java, ExternalResidentDirectory.java, ExternalDebtService.java, RestResidentialDirectory.java, RestPaymentDebtService.java, ServiceTokenProvider.java, IntegrationProperties.java, DebtCheckService.java, DebtCheckScheduler.java, ResidentStatusController.java, ResidentStatusResource.java, DebtCheckServiceTest.java, RestResidentialDirectoryTest.java, RestPaymentDebtServiceTest.java, ResidentStatusControllerIT.java
- Tests: `mvnw.cmd -B verify -Dit.test=ResidentStatusControllerIT` → PASS (WireMock ACL y fallback seguros ante caídas)
- Último commit: feat(debts): resident delinquency from Residential and Payment via service token (H2)
- Métricas: Deuda vencida suspende credenciales (`DELINQUENT`) e incrementa versión de sync; fallback a estado local si servicios externos no responden.
- Bloqueos / notas: —

## Task 7 — Reservas de puerta, autorizados y rol   [x] hecha
- Rama: feature/H2-door-reservations
- Paso actual: Completado
- Archivos tocados: DoorReservation.java, DoorAuthorization.java, ReservationStatus.java, AuthorizationStatus.java, CreateReservationCommand.java, CancelReservationCommand.java, AddAuthorizationCommand.java, RemoveAuthorizationCommand.java, GetDeviceSlotsQuery.java, GetReservationsByHolderQuery.java, GetReservationsQuery.java, GetReservationByIdQuery.java, AccessModeChangedEvent.java, ReservationConflictException.java, ReservationNotFoundException.java, DoorReservationRepository.java, ReservationCommandService.java, ReservationCommandServiceImpl.java, ReservationQueryService.java, ReservationQueryServiceImpl.java, ReservationLifecycleScheduler.java, AccessModeIntegrationEventHandler.java, DoorReservationController.java, ReservationAuthorizationController.java, CreateReservationResource.java, DoorReservationResource.java, ReservationSlotResource.java, AddAuthorizationResource.java, AuthorizationResource.java, DoorReservationTest.java, ReservationCommandServiceImplTest.java, ReservationLifecycleSchedulerTest.java, DoorReservationControllerIT.java
- Tests: `mvnw.cmd -B verify -Dit.test=DoorReservationControllerIT` → PASS (bloqueo pesimista evita solapamiento concurrente en 2 hilos, slots ocultan titular, contratos validados)
- Último commit: feat(reservations): door reservations with guest authorizations and derived access mode (H2)
- Métricas: Lock pesimista en creación; validación de morosidad (409); ciclo de vida automático SCHEDULED -> ACTIVE -> ENDED con limpieza de LED.
- Bloqueos / notas: —

## Task 8 — Consumidor de eventos del edge   [x] hecha
- Rama: feature/H2-edge-events
- Paso actual: Completado
- Archivos tocados: AccessAttempt.java, AccessResult.java, AccessReason.java, AccessAttemptRepository.java, RecordAccessAttemptCommand.java, ApplyDeviceStatusCommand.java, ApplyCommandResultCommand.java, PhysicalAccessAttemptRecordedEvent.java, PhysicalAccessIntegrationEventHandler.java, EdgeEventCommandService.java, EdgeEventCommandServiceImpl.java, EdgeEventConsumer.java, EdgeEventCommandServiceImplTest.java, EdgeEventConsumerIT.java
- Tests: `mvnw.cmd -B verify -Dit.test=EdgeEventConsumerIT` → PASS (idempotencia ×3 probada: 1 fila persistida y 1 evento publicado; DLQ ante payloads corruptos)
- Último commit: feat(edge-events): idempotent consumer for access attempts, device status and edge credentials (H2)
- Métricas: Idempotencia estricta ×3; descarte de eventos obsoletos por timestamp; enrutamiento DLQ verificado.
- Bloqueos / notas: —

## Task 9 — Comandos al nodo (LED RGB)   [x] hecha
- Rama: feature/H2-device-control
- Paso actual: Completado
- Archivos tocados: DeviceCommand.java, DeviceCommandType.java, DeviceCommandStatus.java, DeviceCommandRepository.java, SetLedColorCommand.java, DeviceControlCommandService.java, DeviceControlCommandServiceImpl.java, DeviceCommandExpiryScheduler.java, DeviceControlController.java, SetLedResource.java, DeviceCommandResource.java, DeviceControlCommandServiceImplTest.java, DeviceControlControllerIT.java
- Tests: `mvnw.cmd -B verify -Dit.test=DeviceControlControllerIT` → PASS (202 Accepted, orden publicada en topic, scheduler de expiración verificado)
- Último commit: feat(device-control): RGB LED orders to the edge with result tracking and expiry (H2)
- Métricas: Órdenes publicadas en `<gatewayCode>.device-command`; comando UUID 32 hex; resolución vía CommandResult del edge.
- Bloqueos / notas: —

## Task 10 — Vistas del residente y auditoría   [x] hecha
- Rama: feature/H2-resident-views
- Paso actual: Completado
- Archivos tocados: CurrentReservationSummaryResource.java, DoorSummaryResource.java, AccessAttemptResource.java, DoorSummaryQueryService.java, DoorSummaryQueryServiceImpl.java, GetAccessAttemptsQuery.java, GetMyAttemptsQuery.java, MyAccessController.java, AccessAuditController.java, DoorSummaryQueryServiceImplTest.java, MyAccessControllerIT.java
- Tests: `mvnw.cmd -B verify -Dit.test=MyAccessControllerIT` → PASS (contratos y límites acotados a 50 residente y 500 admin)
- Último commit: feat(me): resident door summary, my credentials and attempts, admin audit (H2)
- Métricas: Capping de intentos en query; enmascaramiento de UID; canEnter/canReserve/canChangeLed calculados dinámicamente.
- Bloqueos / notas: —

## Task 11 — Cierre de H2   [x] hecha
- Rama: feature/H2-docs
- Paso actual: Completado
- Archivos tocados: CreateCredentialLatencyIT.java, README.md, TASKS.md, Edifika-IoT-Platform/MILESTONES.md
- Tests: `mvnw.cmd -B verify -Dit.test=CreateCredentialLatencyIT` → PASS (20 POSTs, p95 < 300 ms)
- Último commit: docs: README and H2 milestone metrics (H2)
- Métricas: Idempotencia ×3 comprobada; suspensión por deuda comprobada; p95 creación credencial < 300 ms.
- Bloqueos / notas: —
