from __future__ import annotations

import json
import socket
import threading
import time
import urllib.request
from pathlib import Path

import uvicorn

from app.main import app

HOST = "127.0.0.1"
PORT = 8010
ROOT = Path(__file__).resolve().parent
OUT = ROOT / "fastapi_live_validation.json"
BENCH_ROOT = ROOT.parent / "mujoco" / "json_service_benchmark" / "benchmark_inputs" / "polygon"


def get_text(url: str) -> tuple[int, str]:
    with urllib.request.urlopen(url, timeout=300) as response:
        return response.status, response.read().decode("utf-8")


def post_json(url: str, payload: dict[str, object]) -> tuple[int, dict[str, object]]:
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=300) as response:
        return response.status, json.loads(response.read().decode("utf-8"))


def main() -> None:
    config = uvicorn.Config(app, host=HOST, port=PORT, log_level="warning")
    server = uvicorn.Server(config)
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()

    started = False
    for _ in range(100):
        try:
            with socket.create_connection((HOST, PORT), timeout=0.2):
                started = True
                break
        except OSError:
            time.sleep(0.1)

    if not started:
        OUT.write_text(
            json.dumps({"error": "uvicorn server did not start"}, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        raise SystemExit(1)

    payload = {
        "holds_json": json.loads((BENCH_ROOT / "holds_polygon.json").read_text(encoding="utf-8")),
        "pose3d_sequence_json": json.loads((BENCH_ROOT / "pose3d_sequence.json").read_text(encoding="utf-8")),
        "user_body_json": json.loads((BENCH_ROOT / "user_body.json").read_text(encoding="utf-8")),
        "top_k_crux": 3,
        "frame_step": 2,
    }

    docs_status, docs_body = get_text(f"http://{HOST}:{PORT}/docs")
    health_status, health_body = get_text(f"http://{HOST}:{PORT}/health")
    fast_status, fast_body = post_json(f"http://{HOST}:{PORT}/api/v1/mujoco-complete/analyze/fast", payload)
    physics_status, physics_body = post_json(f"http://{HOST}:{PORT}/api/v1/mujoco-complete/analyze/physics", payload)

    report = {
        "docs_status": docs_status,
        "docs_contains_swagger": "Swagger UI" in docs_body,
        "health_status": health_status,
        "health_body": json.loads(health_body),
        "fast_status": fast_status,
        "fast_mode": fast_body.get("mode"),
        "fast_timings_s": fast_body.get("timings_s"),
        "physics_status": physics_status,
        "physics_mode": physics_body.get("mode"),
        "physics_timings_s": physics_body.get("timings_s"),
        "physics_summary": physics_body.get("physics_summary"),
    }
    OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    server.should_exit = True
    thread.join(timeout=10)


if __name__ == "__main__":
    main()
