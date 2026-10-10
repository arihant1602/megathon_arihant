"""Independent host-side analysis of PCM16 WAV captures.

Usage: python3 analyze_wav.py out.json a.wav [b.wav ...]
Reports validity, duration, rate, RMS/peak, near-silent %, and a crude
speech-activity indicator (spread of 50 ms frame energies). Nonzero energy
alone is NOT treated as proof of speech.
"""
import json
import math
import struct
import sys
import wave


def analyze(path):
    r = {"file": path}
    try:
        with wave.open(path, "rb") as w:
            r.update(channels=w.getnchannels(), sample_width=w.getsampwidth(),
                     sample_rate=w.getframerate(), frames=w.getnframes())
            raw = w.readframes(w.getnframes())
    except Exception as e:  # invalid / truncated file
        r.update(valid_pcm_wav=False, error=str(e))
        return r
    r["valid_pcm_wav"] = r["sample_width"] == 2 and r["frames"] > 0
    n = len(raw) // 2
    s = struct.unpack(f"<{n}h", raw[: n * 2])
    r["duration_s"] = round(n / r["channels"] / r["sample_rate"], 3)
    peak = max((abs(x) for x in s), default=0)
    rms = math.sqrt(sum(x * x for x in s) / n) if n else 0.0
    db = lambda v: round(20 * math.log10(v / 32768), 2) if v > 0 else None
    r.update(peak=peak, peak_dbfs=db(peak), rms=round(rms, 2), rms_dbfs=db(rms),
             exact_zero_pct=round(100 * sum(1 for x in s if x == 0) / n, 2),
             near_silent_pct=round(100 * sum(1 for x in s if abs(x) < 33) / n, 2))
    hop = r["sample_rate"] // 20
    frames = [s[i:i + hop] for i in range(0, n - hop + 1, hop)]
    e = sorted(math.sqrt(sum(x * x for x in f) / len(f)) for f in frames) if frames else []
    if e and e[len(e) // 10] > 0:
        r["frame_energy_p90_over_p10_db"] = round(20 * math.log10(e[len(e) * 9 // 10] / e[len(e) // 10]), 2)
    else:
        r["frame_energy_p90_over_p10_db"] = None
    if peak == 0:
        r["verdict"] = "DIGITAL_SILENCE (all samples zero - capture pipeline delivered no data)"
    elif (r["frame_energy_p90_over_p10_db"] or 0) > 15:
        r["verdict"] = "SIGNAL_WITH_DYNAMICS (consistent with speech; confirm by listening)"
    else:
        r["verdict"] = "STEADY_SIGNAL (noise floor only; no evidence of speech)"
    return r


if __name__ == "__main__":
    out, paths = sys.argv[1], sys.argv[2:]
    res = [analyze(p) for p in paths]
    with open(out, "w") as f:
        json.dump(res, f, indent=2)
    for x in res:
        print(f'{x["file"].split("/")[-1]:<40} valid={x.get("valid_pcm_wav")} rate={x.get("sample_rate")} '
              f'dur={x.get("duration_s")}s peak={x.get("peak")} zero%={x.get("exact_zero_pct")} -> {x.get("verdict")}')
