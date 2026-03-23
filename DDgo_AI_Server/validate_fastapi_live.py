from __future__ import annotations

import json
import socket
import threading
import time
import urllib.error
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


def decode_json_payload(text: str) -> object:
    if not text:
        return {}
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return {"raw": text}


def request_json(url: str, payload: dict[str, object] | None = None) -> tuple[int, object]:
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json"} if payload is not None else {},
        method="POST" if payload is not None else "GET",
    )
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            return response.status, decode_json_payload(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        return exc.code, decode_json_payload(exc.read().decode("utf-8"))


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
    health_status, health_body = request_json(f"http://{HOST}:{PORT}/health")
    openapi_status, openapi_body = request_json(f"http://{HOST}:{PORT}/openapi.json")
    fast_status, fast_body = request_json(f"http://{HOST}:{PORT}/api/v1/mujoco-complete/analyze/fast", payload)
    physics_status, physics_body = request_json(f"http://{HOST}:{PORT}/api/v1/mujoco-complete/analyze/physics", payload)
    fast_probe_status, fast_probe_body = request_json(
        f"http://{HOST}:{PORT}/api/v1/mujoco-complete/analyze/fast",
        {},
    )
    realtime_start_probe_status, realtime_start_probe_body = request_json(
        f"http://{HOST}:{PORT}/api/v1/mujoco-complete/session/start",
        {},
    )
    openapi_paths = openapi_body.get("paths", {}) if isinstance(openapi_body, dict) else {}
    health_payload = health_body if isinstance(health_body, dict) else {"raw": health_body}

    report = {
        "docs_status": docs_status,
        "docs_contains_swagger": "Swagger UI" in docs_body,
        "health_status": health_status,
        "health_body": health_payload,
        "openapi_status": openapi_status,
        "openapi_contains_analyze_fast": "/api/v1/mujoco-complete/analyze/fast" in openapi_paths,
        "openapi_contains_realtime_session_start": "/api/v1/mujoco-complete/session/start" in openapi_paths,
        "fast_status": fast_status,
        "fast_mode": fast_body.get("mode") if isinstance(fast_body, dict) else None,
        "fast_timings_s": fast_body.get("timings_s") if isinstance(fast_body, dict) else None,
        "physics_status": physics_status,
        "physics_mode": physics_body.get("mode") if isinstance(physics_body, dict) else None,
        "physics_timings_s": physics_body.get("timings_s") if isinstance(physics_body, dict) else None,
        "physics_summary": physics_body.get("physics_summary") if isinstance(physics_body, dict) else None,
        "fast_probe_status": fast_probe_status,
        "fast_probe_body": fast_probe_body,
        "realtime_session_start_probe_status": realtime_start_probe_status,
        "realtime_session_start_probe_body": realtime_start_probe_body,
    }
    OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    server.should_exit = True
    thread.join(timeout=10)


if __name__ == "__main__":
    main()
