"""Shared paths for the desktop prototype and exports in the Android repository."""
import os
from pathlib import Path

CLASSIFIER_ROOT = Path(__file__).resolve().parents[1]
TAXONOMY_PATH = CLASSIFIER_ROOT.parent / "app/src/main/assets/incident_taxonomy.json"
LAYA_REVISION = "7b928d828b7b0e022f929d9bd2e44165aa270148"


def laya_directory():
    """Use an explicit model directory, or the pinned Hugging Face snapshot."""
    if os.environ.get("LAYA_MODEL_DIR"):
        return str(Path(os.environ["LAYA_MODEL_DIR"]).expanduser().resolve())
    cache = Path(os.environ.get("HF_HUB_CACHE", str(Path.home() / ".cache/huggingface/hub")))
    return str(cache / "models--convaiinnovations--laya" / "snapshots" / LAYA_REVISION)
