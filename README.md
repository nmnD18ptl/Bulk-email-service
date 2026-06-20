# Bulk Email Pro

Cross-platform desktop bulk email application: **Spring Boot 3** (Java 17), **Angular 17** (standalone), **Electron 28+**, **H2** file database.

## Prerequisites

- **JDK 17+**
- **Apache Maven 3.9+** (or use your IDE to run the backend)
- **Node.js 18.19+** (the system Node 10 runtime is too old for Angular 17; use [nvm-windows](https://github.com/coreybutler/nvm-windows) or install a current LTS)

## Project layout

```
bulk-email-pro/
├── backend/           # Spring Boot API, H2 persistence
├── frontend/          # Angular SPA
├── electron/          # Desktop shell (starts JAR, loads UI)
├── scripts/build.sh   # Unix packaging helper
└── package.json       # Root npm scripts
```

## Local email testing (Mailpit)

Mailpit is a local SMTP catch-all that captures every outbound email without forwarding it anywhere. It is included in the Docker Compose stack.

**Start Mailpit:**

```bash
docker compose up -d mailpit
```

**View captured emails:** open [http://localhost:8025](http://localhost:8025) in your browser.

**Connect the backend to Mailpit** by activating the `mailpit` Spring profile:

```bash
SPRING_PROFILES_ACTIVE=mailpit ./mvnw spring-boot:run -pl backend
```

All emails sent through any SMTP configuration with host `localhost` / port `1025` will be captured. Configure an SMTP config in the app pointing to `localhost:1025` (no authentication) to route campaign sends through Mailpit during development.

**Run email integration tests:**

```bash
cd backend && ./mvnw verify -Dtest=MailpitEmailDeliveryIntegrationTest
# or run the full integration suite:
./mvnw verify
```

The integration tests spin up an isolated Mailpit container automatically via Testcontainers — no running Docker Compose stack is required.

---

## Development

1. Start the API (from repo root):

   ```bash
   npm run backend:dev
   ```

   H2 file location defaults to `%USERPROFILE%\.bulk-email-pro\data\bulk-email-db` (override with `bulkemail.db.path`).

2. In another terminal, start Angular with API proxy:

   ```bash
   cd frontend && npm install && npm start
   ```

   Open `http://localhost:4200`. API calls to `/api` are proxied to `http://localhost:8080`.

Alternatively:

```bash
npm install
npm run start:dev
```

## Implemented (Priority 1): Contact management

- **Import**: CSV and Excel (`.xlsx`), auto column mapping (Email, Name, Company, Country, Custom 1–5)
- **Validation**: RFC syntax + optional DNS MX check
- **Deduplication**: within file and against existing contacts (by email, case-insensitive)
- **Tags**: segment contacts; filter list by tag
- **Status**: `ACTIVE`, `UNSUBSCRIBED`, `BOUNCED`, `COMPLAINED`
- **Search / filter**: full-text-ish search across main fields + status + tags
- **Export**: CSV

### API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/contacts` | Paginated list (`search`, `status`, `tagIds`, Spring `Pageable`) |
| GET | `/api/contacts/{id}` | Single contact |
| POST | `/api/contacts` | Create (`validateMx` query param) |
| PUT | `/api/contacts/{id}` | Update |
| DELETE | `/api/contacts/{id}` | Delete |
| POST | `/api/contacts/import` | Multipart `file`, optional `validateMx`, `tagNames` |
| GET | `/api/contacts/export` | CSV download |
| GET/POST/PUT/DELETE | `/api/tags` | Tag CRUD |

## Build

```bash
npm run build:backend    # JAR in backend/target/
npm run build:frontend   # frontend/dist/bulk-email-frontend/browser/
```

## Electron

```bash
cd electron && npm install
```

Set `BULKEMAIL_DEV_URL=http://localhost:4200` during development so the shell loads the Angular dev server. Otherwise it loads the production build from `frontend/dist/...` via `loadFile` (requires `npm run build:frontend` first).

Packaging (after backend + frontend builds):

```bash
npm run build:electron
```

Install **Java** on the target machine so the packaged app can spawn `java -jar`.

## Roadmap (from spec)

Priorities 2–10: SMTP, campaigns, batching, spam analysis, tracking, analytics, warmup, automation, hardened Electron installers, distribution.

## Compliance note

Production sending requires valid **unsubscribe**, **physical address**, consent tracking, and DNS authentication (SPF/DKIM/DMARC). This codebase will add those in later milestones; do not send bulk mail without them.
