# StockPulse Platform

Starter platform for the StockPulse stack:

- Spring Boot 3
- Spring Security with JWT
- Spring Data JPA + PostgreSQL
- Spring WebSocket
- Spring Scheduler
- Spring Cache + Redis
- Spring Mail alerts
- Swagger / OpenAPI

## Run infrastructure

```powershell
docker compose up -d postgres redis
```

## Run the app

```powershell
mvn spring-boot:run
```

## Run the frontend

```powershell
cd frontend
npm install
npm run dev
```

## Run the full stack in containers

```powershell
docker compose up --build
```

Your local Alpha Vantage key is stored in `src/main/resources/application-local.yml`, which is gitignored and auto-imported by `application.yml`.
For live market data, `src/main/resources/application-local.yml` now enables Redis caching locally.
The app also defaults JVM timezone handling to `Asia/Kolkata` to avoid the PostgreSQL `Asia/Calcutta` startup error on some Windows setups.

## Useful URLs

- API health: `http://localhost:8080/api/health`
- Market quote: `http://localhost:8080/api/market/IBM/quote`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- STOMP endpoint: `ws://localhost:8080/ws`
- Frontend demo desk: `http://localhost:5173`
- Frontend container demo: `http://localhost:3000`

## Deployment prep

Before publishing the project, make sure production uses environment variables instead of local-only defaults:

- `PORT=8080`
- `DB_URL=jdbc:postgresql://<host>:5432/<database>`
- `DB_USERNAME=<database-user>`
- `DB_PASSWORD=<database-password>`
- `REDIS_HOST=<redis-host>`
- `REDIS_PORT=<redis-port>`
- `CACHE_TYPE=redis`
- `JWT_SECRET=<base64-encoded-secret>`
- `APP_SECURITY_CORS_ALLOWED_ORIGINS=https://your-frontend-domain`
- `ALPHA_VANTAGE_API_KEY=<your-api-key>`
- `ALERTS_MAIL_ENABLED=false` for the first public deploy unless you have real SMTP credentials ready
- `MAIL_HOST=smtp.gmail.com`
- `MAIL_PORT=587`
- `MAIL_USERNAME=<your-email-address>`
- `MAIL_PASSWORD=<your-smtp-app-password>`
- `MAIL_FROM=<your-email-address>`

The frontend should point at the deployed backend:

- `VITE_API_BASE_URL=https://your-backend-domain`
- `VITE_WS_BASE_URL=https://your-backend-domain`

If you deploy the frontend and backend on different public domains, add the frontend URL to `APP_SECURITY_CORS_ALLOWED_ORIGINS`. You can provide multiple origins as a comma-separated list.

Do not commit real secrets to Git. Keep local secrets in `src/main/resources/application-local.yml` or untracked `.env` files, and use your hosting platform's environment-variable settings for production.

## Mail setup

You cannot use random SMTP credentials. If mail is enabled, they must be valid credentials from a real SMTP provider.

For a simple first deploy, keep:

- `ALERTS_MAIL_ENABLED=false`

That lets the app run publicly without breaking alerts when SMTP is not configured yet.

If you want Gmail SMTP later:

- `MAIL_HOST=smtp.gmail.com`
- `MAIL_PORT=587`
- `MAIL_USERNAME=<your Gmail address>`
- `MAIL_PASSWORD=<Google app password>`
- `MAIL_FROM=<same Gmail address>`

`MAIL_PASSWORD` should be a Google app password, not your normal Gmail login password. Gmail SMTP also requires the account to be configured appropriately, typically with 2-Step Verification enabled before an app password can be used.

## Auth endpoints

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me` with `Authorization: Bearer <token>`

## Portfolio endpoints

- `GET /api/portfolio`
- `GET /api/portfolio/trades`
- `POST /api/portfolio/buy`
- `POST /api/portfolio/sell`

## Alert endpoints

- `GET /api/alerts`
- `POST /api/alerts`

## Leaderboard endpoint

- `GET /api/leaderboard`

## Sample trade requests

```powershell
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/portfolio/buy" `
  -Headers @{ Authorization = "Bearer YOUR_TOKEN" } `
  -ContentType "application/json" `
  -Body '{"symbol":"AAPL","quantity":5}'
```

```powershell
Invoke-RestMethod -Method GET `
  -Uri "http://localhost:8080/api/portfolio" `
  -Headers @{ Authorization = "Bearer YOUR_TOKEN" }
```

## Phase 3 notes

- Alpha Vantage quotes are fetched with `WebClient`
- Quote cache TTL is `60` seconds
- A scheduled poll refreshes tracked symbols every `60` seconds

## Phase 4 notes

- Scheduler refreshes now publish STOMP messages to `/topic/prices/{ticker}`
- The React frontend subscribes to live ticker topics with SockJS + STOMP.js
- The dashboard updates portfolio totals and Recharts P&L visuals in real time

## Phase 5 notes

- Price alerts are stored in PostgreSQL and evaluated on each scheduled poll
- Alert emails use Spring Mail and Gmail SMTP-compatible settings from environment variables
- Leaderboard ranks users by portfolio percentage gain
- Docker Compose now includes backend, frontend, PostgreSQL, and Redis
