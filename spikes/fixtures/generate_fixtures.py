import asyncio
import io
import os
import edge_tts
import numpy as np
import soundfile as sf

OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))

# Real-world Hinglish sentences covering dictation, notes, and task assignments
HINGLISH_TEXT = (
    "आज की meeting में deployment plan discuss करेंगे और owner assign करेंगे Friday तक। "
    "Staging environment pe load test run करना है, sabhi critical bug fixes verify karne hain, "
    "aur client demo bilkul ready rakhna hai. "
    "Architecture review kal subah 9:00 AM team lead ke saath scheduled hai. "
    "Please make sure documentation update ho gaya ho and API contracts sign off ho chuke hain."
)

async def generate_speech():
    mp3_path = os.path.join(OUTPUT_DIR, "temp_hinglish.mp3")
    wav_path = os.path.join(OUTPUT_DIR, "hinglish_30s.wav")
    pcm_path = os.path.join(OUTPUT_DIR, "hinglish_30s.pcm")
    silence_wav_path = os.path.join(OUTPUT_DIR, "silence_5s.wav")
    silence_pcm_path = os.path.join(OUTPUT_DIR, "silence_5s.pcm")

    print(f"Generating Hinglish audio with edge-tts (hi-IN-MadhurNeural)...")
    communicate = edge_tts.Communicate(HINGLISH_TEXT, voice="hi-IN-MadhurNeural", rate="-4%")
    await communicate.save(mp3_path)

    # Read mp3 and resample to 16kHz mono PCM16
    data, samplerate = sf.read(mp3_path)
    if data.ndim > 1:
        data = data.mean(axis=1) # Mono
    
    # Target 16000 Hz
    target_sr = 16000
    if samplerate != target_sr:
        import scipy.signal
        num_samples = int(len(data) * target_sr / samplerate)
        data = scipy.signal.resample(data, num_samples)

    # Normalize to -1 dB peak
    max_val = np.max(np.abs(data))
    if max_val > 0:
        data = data / max_val * 0.9

    duration = len(data) / target_sr
    print(f"Audio duration: {duration:.2f} seconds ({len(data)} samples at {target_sr} Hz)")

    # Pad or trim to ~30 seconds if close, or keep natural length
    # Let's ensure it is at least 25-30s
    if duration < 30.0:
        pad_samples = int((30.0 - duration) * target_sr)
        data = np.pad(data, (0, pad_samples), mode='constant')
        duration = len(data) / target_sr
        print(f"Padded to {duration:.2f} seconds")

    # Save 16-bit PCM WAV
    sf.write(wav_path, data, target_sr, subtype='PCM_16')
    print(f"Wrote {wav_path}")

    # Save raw PCM16 (s16le)
    pcm_int16 = (data * 32767).astype(np.int16)
    with open(pcm_path, "wb") as f:
        f.write(pcm_int16.tobytes())
    print(f"Wrote raw PCM16 ({len(pcm_int16.tobytes())} bytes) to {pcm_path}")

    # Generate 5 seconds of silence
    silence = np.zeros(5 * target_sr, dtype=np.float32)
    sf.write(silence_wav_path, silence, target_sr, subtype='PCM_16')
    silence_int16 = (silence * 32767).astype(np.int16)
    with open(silence_pcm_path, "wb") as f:
        f.write(silence_int16.tobytes())
    print(f"Wrote {silence_wav_path} and {silence_pcm_path}")

    if os.path.exists(mp3_path):
        os.remove(mp3_path)
    print("Fixtures generation complete!")

if __name__ == "__main__":
    asyncio.run(generate_speech())
