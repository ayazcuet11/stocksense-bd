# StockSense BD — Implementation Brief

> An AI-powered inventory and demand-forecasting system for mid-size retail chains (10–50 outlets) in Bangladesh. The differentiator is a "Festival Demand Agent" that predicts seasonal surges (Eid, Puja, Pohela Boishakh, monsoon, mango/hilsa season) at the branch level, plus a human-in-the-loop "Smart Reorder Agent."

This brief is written to be handed directly to Claude Code. Build in the order given. Each phase is independently runnable and testable.

---

## 1. Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Frontend | Angular 17+ (standalone components, signals) | Angular Material or PrimeNG for UI |
| Backend | Spring Boot 3.x (Java 21) | Spring Web, Spring Data JPA, Spring Security |
| Agentic layer | Spring AI + Anthropic Claude | `spring-ai-anthropic-spring-boot-starter` |
| Primary DB | MySQL 8 | Transactional data |
| Vector store | PostgreSQL + pgvector | Product embeddings for RAG (can defer to Phase 4) |
| Cache | Redis | Sessions, rate limiting |
| Async | Kafka or RabbitMQ | Agent jobs run as background tasks |
| Auth | JWT, role-based | Roles: OWNER, MANAGER, STAFF |
| Build | Maven (backend), npm (frontend) | |
| Containers | Docker + docker-compose | One compose file for the whole local stack |

Keep MySQL for core data and Postgres/pgvector only for embeddings. Don't merge them.

---

## 2. Domain model (core entities)

Build these JPA entities first. Multi-tenant from day one — every row carries a `tenant_id` (the retail chain).

- `Tenant` — the retail chain (id, name, created_at)
- `Branch` — an outlet (id, tenant_id, name, address, city, phone)
- `User` — (id, tenant_id, name, email, password_hash, role, branch_id nullable)
- `Category` — product category (id, tenant_id, name)
- `Product` — (id, tenant_id, sku, name, category_id, unit, vat_rate)
- `BranchStock` — stock per product per branch (id, branch_id, product_id, quantity, reorder_threshold, updated_at)
- `Supplier` — (id, tenant_id, name, phone, lead_time_days)
- `ProductSupplier` — price per product per supplier (product_id, supplier_id, unit_price)
- `Sale` — a sale transaction (id, branch_id, sold_at, total_amount, vat_amount)
- `SaleLine` — line items (id, sale_id, product_id, quantity, unit_price)
- `PurchaseOrder` — (id, tenant_id, branch_id, supplier_id, status, created_by_agent, approved_by, created_at) — status: DRAFT, PENDING_APPROVAL, APPROVED, SENT, RECEIVED
- `PurchaseOrderLine` — (id, po_id, product_id, quantity, unit_price)
- `FestivalEvent` — (id, name, type, gregorian_date, hijri_date nullable) — seed with BD festivals
- `AgentDecision` — audit log for every agent action (id, agent_type, input_summary, output_summary, reasoning, created_at, approved bool) — critical for a regulated, trust-sensitive product

---

## 3. Build phases

### Phase 0 — Project scaffold (½ day)
- Spring Boot project via Spring Initializr (Web, JPA, Security, Validation, MySQL driver, Redis, Spring AI Anthropic).
- Angular project with routing and Angular Material.
- `docker-compose.yml` with MySQL, Postgres+pgvector, Redis, and the message broker.
- Flyway or Liquibase for DB migrations.
- Health-check endpoint. Confirm the whole stack runs with one command.

### Phase 1 — Core CRUD backend (3–4 days) — *familiar territory, move fast*
- All entities above with repositories, services, REST controllers.
- JWT auth + role-based access (OWNER/MANAGER/STAFF).
- Multi-tenant filtering (every query scoped to tenant_id — use a Hibernate filter or a tenant interceptor).
- Seed data script: 1 tenant, 3 branches, ~100 products, ~12 months of synthetic sales (with deliberate festival spikes baked in so the agent has signal to find later).
- Validation, error handling, basic integration tests.

### Phase 2 — Angular frontend shell (3–4 days)
- Login + JWT handling + route guards by role.
- Dashboard screen (mirror the mockup: metric cards, festival alert panel, reorder queue, stock-health bars).
- Inventory list + product detail.
- Branch switcher in the top bar.
- Wire everything to the Phase 1 API. No AI yet — show placeholder data in the AI panels.

### Phase 3 — Festival Demand Agent (4–5 days) — *the core learning milestone*
This is where agentic development begins. Build incrementally:
1. Add Spring AI + configure the Anthropic client (API key via env var, never hardcoded).
2. Define agent **tools** (function calling) the model can invoke:
   - `getSalesHistory(productId, branchId, fromDate, toDate)`
   - `getUpcomingFestivals(withinDays)`
   - `getCurrentStock(productId, branchId)`
