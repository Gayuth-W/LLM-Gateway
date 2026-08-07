# LLM Gateway

A production-shaped **API gateway for LLM traffic**, built on **Java 21 + Spring WebFlux** (fully reactive). It sits between your applications and your model backend and adds the things you need before LLMs are safe to expose to many teams: **authentication, rate limiting, USD budget caps, automatic fallback routing, circuit breaking, and full observability** (metrics, traces, dashboards, alerts).

The backend is **[Ollama](https://ollama.com)** running locally, so the whole thing runs on your machine with **zero external API keys and zero per-token cost**. "Multiple providers" is modelled as **multiple Ollama models**: the gateway treats each model as a routing target and fails over `llama3.1 → gemma3 → gemma3:1b` when one is unhealthy. Swapping in a hosted provider later is just one more class behind the `LLMProvider` interface.

> **Why this exists:** raw model endpoints have no notion of *who* is calling, *how much* they're spending, or *what to do when the model falls over*. This gateway is the policy and resilience layer that makes a shared LLM backend operable.

---

## Table of contents

1. [Architecture at a glance](#architecture-at-a-glance)
2. [The request lifecycle](#the-request-lifecycle)
3. [Feature tour](#feature-tour)
4. [Running it](#running-it)
5. [Operator auth & RBAC](#operator-auth--rbac)
6. [API reference](#api-reference)
7. [Configuration](#configuration)
8. [Observability](#observability)
9. [Testing & load](#testing--load)
10. [Data structures & algorithms used](#data-structures--algorithms-used)
11. [Project layout](#project-layout)

---

## Architecture at a glance

```
                 ┌──────────────────────────────────────────────────────────────┐
   client  ──▶   │  TeamAuthFilter  ──▶  ProxyController                         │
  (Bearer key)   │   (WebFilter)          │                                       │
                 │                         ▼                                       │
                 │   budget guard ─ content filter ─ model check ─ rate limit     │
                 │                         │                                       │
                 │                         ▼                                       │
                 │                  EnrichmentService                             │
                 │                         │                                       │
                 │                         ▼                                       │
                 │                   FallbackRouter ──▶ Resilience4j (CB + retry)  │
                 │                         │                  │                    │
                 │                         ▼                  ▼                    │
                 │                  ProviderRegistry ──▶ OllamaProvider ──▶ Ollama │
                 │                         │                                       │
                 │   budget rollup ◀───────┴──────▶ metrics / traces / health     │
                 └──────────────────────────────────────────────────────────────┘
        Redis: token buckets, budget counters, rolling health windows
     Postgres: teams, spend rollups, health/breaker/fallback/audit events
   Prometheus + Grafana + Jaeger: metrics, dashboards, distributed traces
```

**Tech:** Spring Boot 3.3, WebFlux, R2DBC (reactive Postgres) + Flyway (migrations), Lettuce/Redis, Bucket4j (distributed token buckets), Resilience4j (circuit breakers + retry), Micrometer + OpenTelemetry (metrics + tracing), Testcontainers + WireMock + Gatling (tests + load).

Everything on the request path is **non-blocking**. Postgres is accessed via R2DBC; Redis via reactive Lettuce; the upstream call via reactive `WebClient`. Java 21 **virtual threads** are enabled for any blocking work that escapes the reactive chain.

---

## The request lifecycle

A call to `POST /v1/chat/completions` flows through these stages:

1. **Authentication** (`TeamAuthFilter`, a `WebFilter`) — resolves the `Authorization: Bearer <key>` to a **Team** (cached), and fast-rejects teams already flagged budget-exhausted. Team + request priority are stashed on the exchange.
2. **Live budget guard** — checks the team's **today's spend** (a Redis counter) against its daily USD cap; over the cap → `402`.
3. **Content screen** — a pluggable policy stage (empty-prompt / denylist today; a moderation hook in production).
4. **Model authorization** — the team must be allowed to use the requested model, and the model must be known → otherwise `403`.
5. **Rate limiting** (token-aware) — a **Bucket4j** token bucket over Redis. `requests/min` is enforced by a bucket chosen by **priority** (HIGH uses the main bucket; LOW uses a smaller reserved bucket so batch jobs can't starve interactive traffic). `tokens/min` is debited as an **estimate** now and **reconciled** to the real count after the call. Over limit → `429` with `Retry-After`.
6. **Enrichment** — the team's profile can inject a system prefix (forwarded) and append a disclaimer (returned).
7. **Fallback-routed execution** (`FallbackRouter`) — picks the ordered list of candidate models (the requested model first, then its fallback chain), **skips models the health service marks DOWN**, and attempts each one wrapped in its **circuit breaker** and **retry** policy. On failure it moves to the next candidate.
8. **Accounting** — computes USD cost, updates the live Redis budget counter (firing one-shot warn/exhausted alerts at thresholds), queues a durable spend delta, **reconciles** the token bucket, and records request/latency/token/cost metrics. Health signals are fed from the real call outcome too.

Streaming works the same way but emits **Server-Sent Events**; token accounting happens when the terminal chunk arrives, and the disclaimer is appended as a final delta before `[DONE]`.

---

## Feature tour

### Unified, OpenAI-shaped API
One endpoint, `POST /v1/chat/completions`, accepting the familiar `{ model, messages, stream, max_tokens, temperature }` body and returning an OpenAI-style `chat.completion` (or `chat.completion.chunk` SSE stream). Your existing OpenAI client code mostly just works — point it at the gateway and use a team key.

### Per-team rate limiting with priorities
Distributed token buckets (Bucket4j + Redis) enforce **requests/min** and **tokens/min** per team. Requests carry an optional `X-Priority: high|low` header; LOW-priority traffic draws from a smaller **reserved** bucket, guaranteeing headroom for interactive requests.

### USD budget caps
Each team has a **daily** (and monthly) USD ceiling. Ollama is free, so the gateway applies a **synthetic per-1K-token price table** (configurable) purely so budgets are demonstrable. Spend is enforced live from a Redis counter and rolled up durably into Postgres for reporting. Crossing 80% warns; crossing 100% blocks further requests and alerts.

### Fallback routing + circuit breaking
Models are organised into **fallback chains**. A health checker continuously probes each model and classifies it `HEALTHY / DEGRADED / DOWN` from a rolling error-rate window. The router skips DOWN models and fails over along the chain; each model has an independent **Resilience4j circuit breaker** (sheds load when a model is failing) and **retry with exponential backoff** (for transient errors). Breaker state changes are persisted and alerted.

### Admin API
CRUD for teams, live spend, per-model health, and a spending report — all under `/admin/**`.

### Observability
Prometheus metrics (RPS, error rate, latency p50/p95/p99, tokens, cost, fallback rate, breaker state), distributed traces exported to Jaeger (the incoming request and the upstream Ollama call are linked), pre-built Grafana dashboards, and Prometheus alert rules.

---

## Running it

You need **Docker** and **[Ollama](https://ollama.com) installed on your host**. Ollama runs on the host (not in Compose) so it can use your GPU/CPU directly; the container reaches it via `host.docker.internal`.

### 1. Pull the models (on the host)

```powershell
ollama serve            # if not already running as a service
ollama pull llama3.1
ollama pull gemma3
ollama pull gemma3:1b
```

> Short on disk/RAM? Pull just `gemma3:1b` and set it as the requested model — fallback still works with one model, it just has nothing to fall back *to*.

### 2. Bring up the stack

```powershell
docker compose up --build
```

This starts Postgres, Redis, Jaeger, Prometheus, Grafana, and the gateway. Flyway creates the schema and seeds three demo teams on first boot.

| Service     | URL                              |
|-------------|----------------------------------|
| Gateway     | http://localhost:8080            |
| Grafana     | http://localhost:3000 (admin/admin) |
| Prometheus  | http://localhost:9090            |
| Jaeger      | http://localhost:16686           |

### 3. Try it

PowerShell:

```powershell
curl.exe http://localhost:8080/v1/chat/completions `
  -H "Authorization: Bearer sk-enterprise-key" `
  -H "Content-Type: application/json" `
  -d '{\"model\":\"llama3.1\",\"messages\":[{\"role\":\"user\",\"content\":\"Say hi.\"}],\"stream\":false}'
```

Or run the full guided demo (Git Bash / WSL / macOS / Linux):

```bash
./scripts/seed.sh
```

It walks through a completion, a stream, a low-priority call, a rate-limit burst on the free tier, and the three admin endpoints.

### Running the app without Docker (dev loop)

You still need Postgres and Redis somewhere. With the two running locally on default ports:

```powershell
mvn spring-boot:run
```

(Defaults in `application.yml` point at `localhost` for Postgres/Redis and `localhost:11434` for Ollama.)

### Seeded demo teams

| API key             | Team            | Limits                       | Notes                                   |
|---------------------|-----------------|------------------------------|-----------------------------------------|
| `sk-enterprise-key` | ACME Enterprise | 120 rpm / 120k tpm / $25/day | All models; compliance enrichment       |
| `sk-internal-key`   | Internal Tools  | 60 rpm / 60k tpm / $5/day    | `llama3.1`, `gemma3:1b`                 |
| `sk-free-key`       | Free Tier       | **5 rpm** / 2k tpm / **$0.01/day** | `gemma3:1b` only — easy to trip 429/402 |

### Seeded operator accounts

Console sign-in, seeded by `V4__admin_users.sql`. Like the team keys above, these are
intentionally well-known for local use — rotate them before the stack is reachable from
anywhere but localhost.

| Username   | Password      | Role       |
|------------|---------------|------------|
| `admin`    | `admin123`    | `ADMIN`    |
| `operator` | `operator123` | `OPERATOR` |
| `viewer`   | `viewer123`   | `VIEWER`   |

---

## Operator auth & RBAC

### Two credentials, deliberately not merged

| Surface       | Credential            | Why                                                              |
|---------------|-----------------------|------------------------------------------------------------------|
| `/v1/**`      | Team API key (`sk-…`) | Machine clients. A completions call should not need a login/refresh loop. |
| `/admin/**`   | Operator JWT (1h)     | Humans. Short-lived, carries a role, and names the actor in the audit trail. |

Adding JWT to `/v1/**` was considered and rejected: key rotation and token expiry solve
different problems, and every LLM API consumers already expect a long-lived key there.
`TeamAuthFilter` is untouched by this change.

### The role model

Three roles, derived from the blast radius of the endpoints that actually exist:

| Role       | Reaches                                                | Can cause                          |
|------------|--------------------------------------------------------|------------------------------------|
| `VIEWER`   | all `GET` — teams, provider health, spend reports       | nothing                            |
| `OPERATOR` | the above + all `PATCH` — limits, budgets, alert thresholds | a cost spike or an outage       |
| `ADMIN`    | the above + `POST /admin/teams`                        | issuing a credential               |

The line that matters is the last one. Tuning a number and minting an API key are
different privileges, so team creation is the only `ADMIN`-only handler. A separate
`FINANCE` role splitting budgets from limits was considered and dropped — both are
cost/availability controls with the same blast radius, so the split would be org-chart
theatre rather than a security boundary.

The hierarchy (`ADMIN > OPERATOR > VIEWER`) is expanded into `ROLE_*` authorities in
`SecurityUtils.authorities()` rather than configured through Spring Security's
`RoleHierarchy`. Keeping it in `Role.implied()` makes it unit-testable (`RoleTest`) and
visible where roles are defined, instead of depending on expression-handler wiring
taking effect.

### What this fixed

Before this change every `/admin/**` route was open — anyone could `POST /admin/teams`
and mint themselves a key with a $500 budget. Worse, the audit actor came from an
`X-Admin-User` header that the caller controlled, so `audit_logs` could be made to name
someone who had done nothing. The actor is now read from the verified JWT subject; the
header is ignored (`RbacIntegrationTest.spoofedAdminUserHeaderIsIgnored`).

### Endpoints

| Method & path        | Auth        | Purpose                                        |
|----------------------|-------------|------------------------------------------------|
| `POST /auth/login`   | none        | `{username, password}` → token + role          |
| `GET /auth/me`       | any role    | Identity behind the presented token            |

```bash
TOKEN=$(curl -s localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"operator","password":"operator123"}' | jq -r .token)

curl -s localhost:8080/admin/teams -H "Authorization: Bearer $TOKEN"
```

### Design calls, and what was left out

- **HS256, not RS256.** This service is the only issuer and the only verifier, so an
  asymmetric key pair plus JWKS would be scaffolding around a single HMAC key. The
  secret must be ≥32 bytes or the application refuses to start.
- **No refresh token, no revocation list.** A token is valid until it expires. That
  makes verification stateless (no DB read per request) at the cost of not being able
  to kill a session early — the TTL is the bound. Rotation plus a Redis denylist is the
  upgrade path if it's ever needed.
- **BCrypt runs on `boundedElastic`.** Verification is CPU-bound at ~50–100 ms; inline
  on a Netty event loop it would stall every other request sharing that thread,
  including proxied completions.
- **Login is not rate limited.** Known gap, stated rather than hidden: the endpoint is
  open to online password guessing. Bucket4j is already in the stack and a per-username
  and per-IP bucket in front of the handler is the honest fix.
- **The console keeps its token in `localStorage`,** which is readable by injected
  script. An httpOnly cookie plus CSRF handling is stronger; it was skipped to keep the
  gateway a stateless bearer API. The short TTL is the mitigation actually in place.
- **Unknown user and wrong password are indistinguishable,** including timing — a dummy
  BCrypt comparison runs when the username doesn't exist, so the endpoint can't be used
  to enumerate accounts.

---

## API reference

### `POST /v1/chat/completions`
Headers: `Authorization: Bearer <team-key>` (required), `X-Priority: high|low` (optional, default `high`).

Request:
```json
{
  "model": "llama3.1",
  "messages": [{ "role": "user", "content": "Hello" }],
  "stream": false,
  "max_tokens": 256,
  "temperature": 0.7
}
```

Non-streaming response (OpenAI-shaped) with extra headers `X-Provider-Used`, `X-Gateway-Fallback`, `X-Request-Tokens`:
```json
{
  "id": "chatcmpl-…",
  "object": "chat.completion",
  "model": "llama3.1",
  "choices": [{ "index": 0, "message": { "role": "assistant", "content": "…" }, "finish_reason": "stop" }],
  "usage": { "prompt_tokens": 12, "completion_tokens": 34, "total_tokens": 46 }
}
```

Streaming (`"stream": true`) returns `text/event-stream` with `chat.completion.chunk` events terminated by `data: [DONE]`.

**Error envelope** (uniform across all failures):
```json
{ "status": 429, "error": "rate_limited", "message": "Rate limit exceeded", "timestamp": "…" }
```

| Status | `error` code         | Meaning                                  |
|-------:|----------------------|------------------------------------------|
| 401    | `unauthorized`       | Missing/invalid API key                  |
| 402    | `budget_exhausted`   | Daily USD budget reached                 |
| 403    | `model_not_allowed`  | Team not permitted / unknown model       |
| 422    | `content_policy`     | Prompt rejected by content filter        |
| 429    | `rate_limited`       | RPM or TPM exceeded (`Retry-After` set)  |
| 503    | `provider_unavailable` | All candidate models failed/unhealthy  |

### Admin

All admin routes require `Authorization: Bearer <operator-jwt>`. Missing or expired token
→ `401 unauthorized`; valid token with an insufficient role → `403 forbidden`, both in the
standard error envelope.

| Method & path                              | Min role   | Purpose                                  |
|--------------------------------------------|------------|------------------------------------------|
| `GET /admin/teams`                         | `VIEWER`   | List teams with live spend               |
| `GET /admin/teams/{id}`                    | `VIEWER`   | One team                                 |
| `POST /admin/teams`                        | `ADMIN`    | Create a team (issues an API key)        |
| `PATCH /admin/teams/{id}/limits`           | `OPERATOR` | Update RPM/TPM/low-priority RPM          |
| `PATCH /admin/teams/{id}/budget`           | `OPERATOR` | Update daily/monthly budget              |
| `PATCH /admin/teams/{id}/alert-threshold`  | `OPERATOR` | Update alert % + Slack channel           |
| `GET /admin/providers/health`              | `VIEWER`   | Per-model status, error rate, p99        |
| `GET /admin/spending?from=&to=`            | `VIEWER`   | Spend rollup by team/model               |

> `/admin/**` requires a token at the path level *and* carries a `@PreAuthorize` per
> handler. The path rule is the safety net: an admin endpoint added later without an
> annotation still cannot be called anonymously.

---

## Configuration

All policy lives under the `gateway:` prefix in `application.yml` and is bound to typed records (`GatewayProperties`). Highlights you'll actually tune:

- `gateway.models` — the known Ollama models and their tier label.
- `gateway.fallback-chains` — per-model ordered fallback lists (the routing graph).
- `gateway.pricing` — synthetic per-1K-token USD prices used for budgets/cost.
- `gateway.enrichment.profiles` — system-prefix / disclaimer-suffix per profile.
- `gateway.resilience.*` — health thresholds, retry backoff, circuit-breaker windows.
- `gateway.budget.*` — flush interval, warn threshold.
- `gateway.alerts.slack.webhook-url` — leave blank to log alerts instead of posting.

The `docker` profile (`application-docker.yml`) only overrides hostnames.

---

## Observability

- **Metrics:** scraped at `/actuator/prometheus`. Custom series are prefixed `gateway_*` (`gateway_requests_total`, `gateway_request_latency_seconds`, `gateway_tokens_input_total`, `gateway_cost_usd_total`, `gateway_fallback_total`) plus Resilience4j's breaker/retry metrics.
- **Dashboards:** three are auto-provisioned into Grafana — *Operations* (RPS, error ratio, latency percentiles), *Business* (tokens, cost, model mix), and *Resilience & Performance* (breaker state, fallback rate, retries).
- **Tracing:** Micrometer Tracing with the OpenTelemetry bridge, exported to Jaeger via OTLP. The inbound request span and the outbound Ollama `WebClient` span are correlated, and gateway-specific attributes (team, served model, fallback, tokens) are tagged.
- **Alerts:** `docker/prometheus/alert.rules.yml` ships rules for error rate > 5%, p99 > 500ms, any breaker OPEN, and frequent budget exhaustion.

---

## Testing & load

**Integration tests** (`src/test/java/.../integration`) run the full stack against **real** Postgres and Redis via **Testcontainers**, with **WireMock** standing in for Ollama (so failures, fallbacks, and streams are deterministic). They cover successful completion + enrichment, model authorization, automatic fallback, streaming, and rate limiting (including `Retry-After`).

```powershell
mvn test          # requires Docker (Testcontainers)
```

**Load test** (Gatling, isolated in the `load-test` profile) against a running gateway:

```powershell
mvn -Pload-test gatling:test -Dgatling.simulationClass=com.llmgateway.gatling.GatewaySimulation
```

---

## Data structures & algorithms used

This was a design goal, so here's the explicit list (several are implemented by hand in this repo, not just pulled from a library):

| # | Structure / algorithm | Where | Why |
|---|-----------------------|-------|-----|
| 1 | **Token-bucket rate limiting** | `RateLimiterService` (Bucket4j over Redis) | Smooth per-minute RPM/TPM limits with burst capacity. |
| 2 | **Tiered / priority buckets** | `RateLimiterService` | Separate reserved bucket for LOW priority so batch traffic can't starve interactive traffic. |
| 3 | **Estimate-then-reconcile counter** | `RateLimiterService.reconcileTokenUsage` | TPM debited as an estimate at admission, corrected to the real token count after the call. |
| 4 | **Redis ZSET sliding window** *(hand-rolled)* | `RollingWindow` | Time-decaying error-rate window for health, correct across instances; `ZREMRANGEBYSCORE` + `ZCARD`. |
| 5 | **Fixed-capacity ring buffer** *(hand-rolled)* | `LatencyRingBuffer` | O(1) latency sampling with bounded memory; p50/p95/p99 via sort + nearest-rank. |
| 6 | **Nearest-rank percentile** *(hand-rolled)* | `LatencyRingBuffer.percentile` | Compute p99 etc. from the sample window. |
| 7 | **BFS over a fallback graph + visited set** *(hand-rolled)* | `FallbackRouter.healthyCandidates` | Build the ordered, de-duplicated candidate list and make cyclic chains (A→B→A) safe. |
| 8 | **Circuit-breaker finite-state machine** | `Resilience4jConfig` / `FallbackRouter` (Resilience4j) | CLOSED→OPEN→HALF_OPEN per model to shed load from a failing model. |
| 9 | **Exponential backoff** | `Resilience4jConfig` (retry interval function) | Space out retries of transient upstream failures. |
| 10 | **Caffeine (Window-TinyLFU) cache** | `TeamService` | Cache hot team-config lookups off the request path with a short TTL. |
| 11 | **Concurrent hash map (O(1) registry)** | `ProviderRegistry`, `ProviderHealthService` | Lock-free model→provider and model→status lookups on the hot path. |
| 12 | **Atomic Redis counters (`INCRBYFLOAT` / `SETNX`)** | `BudgetService` | Live spend accumulation and exactly-once threshold alerts. |
| 13 | **Lock-free MPSC-style flush queue** *(hand-rolled buffering)* | `BudgetService` (`ConcurrentLinkedQueue`) | Buffer per-request spend deltas off the hot path; a scheduled job aggregates and UPSERTs them. |
| 14 | **Hash-bucketed aggregation** | `BudgetService.flush` | Collapse many deltas into one DB statement per (team, day, model) bucket before the UPSERT. |
| 15 | **Atomic UPSERT (add-on-conflict)** | `SpendingRecordRepository.upsertSpend` | Concurrent flushes never lose writes (`ON CONFLICT … DO UPDATE … +EXCLUDED`). |
| 16 | **Single-pass streaming accumulation** | `ProxyController` streaming path | Tally tokens from the terminal NDJSON chunk in one pass while forwarding deltas. |

---

## Project layout

```
src/main/java/com/llmgateway/
├── config/         Typed properties + WebClient/Redis/Resilience4j/observability beans
├── controller/     ProxyController (public) + Admin* controllers
├── dto/            Unified request/response + ollama/* + admin/* shapes
├── exception/      Gateway exception taxonomy + global error handler
├── filter/         TeamAuthFilter + request-context keys
├── model/          R2DBC entities (Team, spend, health/breaker/fallback/audit events)
├── observability/  Micrometer metrics + tracing helper + span attributes
├── provider/       LLMProvider interface, OllamaProvider, ProviderRegistry
├── ratelimit/      Bucket4j token-bucket service + key scheme
├── budget/         Cost calculator + budget service (Redis + flush queue)
├── resilience/     RollingWindow, LatencyRingBuffer, ProviderHealthService, FallbackRouter, CB events
├── repository/     Reactive (R2DBC) repositories
├── security/       JWT mint/verify, auth filter, RBAC filter chain, 401/403 handlers
└── service/        Team/Enrichment/Alert/Audit/ContentFilter services
src/main/resources/  application*.yml + Flyway migrations (schema + demo seed)
src/test/            Testcontainers integration tests + WireMock helper + Gatling sim
docker/              Prometheus config + alert rules + Grafana provisioning & dashboards
```

---

### A note on the build

This repository is written to compile and run as a unit, but the most environment- and version-sensitive spots are: the **Bucket4j 8.7.0 Lettuce async proxy-manager** wiring (`RedisConfig`, `RateLimiterService`), **Ollama NDJSON streaming decode** (`OllamaProvider`), and the **OTLP→Jaeger** exporter. If you bump library versions, re-check those three first.

One more since the auth work: adding `spring-boot-starter-security` closes every route by
default. `SecurityConfig` re-opens `/`, `/auth/login`, `/v1/**` and the actuator scrape
endpoints explicitly, and leaves `anyExchange()` permitted so unmapped paths still 404
rather than 401. Locking down `/actuator/prometheus` is a silent failure — the app runs,
Prometheus just stops scraping and every Grafana panel flatlines — so
`RootAndErrorIntegrationTest` asserts it stays public.
