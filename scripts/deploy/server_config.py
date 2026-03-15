import json
import os
from pathlib import Path


def load_servers() -> dict:
    raw = os.getenv("SMART_EXAM_SERVERS_JSON")
    if raw:
        return json.loads(raw)

    config_path = Path(__file__).with_name("servers.local.json")
    if config_path.exists():
        return json.loads(config_path.read_text(encoding="utf-8"))

    example = Path(__file__).with_name("servers.example.json").name
    raise RuntimeError(
        f"Missing scripts/deploy/servers.local.json. Copy {example} and fill server credentials."
    )
