# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

**StockSense BD** — AI-powered inventory and demand-forecasting for mid-size retail chains (10–50 outlets) in Bangladesh. The project is being built phase by phase per `brief.md`. **Phase 0 (scaffold) and Phase 1 (core CRUD backend) are complete.**

- Base package: `com.stocksense`
- Spring Boot 4.0.2, Java 17
- Primary DB: MySQL 8 | Vector store: PostgreSQL + pgvector | Cache: Redis | Async: RabbitMQ

## Commands

Use the Maven wrapper; no global Maven needed.

```bash
# Infrastructure (start before running the app)
docker-compose up -d

# Run
./mvnw spring-boot:run          # starts on port 8080; spring-boot-docker-compose auto-starts containers if needed

# Test
./mvnw test                     # runs all tests (H2 in-memory, no containers needed)
./mvnw test -Dtest=AuthIntegrationTest#loginReturnsToken   # single test method

# Build
./mvnw package -DskipTests      # build jar
```

Use `docker-compose` (hyphen), not `docker compose` — the v2 CLI plugin is not installed; only the standalone binary is on PATH.

Docker daemon is **colima**. If it's not running: `colima start`. If colima fails with a disk-lock error, run `colima stop -f` first to clear stale state.

## Project structure

```
src/main/java/com/stocksense/
├── config/         SecurityConfig, JwtProperties, CorsProperties, AnthropicProperties
├── security/       JwtTokenProvider, JwtAuthenticationFilter, TenantContext, UserDetailsServiceImpl
├── domain/         All 14 JPA entities + Role/OrderStatus enums
├── repository/     One JpaRepository per entity
├── service/        Business logic — all methods tenant-scoped
├── agent/          Agentic layer (Phase 3+): FestivalForecastAgent, SmartReorderAgent,
│   │               AgentAuditService, RateLimiter
│   ├── anthropic/  AnthropicClient + AnthropicApi (direct Messages API client)
│   ├── tools/      FestivalAgentTools, ReorderAgentTools (function-calling tools)
│   └── job/        ReorderJob, ReorderJobPublisher, ReorderJobListener (async via RabbitMQ)
├── config/ …       + RabbitConfig (reorder queue + Jackson2 converter)
├── controller/     REST controllers (all under /api/**), incl. AgentController
├── dto/            LoginRequest, LoginResponse, RegisterRequest; dto/forecast/* (forecast output);
│                   dto/reorder/* (PurchaseOrderView, DraftLine)
├── exception/      GlobalExceptionHandler, ResourceNotFoundException, ConflictException,
│                   AgentUnavailableException (503), RateLimitExceededException (429)
└── seed/           DataSeeder (runs at startup in non-prod profiles)

src/main/resources/
├── application.properties
└── db/migration/   V1__init.sql (DDL), V2__seed_festivals.sql, V3__seed_festivals_2026_2027.sql
```

## Architecture

### Multi-tenancy
Every entity that owns data carries a `tenant_id` column. The `JwtAuthenticationFilter` extracts `tenantId` from the JWT and writes it to `TenantContext` (a ThreadLocal), cleared in `finally`. **Every service method reads `TenantContext.get()` and passes it to repository queries** — never allow cross-tenant data to leak. Do not use Hibernate multi-tenancy features; the explicit approach is intentional.

### JWT auth
`JwtTokenProvider` issues HMAC-SHA tokens containing `email`, `tenantId`, and `role`. Roles: `OWNER`, `MANAGER`, `STAFF`. Use `@PreAuthorize("hasRole('OWNER')")` / `hasAnyRole(...)` on controller methods. The token secret comes from `app.jwt.secret` (env var `JWT_SECRET` in production — never hardcode).

