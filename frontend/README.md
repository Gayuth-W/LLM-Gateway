# LLM Gateway — Console

A small Next.js operator console for the gateway. It talks to the running gateway and gives
you a clickable view of every endpoint: a request **Playground** (with a live view of which
gateway stage a request passed or was rejected at), **Teams** (limits, budgets, live spend,
create/edit), **Provider Health**, and **Spending**.

It lives in the same repository as the backend, under `frontend/`.

## How it reaches the gateway

The browser calls same-origin `/gateway/*`, and Next.js proxies those requests to the gateway
server-side (see `next.config.js`). That means **no CORS setup and no backend changes** — the
gateway runs exactly as it does today.

## Run it

Prerequisite: the gateway is up (`docker compose up` in the repo root) on `http://localhost:8080`.

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:3200**.

If your gateway runs somewhere other than `localhost:8080`, point the proxy at it:

```bash
GATEWAY_URL=http://localhost:9000 npm run dev
```

## What each screen shows

- **Playground** — pick a team (the seeded keys are preloaded), model, and priority, then send a
  prompt. The response panel shows the served model, whether a fallback was used, token usage, and
  a pipeline strip that lights up green through the stages the request passed and red at the stage
  that rejected it (401 auth, 402 budget, 403 model, 422 content, 429 rate limit). Streaming toggle
  included.
- **Teams** — every team with its limits and live spend; create a team, or edit limits/budgets
  (which apply on the next request).
- **Provider Health** — per-model status (HEALTHY / DEGRADED / DOWN), error rate, p99 latency, and
  sample count, auto-refreshing.
- **Spending** — the per-team/model cost rollup from Postgres, with a date range.

## Build

```bash
npm run build && npm start
```

Plain JavaScript + React (no TypeScript build step), styling in a single `app/globals.css`.
