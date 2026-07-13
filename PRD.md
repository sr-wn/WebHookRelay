# PRD — WebhookRelay

> Living document. New sessions: read this first. It records what is **Done**, what is
> **Pending**, and every **Assumption** made. Update the "Changelog" section after each
> implementation so future sessions know the current state.
>
> Project goal: a real-time webhook inspection platform built as a production-grade,
> cloud-native Spring Boot + React app. The headline engineering problem is a
> horizontally-scalable WebSocket fan-out, solved with a Redis pub/sub STOMP relay.

---

## 1. Status snapshot (last updated: 2026-07-13)

| Area | Status |
| --- | --- |
| Backend core (capture → store → broadcast) | ✅ Done |
| Redis STOMP fan-out relay | ✅ Done (+ architecture.md + 2-instance test) |
| Replay / diff | ✅ Done |
| Slug gen / JWT / rate limiting | ✅ Done |
| Data layer (MySQL + Flyway + HikariCP + JSON cols) | ✅ Done |
| Containerization (multi-stage + compose + HEALTHCHECK) | ✅ Done |
| Infrastructure (Render Blueprint + Vercel project config) | ✅ Done |
| CI/CD (backend → Render hook, frontend → Vercel CLI) | ✅ Done |
| Observability (Actuator + JSON logs + Prometheus + Grafana + alerts) | ✅ Done |
| Frontend (create → live feed → replay/diff) | ✅ Done |
| **Redis hot-endpoint cache** (explicit spec requirement) | ✅ Done (2026-07-13) |
| **Request-parsing unit test** (explicit spec requirement) | ✅ Done (2026-07-13) |
| Real deployment (Vercel + Render) wired end-to-end | ⏳ Pending (code ready; needs creds) |
| Revocable JWT (Redis denylist) | ✅ Done (2026-07-13) |
| Authenticated WebSocket feed (optional token) | ✅ Done (2026-07-13) |
| Authenticated WebSocket feed | ⏳ Deferred (documented) |

---

## 2. What is Done (detail)

### Repo structure (monorepo)
```
WebHookRelay/
  backend/      Spring Boot 3.3, Java 21, Maven
  frontend/     React 18 + Vite, @stomp/stompjs + SockJS
  docs/         architecture.md
  observability/ prometheus + grafana provisioning + alert rules
  .github/workflows/  backend.yml, frontend.yml
  docker-compose.yml, docker-compose.observability.yml, render.yaml
```

### Backend
- **Entities**: `Endpoint` (slug, ownerId, createdAt, expiresAt), `RelayRequest`
  (headers + queryParams as JSON columns, body, sourceIp, method, receivedAt, truncated flag).
- **Controllers**: `RelayController` (ANY `/relay/{slug}`, rate-limited, 200 fast),
  `EndpointController`, `ReplayController` (replay + diff), `TokenController` (JWT mint).
- **Services**: `CaptureService` (header/query/body extraction, body cap+truncate,
  latency timer, Redis publish), `EndpointService` (create w/ slug-collision retry,
  active lookup, `@Scheduled` TTL sweeper), `RateLimiter` (Redis fixed-window, per-slug
  + per-IP), `JwtService`, `ReplayService`, `SlugGenerator` (SecureRandom).
- **Fan-out**: `RedisEventRelay` (publish/onMessage) + `RedisConfig` listener container;
  `WebSocketConfig` (STOMP + SockJS, `/ws`, `/topic/endpoints/{slug}`).
- **Cross-cutting**: `RequestIdFilter` (MDC `requestId` correlation), `SecurityConfig`
  (CORS per-env, secure `/api/**`, permit `/relay/**` + `/ws`), `WebhookRelayProperties`
  (all config externalized).
- **Data**: Flyway migrations under `db/migration/{mysql,postgresql}` (DB-portable),
  `ddl-auto: validate` in all envs. HikariCP small pool (`DB_POOL_MAX`, default 10).