### Entities and key design decisions
- `AppUser` maps to table `app_users` (avoids clash with Spring Security's `User`).
- `ProductSupplier` uses `@EmbeddedId ProductSupplierId(productId, supplierId)`.
- `Sale` and `BranchStock` don't carry `tenant_id` directly — tenant is inferred through `branch_id`. Services assert branch ownership before touching these.
- `PurchaseOrder.status` lifecycle: `DRAFT → PENDING_APPROVAL → APPROVED → SENT → RECEIVED`. Only `POST /api/purchase-orders/{id}/approve` moves to APPROVED — this is the human-in-the-loop gate (do not bypass it).
- `AgentDecision` is the audit log for every agent action — every agent output must be persisted here before taking effect (Phase 3+).

### Seed data (`DataSeeder`)
Runs on `ApplicationReadyEvent` when `tenantRepo.count() == 0`, skipped in `prod` profile. Seeds: 1 tenant ("Rahim Brothers Retail"), 3 branches (Mirpur, Gulshan, Chittagong), 88 products across 8 categories, 5 suppliers, stock entries, and 12 months of 2024 synthetic sales with festival demand spikes baked in (Eid ×2.8, Eid ul-Adha ×2.5, Durga Puja ×1.9, mango season ×1.8, hilsa season ×1.5).

### Agentic layer (Phase 3 — Festival Demand Agent)
The agent lives in `com.stocksense.agent`. **We talk to the Anthropic Messages API directly via
`RestClient` (`AnthropicClient` + `AnthropicApi` records), NOT Spring AI** — Spring AI has no stable
Spring Boot 4 release yet, so its starter would break the build. The direct client implements the
same tool/function-calling loop the brief teaches.

- **Tools** (`FestivalAgentTools`): `getSalesHistory`, `getUpcomingFestivals`, `getCurrentStock`,
  plus `submitFestivalForecast` (a structured-output tool — the model calls it once to return the
  final recommendations, which ends the loop). Every tool is tenant-scoped via `TenantContext` and
  validates branch/product ownership; treat model-supplied tool inputs as untrusted.
- **Loop** (`FestivalForecastAgent`): seeds the conversation with the branch's product catalogue +
  current stock + the available-sales date window, then loops calling the model and dispatching
  tools until `submitFestivalForecast` is called or `app.anthropic.max-tool-iterations` is hit.
- **Audit**: every run is persisted to `agent_decisions` via `AgentAuditService` before returning.
- **Endpoint**: `GET /api/agents/festival-forecast?branchId=&horizonDays=` (OWNER/MANAGER).
  Also `GET /api/agents/status` (is a key configured) and `GET /api/agents/decisions?agentType=`.
- **Config / env**: `ANTHROPIC_API_KEY` (required to run — never hardcode), `ANTHROPIC_MODEL`
  (default `claude-sonnet-4-6`). When no key is set, agent endpoints return **503** and the UI shows
  a "not configured" banner. Agent calls are rate-limited per tenant via Redis (`RateLimiter`,
  fail-open).
- **Data note**: synthetic sales are seeded for the prior year; festivals are seeded through 2027
  (V3). The agent compares the prior-year festival window (where the seeded spikes live) against a
  baseline window — the system prompt and the context's "available sales window" line steer it there.
- **ObjectMapper**: Spring Boot 4 here exposes no injectable `ObjectMapper` bean and
  `jackson-datatype-jsr310` is absent, so agent classes use a private `new ObjectMapper()`. Don't
  inject a shared mapper or define a global one — it would disturb the REST layer's date formatting.

### Smart Reorder Agent (Phase 4 — async + human-in-the-loop)
`SmartReorderAgent` drafts purchase orders for products that have crossed their reorder threshold,
sizing quantities with the same festival/sales tools as Phase 3 and picking a supplier by
price/lead-time. **It runs off the request thread via RabbitMQ**, never synchronously.

- **Flow**: `POST /api/agents/reorder-scan?branchId=&horizonDays=` (OWNER/MANAGER) validates the
  branch + key, then `ReorderJobPublisher` puts a `ReorderJob` on the `stocksense.reorder.scan`
  queue and returns **202** with a `jobId`. `ReorderJobListener` consumes it, **sets `TenantContext`
  from the job payload** (ThreadLocal doesn't cross threads — this is essential), runs the agent, and
  clears the context in `finally`.
- **Tools** (`ReorderAgentTools`): delegates `getSalesHistory`/`getUpcomingFestivals` to
  `FestivalAgentTools`, adds `compareSupplierPrices(productId)` and
  `createDraftPurchaseOrder(branchId, supplierId, lines[])`, plus `finishReorderRun(summary)`.
- **Human-in-the-loop gate**: the agent only ever creates POs in `PENDING_APPROVAL`
  (`createdByAgent=true`) via `PurchaseOrderService.createAgentDraft`. The irreversible steps
  (`approve` → `send`) require a manager. Never let the agent self-approve.
- **UI**: the Orders screen has a "Run AI reorder scan" button; because the scan is async it polls
  `GET /api/purchase-orders/detailed` for ~40s until new drafts appear, then shows them with an
  "AI drafted" badge, line items, totals, and Approve/Mark-sent actions.
- **Infra**: needs RabbitMQ up (`docker-compose up -d rabbitmq`). The Jackson 2 converter
  (`Jackson2JsonMessageConverter` with `new ObjectMapper()`) is used — Spring Boot 4 ships both
  Jackson 2 and 3 AMQP converters; we standardize on Jackson 2 to match the rest of the code.

### Spring Boot 4 autoconfig modularization
Spring Boot 4 split many autoconfigurations out of `spring-boot-autoconfigure` into per-technology modules. **Flyway autoconfig is NOT triggered by `flyway-core` alone** — you must also depend on `org.springframework.boot:spring-boot-flyway`, otherwise migrations silently never run and Hibernate `validate` then fails with "missing table". Use `MySQLDialect` (Hibernate 7 removed `MySQL8Dialect`). `@AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure`.

### Testing
Integration tests use H2 in MySQL-compatibility mode with Flyway disabled (`application-test.properties`). Activate with `@ActiveProfiles("test")`. `@AutoConfigureMockMvc` is `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (Spring Boot 4 moved the package). Do not autowire `ObjectMapper` in tests — instantiate it with `new ObjectMapper()` (the bean is not exposed in the MockMvc test slice).

## Docker / infrastructure

| Service | Image | Port | Credentials |
|---|---|---|---|
| MySQL 8 | `mysql:8.0` | 3306 | stocksense / stocksense / db: stocksense |
| PostgreSQL + pgvector | `pgvector/pgvector:pg16` | 5432 | stocksense / stocksense / db: stocksense_vectors |
| Redis | `redis:7-alpine` | 6379 | — |
| RabbitMQ | `rabbitmq:3-management-alpine` | 5672, 15672 | guest / guest |

**Isolated Compose project (`name: stocksense-bd`).** The compose file pins a project name so containers are `stocksense-bd-mysql-1`, etc., and volumes are `stocksense-bd_*`. This is critical: there is a second, unrelated `~/Downloads/stocksense` project whose directory name also yields the Compose project "stocksense". Without the explicit `name:`, both projects collide on the same container names, volumes, **and** ports — and because that other project uses `mysql:latest` (9.x) while this one pins `mysql:8.0`, the collision corrupted the data dir (`Cannot upgrade from 80046 to 90701`) and reset credentials. Keep `mysql:8.0` pinned; never let `mysql:latest` touch these volumes.

**Data persistence.** Each service has a named volume, so data survives `docker restart`/`stop`/reboot **and** `docker-compose down`. Only `docker-compose down -v` wipes it; if MySQL is ever emptied, Flyway + `DataSeeder` rebuild everything on the next backend start.

**App does NOT manage containers.** `spring.docker.compose.enabled=false` — bring infra up yourself with `docker-compose up -d` before running the app. (Previously the docker-compose integration stopped MySQL/Redis/RabbitMQ on every app shutdown, so a backend crash cascaded into infra teardown.)

**The backend is NOT a container.** It runs on the host. For a backend that survives terminal/session churn, run the built jar rather than `spring-boot:run`: `./mvnw -q package -DskipTests` then `java -Xmx512m -jar target/stocksense-bd-0.0.1-SNAPSHOT.jar`. Starting the Docker stack does *not* start the API. If login 404s, the API process is down or another app holds :8080; if it 500s through the :4200 proxy, the API is unreachable.

## Build phases status

| Phase | Status | Notes |
|---|---|---|
| 0 — Scaffold | ✅ Done | MySQL, pgvector, Redis, RabbitMQ in compose; Flyway; JWT; base package `com.stocksense` |
| 1 — Core CRUD backend | ✅ Done | All 14 entities, repos, services, controllers, JWT auth, multi-tenant, seed data, integration tests |
| 2 — Angular frontend shell | ✅ Done | Angular 21, standalone components, signals, Angular Material; login, dashboard, inventory, orders, placeholder ask-ai screen |
| 3 — Festival Demand Agent | ✅ Done | Direct Anthropic Messages API client (not Spring AI — no Boot 4 release); tool-calling loop, `AgentDecision` audit, `/api/agents/festival-forecast`, live Forecast screen. Needs `ANTHROPIC_API_KEY` to run. |
| 4 — Smart Reorder Agent + HITL | ✅ Done | Async via RabbitMQ (`stocksense.reorder.scan`); drafts PENDING_APPROVAL POs sized by festival signal + supplier price; `POST /api/agents/reorder-scan` (202); Orders screen runs scan + approves. Needs `ANTHROPIC_API_KEY` + RabbitMQ. |
| 5 — Insight Agent (Text-to-SQL) | ⬜ Not started | Read-only DB user; strict guardrails |
| 6 — BD integrations | ⬜ Not started | |

## Frontend (Phase 2)

**Location:** `frontend/` — Angular 21 standalone, signals, Angular Material (azure theme).

```bash
cd frontend
npm start              # dev server on :4200, proxies /api/** → :8080
npm run build          # production build
npx ng serve --port 4200  # explicit port
```

**Structure:**
```
src/app/
├── core/
│   ├── auth/          AuthService (signals), authGuard, guestGuard
│   ├── interceptors/  authInterceptor (attaches Bearer token)
│   ├── models/        models.ts — all TypeScript interfaces
│   └── services/      ApiService (all HTTP calls), activeBranchId signal
├── shared/shell/      ShellComponent — sidenav + toolbar + branch switcher
└── features/
    ├── login/         LoginComponent
    ├── dashboard/     DashboardComponent — metric cards, festival panel, reorder queue, stock bars
    ├── inventory/     InventoryListComponent, ProductDetailComponent
    ├── orders/        OrdersComponent — runs AI reorder scan, PO cards with lines + approve/send
    ├── forecast/      ForecastComponent — runs the Festival Demand Agent, renders recommendations
    └── ask-ai/        AskAiComponent (Phase 5 placeholder)
```

**Key patterns:**
- All HTTP calls live in `ApiService` only — components never inject `HttpClient` directly.
- `AuthService` stores the JWT in `localStorage` and exposes role/email/tenantId as computed signals.
- `ApiService.activeBranchId` is a signal shared across the app; the shell's branch switcher sets it, components react with `effect()`.
- Routes are lazy-loaded (`loadComponent`). Shell wraps all authenticated routes as children.
- Dev proxy (`proxy.conf.json`) maps `/api/**` → `http://localhost:8080` so CORS is not an issue during development.
- `@angular/animations` must be installed separately — it's not bundled with `@angular/material` in Angular 21.
- Do not use `AsyncPipe` for signal-based state — use `computed()` and `effect()` instead.

## Security guardrails (enforced from Phase 3 onward)
- Claude API key via env var only (`ANTHROPIC_API_KEY`) — never in code or properties files.
- Every agent action writes to `AgentDecision` before taking effect.
- Agents never execute irreversible actions (sending a PO, payment) without human approval via the `/approve` endpoint.
- Phase 5 Insight Agent: dedicated read-only DB user, table allowlist, statement timeout, reject non-SELECT.
- Rate-limit agent endpoints via Redis.
