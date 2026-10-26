# RTT-api — Real-time Transcription Backend (Spring Boot 4.1 / Java 21)

Backend service for the Real-time Transcription & Voice Notes web application. Built with **Java 21**, **Spring Boot 4.1**, **Spring Data JPA**, **Flyway**, and **PostgreSQL 16**.

---

## 🚀 Quickstart (4-Command Local Dev Loop)

```bash
# 1. Start Postgres & Whisper sidecar in Docker
docker compose up -d postgres whisper

# 2. Run backend automated verification suite
./mvnw -B test

# 3. Launch Spring Boot application (when ready to review)
./mvnw spring-boot:run

# 4. In a separate terminal, launch the UI with HTTP backend:
cd ../RTT-ui && VITE_DATA_SOURCE=http npm run dev
```

---

## ☕ Why Java & Spring Boot 4.1? (Architecture Decision)

The backend is built in **Java 21 with Spring Boot 4.1** rather than Node/Express because it aligns directly with the maintainer's deep production expertise and offers decisive technical advantages for this system:

1. **Robust Concurrency & Streaming Safety**: Spring's thread model and native WebSocket abstractions (`WebSocketHandler`, `ConcurrentWebSocketSessionDecorator`) provide deterministic, thread-safe streaming for real-time PCM audio chunks without race conditions or memory leaks.
2. **Provable Exactly-Once Delivery**: The reminder scheduler uses `JdbcClient` with explicit `SELECT ... FOR UPDATE SKIP LOCKED` inside declarative transaction boundaries, guaranteed idempotent by the `unique (reminder_id, attempt)` database constraint.
3. **First-Class Database Evolution**: Flyway is native to the Java ecosystem. Migrations execute deterministically on startup with strict schema validation (`clean-disabled=true`), eliminating ORM schema drift.
4. **Honest Architectural Seams**: Outbound integrations (Gemini Live raw WebSocket, Whisper sidecar HTTP client, Firebase Cloud Messaging) are clean interface seams (`LiveEngine`, `PushProvider`) configured via properties (`mock`, `whisper_local`, `gemini_live`), enabling hermetic, testcontainer-backed CI without live third-party dependencies.

---

## 🎙️ Speech Engine Spike & Measurement Notes (Part 0)

Full measurement report and benchmark methodology are documented in [`Docs/SPIKE-RESULTS.md`](../Docs/SPIKE-RESULTS.md).

### 1. Engine Costs & Free-Tier Reality (Measured 2026-10-24)

| Engine | Deployment | Free-Tier Reality | Pricing & Quota | Recommended Use |
|---|---|---|---|---|
| **Gemini Live** (`gemini-3.5-transcribe-live`) | Google GenAI Live WebSocket | **Free tier available**: 3 concurrent sessions, 15 RPM, 1,500 RPD. | Pay-as-you-go: ~$0.009 / audio-minute. A full 5-minute interview demo costs < $0.05. | **Primary engine** (when `GEMINI_API_KEY` is provided). Unmatched word-level streaming and Hinglish code-switching. |
| **Whisper Local** (`faster-whisper-small` / `base`) | Local Python sidecar (`services/whisper`) | **100% Free ($0)**, runs completely offline on host CPU. | 0 API quota, no rate limits. | **Fallback engine** (when offline or key not provided). |
| **Mock Engine** | In-process timer | **100% Free ($0)** | 0 API quota, 0 network. | **CI / Automated E2E testing**. |

### 2. Measured Fallback RTF on 16-Thread i5 CPU (int8, 8 threads)

On the host machine (12th Gen Intel Core i5-12500H):
- **`faster-whisper-base`** (int8, beam 1, VAD): **RTF = 0.065** (Transcribes a 33.3s Hinglish audio clip in **2.18s** — 15× faster than real-time!).
- **`faster-whisper-small`** (int8, beam 1, VAD): **RTF = 0.331** (Transcribes a 33.3s audio clip in **11.03s** — 3× faster than real-time).
- **Silence Rejection**: 0 phantom segments emitted on 5s silence clip (VAD filter cleanly rejects noise).
- **Gemini Live Java Spike (`GeminiLiveJavaSpike.java`)**: Raw WebSocket connection with base64 framing verified. Exits cleanly with code 1 when `GEMINI_API_KEY` is unset (socket safety verified).

### 3. ASR Audio Frame Cache (`asr_cache`)

To eliminate duplicate transcription work and reduce API costs:
- PCM16 audio frames are hashed using SHA-256.
- A dual-layer cache (in-memory Caffeine cache for ultra-low latency + PostgreSQL `asr_cache` table) caches transcription segments.
- On cache hit, response time is **< 1 ms** with **0 API cost**, and `hit_count` is incremented.

---

## 🗄️ Database Migrations (Flyway)

Flyway manages migration history in `src/main/resources/db/migration/` and `flyway_schema_history`:

| Version | Migration File | Description |
|---|---|---|
| **V1** | `V1__init.sql` | Core schema: `users`, `notes`, `note_blocks`, `tags`, `note_tags`, `reminders`, `reminder_deliveries`, `asr_cache`. |
| **V2** | `V2__auth_credentials.sql` | Auth & issued credentials: adds `fcm_token`, `role`, `label`, `first_login_at`, `expires_at`, `disabled_at`, `last_login_at`, `attempt_count`, `locked_until` to `users`. |
| **V3** | `V3__bin_indexes.sql` | Bin & Recycler: adds partial index `idx_notes_active_updated_at` (`notes(updated_at DESC) WHERE deleted_at IS NULL`) and `idx_notes_purge_at`. |
| **V4** | `V4__source_provenance.sql` | Block provenance: adds `source` column (`'dictation' \| 'typed'`) to `note_blocks` and index `idx_note_blocks_source_created_at`. |

---

## 🔐 Key Hygiene & Security Defaults (What is NOT in the repo)

1. **Zero Secrets in Version Control**: `.env` is gitignored. No private keys, passwords, or tokens are committed.
2. **Environment Variable Injection**: Credentials (`ADMIN_PASSWORD`, `DEMO_PASSWORD`, `GEMINI_API_KEY`, `JWT_SECRET`) are provided exclusively at runtime via environment variables.
3. **Structured Log Masking**: `RequestLoggingFilter` masks sensitive headers (`Authorization: Bearer [MASKED]`), query parameters (`ticket=[MASKED]`, `key=[MASKED]`), and cookies (`refreshToken=[MASKED]`).
4. **WebSocket URI Masking**: Gemini Live WebSocket outbound URLs mask the API key in all internal and debug logs (`?key=[MASKED]`).
5. **Fail on Unknown JSON Properties**: Configured globally (`spring.jackson.deserialization.fail-on-unknown-properties=true`) to immediately reject unexpected payload mutations with a clear `400 Bad Request`.
6. **Automated CI Secret Scanning**: The GitHub Actions CI pipeline runs a strict regex scan on tracked files to block accidental credential leaks.