- **Profiles**: `dev`, `staging`, `prod`. Structured JSON logs via logback + logstash
  encoder. Actuator `/health` `/metrics` `/info` `/prometheus`.
- **Metrics**: `webhookrelay.capture.latency` (p50/p95/p99 histogram) + `webhookrelay.capture.total`.

### Frontend
- `App.jsx`: create endpoint → live STOMP feed → replay + diff of captured requests.
- Multi-stage Dockerfile (node build → nginx), `nginx.conf` (SPA fallback + `/healthz`,
  local compose only), `.dockerignore`, `vite.config.js` (proxy `/api`,`/ws` in dev),
  `api.js`. `vercel.json` (Vite preset + SPA rewrite) for the Vercel deploy target.

### Infra
- `docker-compose.yml`: backend, frontend, mysql, redis. `docker-compose.observability.yml`
  adds prometheus + grafana. Both Dockerfiles have `HEALTHCHECK`.
- `render.yaml`: blueprint for backend + Postgres + Redis KV (frontend is on Vercel).
- **Infrastructure**: provisioned using Render Blueprint (`render.yaml`) and Vercel project
  configuration (`frontend/vercel.json`). `render.yaml` wires `DATABASE_URL` + `REDIS_URL`
  automatically; `ConnectionUrlEnvironmentPostProcessor` converts them to Spring properties.

### CI/CD
- `backend.yml`: path-filtered → `mvn verify` (Testcontainers MySQL+Redis) → build+push
  to GHCR → staging deploy (auto) → prod deploy (manual `environment` approval gate).
- `frontend.yml`: path-filtered → `npm install` → `npm run lint` → `npm run build` →
  **Vercel CLI** deploy (preview for staging, `--prod` for production; same gating).

### Docs
- `docs/architecture.md`: the WebSocket fan-out problem + Redis solution + tradeoffs
  (interview-ready). Root `README.md`.

### Tests
- `SlugGeneratorTest`, `EndpointExpiryTest`, `JwtServiceTest` (unit, no Docker).
- `RedisFanoutTwoInstanceTest` (proves cross-instance fan-out via one Redis).
- `CaptureFlowIntegrationTest` (receive→store→broadcast via Testcontainers).
- NOTE: Testcontainers tests need a reachable Docker daemon. They pass in CI
  (`ubuntu-latest`) but fail locally on Windows unless `DOCKER_HOST` is set to the
  desktop-linux pipe.

---

## 3. What is Pending

### P1 — explicitly required by spec, not yet built
~~1. **Redis hot-endpoint cache.**~~ ✅ Implemented 2026-07-13: new `RedisEndpointCache`
   (cache-aside, short-TTL, negative caching) wired into `EndpointService.findActiveBySlug`
   and reused by `findActiveBySlugForOwner`; evicted on create. Config: `WEBHOOKRELAY_ENDPOINT_CACHE_TTL` (default 30s).
~~2. **Request-parsing unit test.**~~ ✅ Implemented 2026-07-13: `CaptureServiceTest`
   covers header/query/body extraction, X-Forwarded-For client-IP, remote-addr fallback,
   and 1 MiB body cap/truncation. Runs without Docker.

### P2 — deployment wiring (code ready, needs credentials)
~~3. **Real deploy steps.**~~ ✅ Wired 2026-07-13 (topology updated 2026-07-13): frontend
   deploys to **Vercel** via the Vercel CLI (`vercel pull`/`build`/`deploy --prebuilt`,
   `--prod` for production) in `frontend.yml`; backend `curl -X POST`s a **Render deploy-hook
   URL** in `backend.yml`. Both gated: staging auto on merge to main, prod behind the
   `production` environment's manual-approval gate. Secrets the user must add:
   `RENDER_BACKEND_DEPLOY_HOOK_STAGING`/`_PROD`, `VERCEL_TOKEN`, `VERCEL_ORG_ID`,
   `VERCEL_PROJECT_ID`. `VITE_API_BASE` is set as a Vercel project env var.
