"""Print one line per result JSON (one JSON object per input line)."""
import json
import sys

for line in sys.stdin:
    line = line.strip()
    if not line.startswith("{"):
        continue
    j = json.loads(line)
    mics = [m["id"] for m in j["active_microphones"]]
    print(f'{j["tag"]:<30} routed={j["routed_device"]:<24} active={mics} rate={j["sample_rate"]} '
          f'rms={j["rms_dbfs"]:.1f}dBFS peak={j["peak"]} silent%={j["near_silent_pct"]:.1f} err={j["read_errors"]}')
