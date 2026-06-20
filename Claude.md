# Bulk Email Pro — Production Hardening Phase (P3.5)

You are the Lead Architect, Principal Backend Engineer, QA Lead, DevOps Engineer, and SRE for Bulk Email Pro.

## Project Vision

Bulk Email Pro will evolve into a cloud-first, multi-tenant omnichannel communication platform supporting:

* Email Marketing
* WhatsApp Campaigns
* SMS Campaigns
* Marketing Automation
* CRM
* Customer Data Platform (CDP)

Current focus is NOT feature expansion.

Current focus is:

RELIABILITY → DELIVERABILITY → OBSERVABILITY → AUTOMATION.

No new business features should be added until the production readiness checklist passes.

---

## Current Architecture

### Frontend

* Angular 17 (Standalone)
* Electron desktop application

### Backend

* Spring Boot 3.2
* Java 21

### Infrastructure

* PostgreSQL 16
* Redis 7
* RabbitMQ 3

### Patterns

* Multi-tenant architecture
* JWT authentication
* AES-256 encryption
* Transactional outbox
* Distributed Redis locks
* Event-driven messaging
* WebSocket progress updates

---

## Completed Priorities

### P1

* PostgreSQL migration
* Flyway
* Audit logs
* Suppression lists
* Outbox events

### P2

* Redis caching
* SMTP rate limiting
* JWT denylist
* API rate limiting
* Distributed locking

### P3

* RabbitMQ queues
* DLQs
* Outbox relay
* Campaign consumers
* Webhook consumers

---

## Current Objective

Implement Priority P3.5: Production Validation, Automated Testing, Monitoring, and Deployment.

Feature development is prohibited until P3.5 is complete.

---

## Success Criteria

The platform must satisfy all requirements below:

* Zero duplicate email sends
* No cross-tenant data leakage
* Recover automatically from application restarts
* Recover automatically from RabbitMQ outages
* Recover automatically from SMTP failures
* Horizontal scaling supported
* Complete observability
* Fully automated testing
* Minimum 80% code coverage
* Zero manual testing dependency

---

# Phase 1 — Mailpit Integration

Add Mailpit to docker-compose.yml.

Configuration:

* SMTP Port: 1025
* UI Port: 8025

Create:

* application-mailpit.yml

Requirements:

* Enable Mailpit profile
* Disable authentication
* Support HTML preview
* Support tracking pixel verification

Create integration tests that verify:

* Email delivery
* Merge tags
* Tracking links
* Unsubscribe links
* RFC headers

---

# Phase 2 — Integration Testing

Use:

* JUnit 5
* Testcontainers

Required containers:

* PostgreSQL
* Redis
* RabbitMQ
* Mailpit

Create tests for:

## Outbox

* Event persists during RabbitMQ outage
* Event publishes after recovery

## RabbitMQ

* Queue processing
* DLQ processing
* Retry behaviour

## Redis

* Distributed locking
* Rate limiting
* JWT denylist

## SMTP

* Email delivery
* Retry handling
* Bounce handling

## Multi-instance

* Prevent duplicate campaign processing

Requirements:

* Tests must be repeatable
* Tests must run in CI
* Tests must not require manual setup

Commands:

mvn test
mvn verify

---

# Phase 3 — API Testing

Use:

* RestAssured

Create tests for:

* Register organisation
* Login
* Logout
* Contact CRUD
* Import contacts
* Create campaign
* Send campaign
* Pause campaign
* Resume campaign
* Cancel campaign
* Unsubscribe
* Open tracking
* Click tracking
* Webhook processing

---

# Phase 4 — Frontend E2E Testing

Use:

* Playwright

Critical user journeys:

1. User registration
2. Login
3. SMTP configuration
4. Contact import
5. Campaign creation
6. Test email send
7. Campaign launch
8. Analytics dashboard

Requirements:

* Run headless in CI
* Generate screenshots on failure
* Generate HTML reports

Command:

npm run test:e2e

---

# Phase 5 — Load Testing

Use:

* k6

Create scripts for:

## Login API

* 500 concurrent users

## Contact Import

* 100,000 contacts

## Campaign Send

* 1,000,000 recipients

## Tracking Endpoints

* 10,000 requests/minute

Generate reports for:

* Throughput
* Error rate
* P95 latency
* P99 latency

Command:

k6 run tests/load/campaign-send.js

---

# Phase 6 — Observability

Add:

* Prometheus
* Grafana
* OpenTelemetry

Expose metrics for:

## Application

* API latency
* Error rate
* Request count

## PostgreSQL

* Connection pool
* Query latency

## Redis

* Cache hit ratio
* Memory usage

## RabbitMQ

* Queue depth
* DLQ size
* Consumer lag

## Email

* Send rate
* Open rate
* Bounce rate
* Complaint rate

Create:

* Grafana dashboards
* Alert definitions

Critical alerts:

* Queue depth > 10,000
* DLQ size > 0
* SMTP failures > 5%
* Error rate > 2%
* Database connections > 80%

---

# Phase 7 — CI/CD

Create GitHub Actions workflows.

Workflow requirements:

## Pull Request

* Build
* Unit tests
* Integration tests
* Static analysis

## Main Branch

* Build Docker image
* Push image
* Deploy automatically

Required quality gates:

* Code coverage ≥ 80%
* No critical vulnerabilities
* No failing tests

---

# Phase 8 — Deployment

Initial cloud stack:

Frontend:

* Vercel

Backend:

* Railway

Database:

* Neon PostgreSQL

Redis:

* Upstash Redis

RabbitMQ:

* CloudAMQP

Storage:

* Cloudflare R2

Requirements:

* Docker-based deployment
* Environment variables only
* No secrets in source code

Generate:

* Dockerfile
* docker-compose.prod.yml
* deployment.md
* .env.example

---

# Working Rules

Before writing code:

1. Analyze existing implementation.
2. Reuse existing patterns.
3. Avoid breaking API contracts.
4. Preserve tenant isolation.
5. Preserve backward compatibility.

For every implementation:

1. Business objective
2. Architecture changes
3. Database changes
4. API changes
5. Backend implementation
6. Frontend implementation
7. Test strategy
8. Monitoring requirements
9. Deployment steps
10. Rollback plan

Never mark a priority complete until:

* Tests pass
* Monitoring exists
* Documentation exists
* Failure scenarios are validated

If a requirement conflicts with reliability, choose reliability over speed.
