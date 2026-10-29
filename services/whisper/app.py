import io
import os
import time
import numpy as np
import soundfile as sf
from fastapi import FastAPI, Request, Response, HTTPException, Query
from faster_whisper import WhisperModel

MODEL_NAME = os.getenv("WHISPER_MODEL", "base")
COMPUTE_TYPE = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
CPU_THREADS = int(os.getenv("WHISPER_CPU_THREADS", "8"))
DEVICE = "cpu"

app = FastAPI(title="RTT Whisper Fallback Sidecar")

print(f"Loading faster-whisper model '{MODEL_NAME}' ({COMPUTE_TYPE}) on {DEVICE} with {CPU_THREADS} threads...")
model = WhisperModel(MODEL_NAME, device=DEVICE, compute_type=COMPUTE_TYPE, cpu_threads=CPU_THREADS)
print("Whisper model loaded and ready.")

@app.get("/healthz")
def healthz():
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "compute_type": COMPUTE_TYPE,
        "device": DEVICE,
        "threads": CPU_THREADS
    }

@app.post("/transcribe")
@app.post("/asr")
async def transcribe(
    request: Request,
    sample_rate: int = Query(16000, description="Sample rate in Hz"),
    language: str | None = Query(None, description="Language code: 'hi', 'en', or None for auto"),
    prompt: str | None = Query(None, description="Optional initial prompt to condition vocabulary")
):
    body = await request.body()
    if not body:
        raise HTTPException(status_code=400, detail="Empty audio payload")

    content_type = request.headers.get("content-type", "")
    t0 = time.perf_counter()

    # Handle octet-stream raw PCM16 (s16le) or WAV/audio container
    if "octet-stream" in content_type or "audio/pcm" in content_type:
        # Convert raw PCM16 bytes to float32 numpy array normalized to [-1.0, 1.0]
        audio_data = np.frombuffer(body, dtype=np.int16).astype(np.float32) / 32768.0
    else:
        # Assume WAV/audio container
        try:
            audio_data, sr = sf.read(io.BytesIO(body))
            if audio_data.ndim > 1:
                audio_data = audio_data.mean(axis=1)
            sample_rate = sr
        except Exception as e:
            # Fallback to PCM16
            audio_data = np.frombuffer(body, dtype=np.int16).astype(np.float32) / 32768.0

    duration = len(audio_data) / sample_rate

    # Transcribe with beam_size=1, temperature=0.0, and tuned VAD for real-time fast speech
    segments, info = model.transcribe(
        audio_data,
        language=language,
        initial_prompt=prompt,
        beam_size=1,
        temperature=0.0,
        condition_on_previous_text=False,
        vad_filter=True,
        vad_parameters=dict(
            min_silence_duration_ms=250,
            speech_pad_ms=100
        ),
        word_timestamps=False
    )

    results = []
    for s in segments:
        results.append({
            "id": s.id,
            "seek": s.seek,
            "start": round(s.start, 3),
            "end": round(s.end, 3),
            "text": s.text.strip(),
            "avg_logprob": round(s.avg_logprob, 3),
            "no_speech_prob": round(s.no_speech_prob, 3),
            "compression_ratio": round(s.compression_ratio, 3)
        })

    elapsed = time.perf_counter() - t0
    full_text = " ".join(r["text"] for r in results)

    return {
        "text": full_text,
        "segments": results,
        "language": info.language,
        "language_probability": round(info.language_probability, 3),
        "duration": round(duration, 3),
        "elapsed_seconds": round(elapsed, 3),
        "rtf": round(elapsed / max(duration, 0.001), 3)
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