4. ~~**Infrastructure provisioning.**~~ ✅ Done: infrastructure is provisioned using Render
   Blueprint (`render.yaml`) and Vercel project configuration (`frontend/vercel.json`).

### P3 — documented deferred follow-ups (interview talking-points, not blockers)
5. ~~Revocable JWT (Redis denylist).~~ ✅ Implemented 2026-07-13: `TokenDenylist`
   (Redis-set of revoked `jti`s, TTL = token lifetime), enforced via `DenylistJwtValidator`
   wired into the `JwtDecoder`; `POST /api/auth/revoke` invalidates the caller's token.
   Tokens now carry a `jti` claim.
6. ~~Authenticated WebSocket feed.~~ ✅ Implemented 2026-07-13: optional bearer-token auth
   on STOMP CONNECT. `WsTokenHandshakeInterceptor` captures a `?token=` / `Authorization`
   token; `StompAuthChannelInterceptor` validates it via the same denylist-aware `JwtDecoder`
   and sets the session principal. No token → anonymous (slug capability model preserved).
   Test: `StompAuthChannelInterceptorTest`.
7. ~~Rate limiter is fixed-window (not token-bucket).~~ ✅ Implemented 2026-07-13: replaced
   with a **distributed token-bucket** (`RateLimiter`) backed by an atomic Redis Lua script
   (read-refill-deduct-write), enforced globally across instances. Independent per-slug and
   per-IP buckets; fails open on Redis error. Config: `WEBHOOKRELAY_RL_SLUG_BURST`/`_RATE`,
   `WEBHOOKRELAY_RL_IP_BURST`/`_RATE`. Test: `RateLimiterTest`.