3. Write the agent prompt: given a branch and a horizon, identify products likely to surge before upcoming festivals and recommend stock-up quantities with reasoning.
4. Persist every output to `AgentDecision` (input, output, reasoning).
5. Expose `GET /api/agents/festival-forecast?branchId=&horizonDays=`.
6. Build the Festival Forecast screen in Angular to render the output.

Success criteria: feed it the seeded data, get a sensible "stock up 40% on Rooh Afza before Eid at Mirpur" with a readable justification.

### Phase 4 — Smart Reorder Agent + human-in-the-loop (3–4 days)
- Agent watches stock crossing `reorder_threshold` and drafts a `PurchaseOrder` (status DRAFT → PENDING_APPROVAL).
- Reorder quantity informed by the Phase 3 forecast.
- Tools: `compareSupplierPrices(productId)`, `createDraftPurchaseOrder(...)`.
- Manager approval endpoint: `POST /api/purchase-orders/{id}/approve`. Only after approval does status move to APPROVED/SENT.
- PO approval screen in Angular (mirror the dashboard reorder panel, expanded).
- Run agent jobs asynchronously via the message broker, not in the request thread.

### Phase 5 — Insight Agent (Text-to-SQL) (3–4 days)
- Natural-language query endpoint: user types a question (Bangla or English), agent generates safe read-only SQL, executes it, returns a formatted result.
- **Safety: read-only DB user, allowlist of tables, statement timeout, reject any non-SELECT.** Never let the model's SQL hit a write-capable connection.
- "Ask AI" screen in Angular with a chat-style input and result table/chart.

### Phase 6 — BD-specific integrations (as needed)
- Hijri + Bengali calendar so festival dates compute correctly going forward.
- SMS alerts in Bangla (e.g. SSL Wireless) to branch managers.
- bKash/Nagad payment hooks (if you take the product to real sales).
- NBR VAT-compliant invoice generation (PDF).

---

## 4. Agentic patterns to learn (the whole point)

This project is a vehicle for these patterns — call them out as you build:
- **Tool/function calling** — the agent calling your Java methods (Phase 3).
- **Human-in-the-loop approval gates** — agent proposes, human disposes (Phase 4).
- **RAG** — embedding product knowledge in pgvector for grounded answers (Phase 4/5).
- **Text-to-SQL with guardrails** — NL over your own DB, safely (Phase 5).
- **Audit/observability** — logging every agent decision and its reasoning (all phases via `AgentDecision`).
- **Async agent execution** — long-running agent jobs off the request thread (Phase 4).

---

## 5. Security & safety guardrails (do not skip)

- Claude API key in environment variables only; never commit it.
- Insight Agent uses a dedicated read-only DB user; reject non-SELECT statements; enforce a query timeout and table allowlist.
- Every agent action writes to `AgentDecision` before it takes effect.
- Agents never execute irreversible actions (sending a PO, charging payment) without explicit human approval.
- Rate-limit the agent endpoints (Redis) to control API cost.
- Validate and sanitise all agent tool inputs as strictly as any external input.

---

## 6. Suggested repo structure

```
stocksense-bd/
├── docker-compose.yml
├── brief.md
├── backend/
│   ├── src/main/java/com/stocksense/
│   │   ├── config/        (security, tenant, spring-ai)
│   │   ├── domain/        (entities)
│   │   ├── repository/
│   │   ├── service/
│   │   ├── controller/
│   │   ├── agent/         (festival, reorder, insight agents + tools)
│   │   └── audit/         (AgentDecision logging)
│   └── src/main/resources/db/migration/  (Flyway)
└── frontend/
    └── src/app/
        ├── core/          (auth, interceptors, guards)
        ├── shared/        (components, models)
        └── features/      (dashboard, inventory, forecast, orders, ask-ai)
```

---

## 7. First Claude Code session — concrete starting prompt

> "Scaffold the StockSense BD project per brief.md. Start with Phase 0 and Phase 1: set up the Spring Boot 3 backend (Java 21, Maven) with the entities, repositories, services, and REST controllers listed in section 2, JWT auth with OWNER/MANAGER/STAFF roles, multi-tenant scoping by tenant_id, Flyway migrations, and a seed script that generates 1 tenant, 3 branches, ~100 products, and 12 months of synthetic sales with festival spikes. Add a docker-compose with MySQL, Postgres+pgvector, and Redis. Write integration tests for the auth flow and one CRUD resource."

Then proceed phase by phase. Don't start the agentic layer (Phase 3) until Phases 1–2 are solid — you want clean data and a working app before you add AI on top.
