# WebhookRelay — Architecture

## The problem this project exists to solve well

Real-time dashboards over WebSockets are trivial on a single instance and quietly
broken the moment you scale horizontally. WebhookRelay is built specifically to get
this right, because it is a genuine distributed-systems problem — not CRUD busywork.

---

## The WebSocket fan-out problem

### What happens on one instance (works fine)

Spring's STOMP support ships with a **simple in-memory broker**. When a captured
webhook is published to a destination like `/topic/endpoints/{slug}`, the broker
looks up every WebSocket session subscribed to that destination **in this JVM's
memory** and writes the message to each one.

```
Client ──subscribe /topic/endpoints/abc──▶ Instance A (in-memory broker)
Webhook ─────────────────────────────────▶ Instance A ──▶ broadcasts locally ✅
```

### What breaks when you run 2+ instances behind a load balancer

Subscriptions live in the memory of **whichever instance the client connected to**.
A webhook can be received by **any** instance (the LB spreads HTTP traffic). If the
client is subscribed on Instance A but the webhook lands on Instance B, Instance B's
in-memory broker has **no knowledge** of that subscription. The message is delivered
to nobody.

```
Client ──subscribe──▶ Instance A   (subscription lives in A's memory)
Webhook ────────────▶ Instance B   (B's broker: "nobody is subscribed here")
                                     message dropped ❌
```

This is the trap: it passes every local test and every single-container demo, then
silently loses messages in production the day you scale past one replica. It also
violates the twelve-factor "stateless process" rule — subscriber state that can't
survive a restart or move between instances.

---

## The solution: a Redis pub/sub bus in front of the local broker

We keep the in-memory broker (it's fast and it's fine for local delivery) but we put
a **Redis pub/sub channel between "a webhook was captured" and "broadcast to
clients."** Every app instance subscribes to the same Redis channel at startup.

### Flow

```
                          ┌─────────────── Redis pub/sub channel: "webhookrelay.events"
                          │                         ▲
                          ▼                         │ publish(JSON)
   ┌──────────────┐  onMessage  ┌──────────────┐   │   ┌──────────────┐
   │  Instance A  │◀────────────│    REDIS     │───┼──▶│  Instance B  │
   │ local broker │             └──────────────┘   │   │ local broker │
   └──────┬───────┘                                 │   └──────┬───────┘
          │ convertAndSend to LOCAL sessions        │          │ (no local subs)
          ▼                                         │          ▼
     Client (subscribed on A) ✅                    │      (nothing to do)
                                                    │
   Webhook POST /relay/abc  ───────────────────────┘
   (lands on Instance B → B persists → B publishes to Redis)
```

Concretely:

1. A webhook hits **any** instance → `CaptureService` persists it to MySQL.
2. That instance calls `RedisEventRelay.publish()`, pushing the serialized event
   onto the Redis channel `webhookrelay.events`.
3. Redis delivers that message to **every** subscribed instance.
4. On each instance, `RedisEventRelay.onMessage()` fires and calls
   `SimpMessagingTemplate.convertAndSend("/topic/endpoints/{slug}", payload)`.
5. Each instance's **local** broker now delivers to whichever clients happen to be
   subscribed **on that instance**. Instances with no relevant subscribers simply
   do nothing.

Net effect: **any instance can broadcast to any connected client, regardless of
which instance that client is subscribed to.** The instances are now stateless with
respect to fan-out — Redis is the shared bus.

### Where this lives in the code

| Concern | File |
| --- | --- |
| Publish captured event to Redis | `relay/RedisEventRelay#publish` |
| Subscribe + re-broadcast locally | `relay/RedisEventRelay#onMessage` |
| Wire Redis listener container | `config/RedisConfig` |
| Local STOMP broker + endpoint | `config/WebSocketConfig` |

### The proof it works

`RedisFanoutTwoInstanceTest` starts **two independent listener containers + relays**
(each simulating a separate instance) against one Testcontainers Redis, publishes
from "instance A", and asserts **both** instances' `SimpMessagingTemplate`s receive
the message. That is the horizontal-scalability guarantee, verified in CI.

---

## Design tradeoffs (be ready to defend these)

- **Why Redis pub/sub and not the RabbitMQ STOMP relay?** We already run Redis for
  caching and rate-limiting, so pub/sub adds zero new infrastructure. The tradeoff:
  Redis pub/sub is **fire-and-forget** — a message published while an instance is
  briefly disconnected is lost for that instance. For an ephemeral inspection tool
  (captured requests are also persisted in MySQL and re-fetchable on reconnect) this
  is an acceptable tradeoff. If we needed guaranteed delivery / durable subscriptions,
  the right move is Spring's native `StompBrokerRelay` backed by RabbitMQ (or Redis
  Streams with consumer groups).

- **Why keep the in-memory broker at all?** Local delivery is fast and the Redis
  bridge is a thin layer on top. We are not trying to make Redis the STOMP broker;
  we're using it purely as a cross-instance event bus.

- **Full STOMP relay alternative.** `enableStompBrokerRelay()` pointed at RabbitMQ
  moves *all* broker state out of the app and is the textbook "correct" answer for
  large scale. We chose the lighter Redis bridge deliberately for a project this size.

---

## Data & scaling notes

- **MySQL** stores `Endpoint` and `RelayRequest`. Headers/query params are `JSON`
  columns because payload shape is arbitrary — no rigid schema migrations per payload.
- **HikariCP** pool is intentionally small (default max 10). The workload is
  IO-bound and we scale out horizontally; oversized pools per replica just saturate
  the DB's connection limit. Tune via `DB_POOL_MAX`.
- **Flyway** owns the schema (`ddl-auto: validate`). No `ddl-auto: update` in any
  environment.
- **Redis** does triple duty: WebSocket fan-out bus, hot-endpoint lookup cache, and
  the distributed rate-limiter's counters (so limits hold across all instances).
- **TTL expiry** is a `@Scheduled` sweeper (`EndpointService#sweepExpired`); the
  delete is idempotent so running it on every instance is safe.

## Observability

- Actuator exposes `/actuator/health`, `/metrics`, `/info`, `/prometheus`.
- Structured JSON logs to stdout (logback + logstash encoder) in staging/prod;
  a `requestId` MDC value correlates a single webhook's lifecycle
  (received → stored → broadcast). See `config/RequestIdFilter`.
- **Alerting rationale:** alert if p99 latency from *receipt* to *WebSocket push*
  exceeds 500ms — that's the user-visible "is my dashboard real-time?" SLO. Also
  alert on Redis connection failures (fan-out silently degrades) and on rate-limit
  rejection spikes (possible abuse).

## Security

- Slugs are cryptographically random (`SlugGenerator`, `SecureRandom`), never
  sequential — prevents endpoint enumeration.
- Rate limiting is Redis-backed and enforced **per-slug and per-IP** across all
  instances (`RateLimiter`).
- Captured bodies are capped/truncated (`webhookrelay.max-body-bytes`) so a huge
  payload can't OOM the service.
- CORS is an explicit allow-list per environment — never wildcard in prod.
