import os
import sys
import time
import json
import numpy as np
import soundfile as sf
from faster_whisper import WhisperModel

# Ensure UTF-8 output on Windows console
if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
FIXTURES_DIR = os.path.join(SCRIPT_DIR, "..", "fixtures")
AUDIO_FILE = os.path.join(FIXTURES_DIR, "hinglish_30s.wav")
SILENCE_FILE = os.path.join(FIXTURES_DIR, "silence_5s.wav")

CPU_THREADS = 8
COMPUTE_TYPE = "int8"
DEVICE = "cpu"

MODELS_TO_TEST = [
    "base",
    "small",
]

def load_audio_info(path):
    data, sr = sf.read(path)
    duration = len(data) / sr
    return duration, sr

def run_benchmarks():
    if not os.path.exists(AUDIO_FILE):
        print(f"Error: {AUDIO_FILE} does not exist. Run generate_fixtures.py first.")
        sys.exit(1)

    duration, sr = load_audio_info(AUDIO_FILE)
    print(f"=== Faster-Whisper Fallback Spike (0b) ===")
    print(f"Host CPU: 12th Gen Intel Core i5-12500H (16 threads)")
    print(f"Config: {CPU_THREADS} threads, {COMPUTE_TYPE} precision, device={DEVICE}")
    print(f"Fixture: {AUDIO_FILE} (Duration: {duration:.2f}s, SR: {sr} Hz)\n")

    results = {
        "metadata": {
            "device": DEVICE,
            "cpu_threads": CPU_THREADS,
            "compute_type": COMPUTE_TYPE,
            "audio_duration_sec": duration,
        },
        "models": {},
        "language_modes": {},
        "prompt_conditioning": {},
        "silence_noise_rejection": {},
    }

    # 1. Benchmark Models for RTF
    print("--- 1. Model RTF Benchmark ---")
    for model_name in MODELS_TO_TEST:
        print(f"\nLoading model '{model_name}' ({COMPUTE_TYPE})...")
        t_load_start = time.perf_counter()
        model = WhisperModel(model_name, device=DEVICE, compute_type=COMPUTE_TYPE, cpu_threads=CPU_THREADS)
        t_load = time.perf_counter() - t_load_start
        print(f"Model loaded in {t_load:.2f}s.")

        # Test beam_size=1 (real-time streaming friendly)
        t_start = time.perf_counter()
        segments, info = model.transcribe(
            AUDIO_FILE,
            beam_size=1,
            vad_filter=True,
            word_timestamps=True
        )
        segment_list = list(segments)
        t_elapsed = time.perf_counter() - t_start
        rtf = t_elapsed / duration
        full_text = " ".join(s.text.strip() for s in segment_list)

        print(f"  [beam_size=1 + vad] Time: {t_elapsed:.2f}s | RTF: {rtf:.3f} (pass: {rtf < 1.0})")
        print(f"  Detected language: {info.language} (probability: {info.language_probability:.2f})")
        print(f"  Transcript: {full_text}")

        results["models"][f"{model_name}_beam1"] = {
            "load_time_sec": round(t_load, 2),
            "transcribe_time_sec": round(t_elapsed, 2),
            "rtf": round(rtf, 4),
            "rtf_pass": rtf < 1.0,
            "detected_language": info.language,
            "language_prob": round(info.language_probability, 3),
            "transcript": full_text
        }

        # Also test beam_size=5
        t_start5 = time.perf_counter()
        segments5, info5 = model.transcribe(
            AUDIO_FILE,
            beam_size=5,
            vad_filter=True
        )
        segment_list5 = list(segments5)
        t_elapsed5 = time.perf_counter() - t_start5
        rtf5 = t_elapsed5 / duration
        print(f"  [beam_size=5 + vad] Time: {t_elapsed5:.2f}s | RTF: {rtf5:.3f} (pass: {rtf5 < 1.0})")

        results["models"][f"{model_name}_beam5"] = {
            "load_time_sec": round(t_load, 2),
            "transcribe_time_sec": round(t_elapsed5, 2),
            "rtf": round(rtf5, 4),
            "rtf_pass": rtf5 < 1.0,
            "detected_language": info5.language,
            "language_prob": round(info5.language_probability, 3),
            "transcript": " ".join(s.text.strip() for s in segment_list5)
        }

    # 2. Language Modes Comparison (using 'base' and 'small')
    print(f"\n--- 2. Language Modes Comparison (using 'base') ---")
    model_base = WhisperModel("base", device=DEVICE, compute_type=COMPUTE_TYPE, cpu_threads=CPU_THREADS)
    for lang in [None, "hi", "en"]:
        lang_label = lang if lang else "auto"
        t_start = time.perf_counter()
        segments, info = model_base.transcribe(
            AUDIO_FILE,
            language=lang,
            beam_size=1,
            vad_filter=True
        )
        seg_list = list(segments)
        t_elapsed = time.perf_counter() - t_start
        text = " ".join(s.text.strip() for s in seg_list)
        print(f"  Language={lang_label} ({t_elapsed:.2f}s): {text}")
        results["language_modes"][lang_label] = {
            "time_sec": round(t_elapsed, 2),
            "transcript": text
        }

    # 3. Initial Prompt Conditioning
    print(f"\n--- 3. Hinglish Initial Prompt Conditioning ---")
    prompt = "Meeting notes, deployment plan, staging server pe test, review kal subah."
    t_start = time.perf_counter()
    segments, info = model_base.transcribe(
        AUDIO_FILE,
        initial_prompt=prompt,
        beam_size=1,
        vad_filter=True
    )
    seg_list = list(segments)
    t_elapsed = time.perf_counter() - t_start
    text_prompted = " ".join(s.text.strip() for s in seg_list)
    print(f"  Prompt: '{prompt}'")
    print(f"  Output: {text_prompted}")
    results["prompt_conditioning"] = {
        "prompt": prompt,
        "time_sec": round(t_elapsed, 2),
        "transcript": text_prompted
    }

    # 4. Silence & Noise Rejection Thresholds
    print(f"\n--- 4. Silence & Noise Thresholds ({SILENCE_FILE}) ---")
    if os.path.exists(SILENCE_FILE):
        segments, info = model_base.transcribe(
            SILENCE_FILE,
            beam_size=1,
            vad_filter=False,
            word_timestamps=True
        )
        silence_segs = list(segments)
        print(f"  Silence segments returned: {len(silence_segs)}")
        silence_data = []
        for s in silence_segs:
            silence_data.append({
                "text": s.text,
                "avg_logprob": round(s.avg_logprob, 3),
                "no_speech_prob": round(s.no_speech_prob, 3),
                "compression_ratio": round(s.compression_ratio, 3)
            })
            print(f"    seg text: '{s.text}', no_speech_prob: {s.no_speech_prob:.3f}, avg_logprob: {s.avg_logprob:.3f}")
        results["silence_noise_rejection"] = {
            "num_segments": len(silence_segs),
            "segments": silence_data,
            "recommended_no_speech_threshold": 0.6,
            "recommended_logprob_threshold": -1.0
        }

    # Save raw JSON
    out_json = os.path.join(SCRIPT_DIR, "whisper_benchmark_results.json")
    with open(out_json, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    print(f"\nSaved benchmark results to {out_json}")

if __name__ == "__main__":
    run_benchmarks()