8. Grafana dashboard screenshot in README (can't be auto-provisioned by AI tooling).

---

## 4. Assumptions (correct me)

1. **Deploy target**: free tier → frontend on **Vercel**, backend + Postgres + Redis on
   **Render** (deploy from CI; provisioned via Render Blueprint + Vercel project config).
   Render's managed DB is Postgres (not MySQL) →
   backend ships both MySQL & Postgres Flyway migrations and auto-selects by JDBC URL.
2. **STOMP relay**: Redis pub/sub bridge (not RabbitMQ). Tradeoff documented in
   `docs/architecture.md` (fire-and-forget acceptable because captures are also
   persisted and re-fetchable on reconnect).
3. **Defaults** (all env-configurable): TTL 24h, body cap 1 MiB, token-bucket rate limit
   per slug (burst 120, refill 2/s) and per IP (burst 300, refill 5/s).
4. **Auth model**: stateless JWT bearer for endpoint ownership; public capture and the WS
   feed stay anonymous, protected by the unguessable slug (capability model). Replay/diff
   are owner-scoped.
5. **Frontend** is intentionally minimal but covers the full create → live → replay/diff
   flow.

---

## 5. Changelog (newest first)

- **2026-07-13** — **Deployment hardening.** Removed all remaining IaC references (infra is
  now provisioned via Render Blueprint + Vercel project config). Standardized the JWT env
  var on `WEBHOOKRELAY_JWT_SECRET` (fixed `render.yaml`, which previously set an unused
  `JWT_SIGNING_KEY`). Added `ConnectionUrlEnvironmentPostProcessor` (+ 5 unit tests) to map
  Render's `DATABASE_URL`→JDBC/user/pass and `REDIS_URL`→`spring.data.redis.url`; wired both
  into `render.yaml`. Documented explicit CORS origin (never `*`). Added Grafana panels
  (CPU, 4xx/5xx, DB connections, active WebSocket sessions, Redis ops) backed by new metrics:
  `WebSocketSessionMetrics` gauge + Lettuce command metrics via a `ClientResources` bean.
  Restructured README (Architecture diagram, Features, Deployment, CI/CD, Monitoring,
  Screenshot, API Documentation, Future Improvements). All 28 Docker-free unit tests pass.
- **2026-07-13** — **Moved frontend deploy to Vercel** (topology: React→Vercel,
  Spring Boot→Render, Render Postgres + Redis). Rewrote `frontend.yml` to deploy via the
  Vercel CLI (preview=staging, `--prod`=production) instead of a Render deploy hook / GHCR
  image; dropped the frontend Docker build-and-push job. Added `frontend/vercel.json`
  (Vite preset + SPA rewrite). Removed the frontend static service from `render.yaml`.
  New secrets: `VERCEL_TOKEN`, `VERCEL_ORG_ID`, `VERCEL_PROJECT_ID`. Updated README + PRD.
- **2026-07-13** — Removed the hand-managed IaC directory: deleted it and cleaned all
  references from `README.md`, `PRD.md`, and `.gitignore`. Deployment is handled via Render
  (backend) + Vercel (frontend) from CI.
- **2026-07-13** — Implemented **distributed token-bucket rate limiting** (PRD P3 #7):
  rewrote `RateLimiter` to use an atomic Redis Lua script (read-refill-deduct-write) with
  independent per-slug and per-IP buckets, enforced globally across stateless instances;
  fails open on Redis error. `WebhookRelayProperties.RateLimit` now `(perSlugBurst,
  perSlugPerSecond, perIpBurst, perIpPerSecond)`; new env vars + `application.yml` defaults.
  Test: `RateLimiterTest`. All 23 Docker-free unit tests pass offline.
- **2026-07-13** — Audited full repo. Confirmed backend compiles, Dockerfiles have
  HEALTHCHECK, frontend CI runs lint, nginx serves `/healthz`. Identified two genuine
  spec gaps (Redis endpoint cache, request-parsing unit test) and wrote this PRD.
- **2026-07-13** — Closed both P1 gaps: added `RedisEndpointCache` (cache-aside, TTL 30s,
  negative caching) into `EndpointService`; added `CaptureServiceTest` (header/query/body
  parsing + 1 MiB truncation, no Docker). Fixed `JwtServiceTest` for the new
  `WebhookRelayProperties` record shape. All 3 new unit tests pass offline.
- **2026-07-13** — Fixed a **real production bug** in JWT issuance: `JwtService.issueToken`
  used `JwtEncoderParameters.from(claims)`, whose default JWS alg is RS256, so the encoder
  could never match the HMAC (OCT) signing key → `/api/auth/token` would have returned HTTP
  500 in prod. Now pins `MacAlgorithm.HS256` in the JWS header and sets `algorithm(HS256)`
  on the key in `SecurityConfig`. Also pinned `nimbus-jose-jwt` to 9.37.3 (a transitive
  oauth2-oidc-sdk pulled 9.22, whose `OctetSequenceKey.Builder` lacks the method). All 10
  Docker-free unit tests pass offline.
- **2026-07-13** — Implemented **revocable JWTs** (PRD P3 #5): `TokenDenylist` (Redis-set of
  revoked `jti`s, TTL = token remaining lifetime, shared so revocation holds across all
  instances), `DenylistJwtValidator` composed into the `JwtDecoder` in `SecurityConfig`,
  `jti` claim added to issued tokens, and `POST /api/auth/revoke`. Tests:
  `TokenDenylistTest`, `DenylistJwtValidatorTest`. All 15 Docker-free unit tests pass.
- **2026-07-13** — Implemented **optional authenticated WebSocket feed** (PRD P3 #6):
  `WsTokenHandshakeInterceptor` + `StompAuthChannelInterceptor` validate a bearer token on
  STOMP CONNECT via the shared denylist-aware `JwtDecoder` and set the session principal;
  anonymous (slug-capability) connections still allowed. Test: `StompAuthChannelInterceptorTest`.
  All 19 Docker-free unit tests pass.
- **2026-07-13** — Wired **real deploy steps** (PRD P2 #3) in both GitHub workflows: staging
  auto-deploys on merge to main, prod is gated behind manual approval, and each `curl`s a
  Render deploy-hook URL sourced from repo/environment secrets. Documented the required
  secrets and the AWS ECS alternative in the PRD. YAML validated.
