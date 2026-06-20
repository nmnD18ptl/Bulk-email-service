# Deployment Guide — Bulk Email Pro

Beta deployment stack:

| Layer | Service | Plan | Cost |
|---|---|---|---|
| Backend | Railway | Hobby ($5/mo) or Trial | — |
| Frontend | Vercel | Hobby (free) | — |
| PostgreSQL | Neon | Free tier | 0.5 GB storage |
| Redis | Upstash | Free tier | 10,000 cmd/day |
| RabbitMQ | CloudAMQP | Little Lemur (free) | 1M msg/mo |

---

## Prerequisites

- Git repository pushed to GitHub (Railway and Vercel deploy from GitHub)
- Node.js 18+ installed locally
- Docker installed locally (for local production simulation)
- Accounts created at: [railway.app](https://railway.app), [vercel.com](https://vercel.com), [neon.tech](https://neon.tech), [upstash.com](https://upstash.com), [cloudamqp.com](https://cloudamqp.com)

---

## Step 1 — Neon PostgreSQL

1. Log in to [neon.tech](https://neon.tech) → **New Project**
2. Choose a region close to your Railway deployment (e.g. `us-east-1`)
3. Note the project name; Neon creates a database named `neondb` by default
4. Go to **Dashboard → Connection Details**
5. Select **Connection string** → copy the value (starts with `postgresql://`)
6. Convert the prefix for JDBC: `postgresql://` → `jdbc:postgresql://`
7. The full URL looks like:
   ```
   jdbc:postgresql://ep-xxxx.us-east-1.aws.neon.tech/neondb?sslmode=require
   ```
8. Record the following from the connection string:
   - `DB_URL` — the full JDBC URL above
   - `DB_USERNAME` — value after `://` before `:`
   - `DB_PASSWORD` — value between `:` and `@`

> **Neon free tier** caps at 10 simultaneous connections. `DB_POOL_MAX=5` in the env vars respects this limit.

---

## Step 2 — Upstash Redis

1. Log in to [upstash.com](https://upstash.com) → **Create Database**
2. Type: **Redis** | Region: same as Neon | TLS: **Enabled**
3. After creation, open the database → **Details** tab
4. Copy the **Redis URL** (starts with `rediss://`)
5. Set `REDIS_URL` to this value

---

## Step 3 — CloudAMQP RabbitMQ

1. Log in to [cloudamqp.com](https://cloudamqp.com) → **Create New Instance**
2. Plan: **Little Lemur** (free) | Region: same as Neon
3. After creation, click the instance → copy the **AMQP URL** (starts with `amqps://`)
4. Set `RABBITMQ_URL` to this value

> CloudAMQP free tier allows 1 million messages/month and 20 concurrent connections.

---

## Step 4 — Generate secrets

Run the following to generate cryptographically random secrets:

```bash
# JWT secret (copy output → JWT_SECRET)
openssl rand -base64 48

# Encryption key (copy output → ENCRYPTION_KEY)
openssl rand -base64 48
```

Both values must be **at least 32 characters**. The `StartupValidator` rejects shorter values.

---

## Step 5 — Backend deployment (Railway)

### 5.1 Create the service

1. Open [railway.app](https://railway.app) → **New Project** → **Deploy from GitHub repo**
2. Select your repository → Railway detects the `Dockerfile` in `backend/`
3. Railway creates the service and starts building

### 5.2 Configure the root directory

Railway must build from the `backend/` subdirectory:

- Service → **Settings** → **Root Directory** → set to `backend`

### 5.3 Set environment variables

Service → **Variables** → **Raw Editor** → paste and fill in:

```env
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://<host>.neon.tech/neondb?sslmode=require
DB_USERNAME=<neon-username>
DB_PASSWORD=<neon-password>
DB_POOL_MAX=5
DB_POOL_MIN=1
REDIS_URL=rediss://default:<token>@<host>.upstash.io:6379
REDIS_POOL_MAX=5
REDIS_POOL_MIN=1
RABBITMQ_URL=amqps://<user>:<pass>@<host>.cloudamqp.com/<vhost>
JWT_SECRET=<your-generated-secret>
ENCRYPTION_KEY=<your-generated-key>
JWT_EXPIRY_MS=3600000
CORS_ORIGINS=https://<your-project>.vercel.app,https://*.vercel.app
TRACKING_BASE_URL=https://<your-service>.railway.app
UNSUBSCRIBE_BASE_URL=https://<your-service>.railway.app
PORT=8080
```

> `TRACKING_BASE_URL` and `UNSUBSCRIBE_BASE_URL` need the Railway public URL.  
> Find it under Service → **Settings** → **Domains** after the first deploy.

### 5.4 Configure health check

Service → **Settings** → **Health Check**:

- **Path**: `/actuator/health`
- **Timeout**: `300` seconds (allows Flyway to run migrations on first boot)

### 5.5 Verify deployment

After the first deploy completes:

```bash
# Replace with your Railway domain
curl https://<your-service>.railway.app/actuator/health
```

Expected response:
```json
{"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"},"rabbit":{"status":"UP"}}}
```

---

## Step 6 — Frontend deployment (Vercel)

### 6.1 Prepare the Angular build

Verify the API base URL is configurable via an environment variable.  
In `frontend/src/environments/environment.prod.ts`:

```typescript
export const environment = {
  production: true,
  apiUrl: process.env['API_URL'] || 'https://<your-service>.railway.app'
};
```

### 6.2 Create the Vercel project

1. Open [vercel.com](https://vercel.com) → **Add New Project** → import your GitHub repo
2. **Framework Preset**: Angular
3. **Root Directory**: `frontend`
4. **Build Command**: `npm run build -- --configuration production`
5. **Output Directory**: `dist/bulk-email-frontend/browser`

### 6.3 Set environment variables

Vercel → Project → **Settings** → **Environment Variables**:

| Key | Value |
|---|---|
| `API_URL` | `https://<your-service>.railway.app` |

### 6.4 Verify frontend

After deploy, open the Vercel URL and confirm:
- Login page loads
- Attempting login reaches the backend (check Network tab — should see `200` from Railway)

---

## Step 7 — Update CORS after Vercel deploy

Once Vercel assigns your domain, update `CORS_ORIGINS` in Railway:

```
CORS_ORIGINS=https://bulk-email-pro.vercel.app,https://*.vercel.app
```

Railway redeploys automatically when variables change.

---

## Local production simulation

To run the production Docker image locally against cloud services:

```bash
# 1. Copy and fill in credentials
cp .env.example .env

# 2. Build the image
docker compose -f docker-compose.prod.yml build

# 3. Start
docker compose -f docker-compose.prod.yml up

# 4. Verify
curl http://localhost:8080/actuator/health
```

---

## Validation checklist

Run these checks after every deploy:

| Check | Command / URL |
|---|---|
| Backend health | `GET /actuator/health` → `{"status":"UP"}` |
| Database connected | health response includes `"db":{"status":"UP"}` |
| Redis connected | health response includes `"redis":{"status":"UP"}` |
| RabbitMQ connected | health response includes `"rabbit":{"status":"UP"}` |
| Flyway migrations | Railway logs: `Successfully applied N migrations` |
| CORS works | Frontend login request returns `200`, not `CORS error` |
| Actuator unauthenticated | `curl /actuator/health` returns `200` without a token |
| Startup validator | Railway logs: `Startup validation passed` |
| Graceful shutdown | Railway logs on redeploy: `Graceful shutdown initiated` |

---

## Monitoring

After P3.5 Phase 6 (Prometheus + Grafana), scrape metrics from:

```
https://<your-service>.railway.app/actuator/prometheus
```

Key metrics to alert on:

| Metric | Alert threshold |
|---|---|
| `hikaricp_connections_active` | > 8 (approaching Neon's 10-connection limit) |
| `rabbitmq_queue_messages` | > 10,000 |
| `http_server_requests_seconds_max` | > 5s |
| `jvm_memory_used_bytes` | > 400 MB (approaching 512 MB Railway limit) |

---

## Rollback procedure

### Railway backend rollback

1. Railway → Deployments tab → find the last working deploy
2. Click **Redeploy** on that entry
3. Railway rebuilds from the same commit — no code change needed

### Vercel frontend rollback

1. Vercel → Deployments → find the last working deploy
2. Click **...** → **Promote to Production**

### Database rollback

Flyway does not provide automatic rollback. To revert a migration:

1. Connect to Neon via the SQL editor or psql
2. Apply the inverse SQL manually
3. Delete the row from `flyway_schema_history` for the reverted migration
4. Redeploy — Flyway will re-run from the deleted migration number on next boot

> Always take a Neon snapshot before deploying migrations:
> Neon Dashboard → **Branches** → **Create branch** (point-in-time snapshot).

---

## Environment variable reference

| Variable | Required | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | yes | Must be `prod` |
| `PORT` | yes | Set automatically by Railway |
| `DB_URL` | yes | Full JDBC URL from Neon (includes `?sslmode=require`) |
| `DB_USERNAME` | yes | Neon database user |
| `DB_PASSWORD` | yes | Neon database password |
| `DB_POOL_MAX` | no | HikariCP max connections (default `10`, Neon free limit) |
| `DB_POOL_MIN` | no | HikariCP min idle (default `2`) |
| `REDIS_URL` | yes | Upstash Redis URL (`rediss://`) |
| `REDIS_POOL_MAX` | no | Lettuce pool max (default `10`) |
| `REDIS_POOL_MIN` | no | Lettuce pool min idle (default `1`) |
| `RABBITMQ_URL` | yes | CloudAMQP AMQP URL (`amqps://`) |
| `RABBITMQ_PREFETCH` | no | Consumer prefetch count (default `5`) |
| `JWT_SECRET` | yes | ≥ 32 chars, random |
| `ENCRYPTION_KEY` | yes | ≥ 32 chars, random (AES-256 for SMTP passwords) |
| `JWT_EXPIRY_MS` | no | Token TTL in ms (default `3600000` = 1 h) |
| `CORS_ORIGINS` | yes | Comma-separated list of allowed frontend origins |
| `TRACKING_BASE_URL` | yes | Railway public URL (for tracking pixel and click links) |
| `UNSUBSCRIBE_BASE_URL` | yes | Railway public URL (for unsubscribe header) |
