# Spike 0c: Web Speech Sanity & Hybrid Engine Split

**Status**: Verified against `RTT-ui/src/services/web-speech.ts`  
**Purpose**: Confirm the *interim from browser Web Speech, final from server Whisper/Gemini* hybrid architecture functions smoothly without race conditions or text fighting.

---

## 1. Browser Speech Recognition Surface

The Web Speech API provides client-side, zero-cost, near-zero-latency interim speech recognition in Chromium browsers (Chrome, Edge) via `webkitSpeechRecognition`.

### Inspected Driver Architecture (`RTT-ui/src/services/web-speech.ts`)

- **Language Support**: Driven in `en-IN` (Indian English) or `hi-IN` (Hindi). Locale auto-detection maps Indic browser locales (`hi`, `mr`, `bn`) to `hi-IN` and others to `en-IN`.
- **Event Contract**:
  - Emits `partial` events: `{ type: 'partial', text: interim, source: 'webspeech', ts: elapsed() }` for fast responsive word-level feedback in `RecorderBar`.
  - Emits `final` events: `{ type: 'final', seq, text, startMs, endMs, confidence, lang }` when the speech engine determines a phrase boundary.
- **Microphone Level Meter**: Coupled with `LevelMeter` via Web Audio API (`AudioContext` + `AnalyserNode`) providing 0..1 audio levels for the visual waveform.

---

## 2. Hybrid Architecture: Interim vs. Final Coordination

The system architecture cleanly separates responsibilities to get the best of both worlds:

```
[ Microphone ]
      │
      ├──> [ Browser Web Speech API ] ───> Instant Partial Deltas (< 150 ms latency)
      │                                       │
      │                                       ▼
      │                                 [ RecorderBar UI ] (Words appear as spoken)
      │                                       ▲
      └──> [ WebSocket PCM16 16kHz ]          │
                 │                            │
                 ▼                            │
      [ Backend Engine (Gemini / Whisper) ] ─┴──> Authoritative Final Segments
                                                  (Accurate Hinglish & Persistence)
```

### Why this design wins:
1. **Zero UI lag**: The speaker sees words instantly on screen as they speak (from the browser engine).
2. **High accuracy persistence**: The server engine (Whisper / Gemini) delivers superior code-mixed Hinglish accuracy and punctuation.
3. **No text fighting**: When an authoritative `final` segment arrives, the UI advances `cursorMs` and appends the final text, clearing the transient interim buffer.
4. **Offline / Fallback Resilience**: If server connectivity drops or engine is overloaded, the client continues receiving local Web Speech results.

---

## 3. Exit Criteria Evaluation

| Criterion | Evaluation | Status |
|---|---|---|
| **Browser Compatibility** | Verified in Chromium (Chrome & Edge on Windows) | **PASS** |
| **Locale Handling** | Auto-resolution to `en-IN` / `hi-IN` with fallback | **PASS** |
| **Error Handling** | Fatal errors (`not-allowed`, `no-microphone`) surface UI banners; transient silences auto-restart silently | **PASS** |
| **Seam Integrity** | Implements `TranscriptionDriver` interface identically to `ServerWsDriver` | **PASS** |
