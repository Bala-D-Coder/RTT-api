# Developer Onboarding & Local Setup Guide (RTT-api)

Backend service for the Real-time Transcription & Voice Notes application.

---

## 📋 Prerequisites

| Tool | Required Version | Status on Dev Box |
|---|---|---|
| **Java** | OpenJDK 21 (LTS) | Installed (Temurin 21.0.11) |
| **Build Tool** | Apache Maven ≥ 3.9 + committed `mvnw` | Installed (`./mvnw.cmd` on Windows) |
| **Docker** | Docker Desktop with WSL2 backend | Installed (Engine 29.9.2, Compose v5.6.1) |
| **Postgres Port** | Host port `5433` (5432 kept for native DB) | Confirmed free |

---

## 🚀 Quickstart (3 Steps)

### 1. Launch Supporting Containers (Postgres + Whisper)

```bash
docker compose up -d postgres whisper
```
- PostgreSQL 16 Alpine starts on port **5433** (`jdbc:postgresql://127.0.0.1:5433/rtt`).
- Whisper local sidecar starts on port **8001** (`http://127.0.0.1:8001`).

### 2. Configure Environment

Copy `.env.example` to `.env`:
```bash
cp .env.example .env
```
Fill in your `GEMINI_API_KEY` (if testing live Gemini transcriber) and `SUPPORT_EMAIL`.

### 3. Verify & Run Backend

```bash
# Run all unit, integration, and OpenAPI verification tests
./mvnw -B test

# Run application locally
./mvnw spring-boot:run
```

App boots at `http://localhost:8080`.

---

## 📖 API Documentation & Endpoints

- **OpenAPI Schema (JSON)**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Swagger UI Interactive Docs**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Liveness Probe**: `GET http://localhost:8080/healthz`
- **Readiness Probe**: `GET http://localhost:8080/readyz`

---

## ⚙️ Spring Profiles

- `default` / `dev`: Local development profile (`seed-on-startup=true`, `show-sql=true`).
- `test`: Used during integration testing (`application-test.yml`).
- `full`: Used inside Docker Compose when running the containerized API (`docker compose --profile full up`).
