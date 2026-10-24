# RTT-api — Real-time Transcription Backend (Spring Boot 4.1 / Java 21)

Backend service for the Real-time Transcription & Voice Notes web application. Built with **Java 21**, **Spring Boot 4.1**, **Spring Data JPA**, **Flyway**, and **PostgreSQL 16**.

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
- **Gemini Live Java Spike (`GeminiLiveJavaSpike.java`)**: Exits cleanly in 0.52s with code 1 when `GEMINI_API_KEY` is unset (socket safety verified).

---

## 🛠️ Quickstart (Local Dev Loop)

```bash
# 1. Start Postgres & Whisper sidecar in Docker
docker compose up -d postgres whisper

# 2. Run backend verification
./mvnw -B verify

# 3. Launch Spring Boot application
./mvnw spring-boot:run
```
