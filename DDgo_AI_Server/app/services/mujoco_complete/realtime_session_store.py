from __future__ import annotations

import json
import shutil
import threading
import uuid
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SERVER_ROOT = Path(__file__).resolve().parents[3]
REALTIME_SESSION_ROOT = SERVER_ROOT / "tmp" / "realtime_sessions"


class RealtimeSessionNotFoundError(FileNotFoundError):
    pass


class RealtimeSessionStateError(ValueError):
    pass


class MujocoRealtimeSessionStore:
    def __init__(self) -> None:
        self._lock = threading.RLock()
        self.root = REALTIME_SESSION_ROOT

    def start_session(
        self,
        *,
        user_body_json: dict[str, Any],
        video_metadata: dict[str, Any],
        top_k_crux: int,
        frame_step: int,
        mode: str | None = None,
    ) -> dict[str, Any]:
        if not isinstance(user_body_json, dict) or not user_body_json:
            raise RealtimeSessionStateError("user_body_json is required.")
        if not isinstance(video_metadata, dict) or not video_metadata:
            raise RealtimeSessionStateError("video_metadata is required.")

        session_id = uuid.uuid4().hex
        now = self._now_iso()
        session_dir = self._session_dir(session_id)
        session_dir.mkdir(parents=True, exist_ok=False)

        session = {
            "session_id": session_id,
            "status": "ACTIVE",
            "created_at": now,
            "updated_at": now,
            "analysis_mode": self._normalize_mode(mode),
            "user_body_json": deepcopy(user_body_json),
            "video_metadata": self._normalize_video_metadata(video_metadata, processed_frames=0),
            "top_k_crux": int(top_k_crux),
            "frame_step": max(1, int(frame_step)),
            "holds_json": None,
            "has_holds": False,
            "frame_count": 0,
            "last_frame_index": None,
            "pose_chunk_count": 0,
            "finalized_at": None,
            "final_result_path": None,
        }
        self._write_session(session_id, session)
        self._write_text(self._pose_frames_path(session_id), "")
        return session

    def append_pose_frames(
        self,
        session_id: str,
        frames: list[dict[str, Any]],
    ) -> tuple[dict[str, Any], int]:
        if not frames:
            return self.get_session(session_id), 0

        with self._lock:
            session = self.get_session(session_id)
            self._ensure_active(session)

            accepted_frames: list[dict[str, Any]] = []
            last_frame_index = self._optional_int(session.get("last_frame_index"))

            for frame in sorted(frames, key=self._frame_sort_key):
                normalized_frame = self._normalize_frame(frame)
                if normalized_frame is None:
                    continue
                frame_index = int(normalized_frame["frame_index"])
                if last_frame_index is not None and frame_index <= last_frame_index:
                    continue
                accepted_frames.append(normalized_frame)
                last_frame_index = frame_index

            if not accepted_frames:
                return self.get_session(session_id), 0

            pose_path = self._pose_frames_path(session_id)
            with pose_path.open("a", encoding="utf-8") as handle:
                for frame in accepted_frames:
                    handle.write(json.dumps(frame, ensure_ascii=False))
                    handle.write("\n")

            session["frame_count"] = int(session.get("frame_count", 0)) + len(accepted_frames)
            session["pose_chunk_count"] = int(session.get("pose_chunk_count", 0)) + 1
            session["last_frame_index"] = last_frame_index
            session["updated_at"] = self._now_iso()
            session["video_metadata"] = self._normalize_video_metadata(
                session.get("video_metadata") or {},
                processed_frames=int(session["frame_count"]),
            )
            self._write_session(session_id, session)
            return session, len(accepted_frames)

    def attach_context(
        self,
        session_id: str,
        holds_json: dict[str, Any],
    ) -> dict[str, Any]:
        if not isinstance(holds_json, dict) or not holds_json:
            raise RealtimeSessionStateError("holds_json is required.")

        with self._lock:
            session = self.get_session(session_id)
            self._ensure_active(session)
            session["holds_json"] = deepcopy(holds_json)
            session["has_holds"] = True
            session["updated_at"] = self._now_iso()
            self._write_session(session_id, session)
            return session

    def build_analysis_payload(self, session_id: str) -> dict[str, Any]:
        session = self.get_session(session_id)
        frames = self._read_pose_frames(session_id)
        if not frames:
            raise RealtimeSessionStateError("No pose frames were recorded for this session.")
        if not session.get("has_holds") or not isinstance(session.get("holds_json"), dict):
            raise RealtimeSessionStateError("holds_json must be attached before finalize.")

        pose_payload = {
            "source": {
                "uri": f"realtime-session://{session_id}",
                "video_uri": f"realtime-session://{session_id}",
                "generator": "MujocoRealtimeSessionStore",
                "exported_at": session.get("created_at"),
                "session_id": session_id,
                "mode": session.get("analysis_mode"),
            },
            "video_metadata": self._normalize_video_metadata(
                session.get("video_metadata") or {},
                processed_frames=len(frames),
            ),
            "frames": frames,
        }
        return {
            "session": session,
            "holds_json": deepcopy(session["holds_json"]),
            "user_body_json": deepcopy(session["user_body_json"]),
            "pose_payload": pose_payload,
            "top_k_crux": int(session.get("top_k_crux", 3)),
            "frame_step": max(1, int(session.get("frame_step", 1))),
            "mode": self._normalize_mode(session.get("analysis_mode")),
        }

    def mark_finalized(
        self,
        session_id: str,
        *,
        final_result: dict[str, Any],
    ) -> dict[str, Any]:
        with self._lock:
            session = self.get_session(session_id)
            session["status"] = "FINALIZED"
            session["finalized_at"] = self._now_iso()
            session["updated_at"] = session["finalized_at"]
            result_path = self._final_result_path(session_id)
            self._write_json(result_path, final_result)
            session["final_result_path"] = str(result_path)
            self._write_session(session_id, session)
            return session

    def load_final_result(self, session_id: str) -> dict[str, Any] | None:
        result_path = self._final_result_path(session_id)
        if not result_path.exists():
            return None
        return json.loads(result_path.read_text(encoding="utf-8"))

    def delete_session(self, session_id: str) -> None:
        with self._lock:
            session_dir = self._session_dir(session_id)
            if not session_dir.exists():
                raise RealtimeSessionNotFoundError(f"Session {session_id} not found.")
            shutil.rmtree(session_dir)

    def get_session(self, session_id: str) -> dict[str, Any]:
        session_path = self._session_path(session_id)
        if not session_path.exists():
            raise RealtimeSessionNotFoundError(f"Session {session_id} not found.")
        return json.loads(session_path.read_text(encoding="utf-8"))

    def _read_pose_frames(self, session_id: str) -> list[dict[str, Any]]:
        pose_path = self._pose_frames_path(session_id)
        if not pose_path.exists():
            raise RealtimeSessionNotFoundError(f"Session {session_id} not found.")
        frames: list[dict[str, Any]] = []
        with pose_path.open("r", encoding="utf-8") as handle:
            for line in handle:
                stripped = line.strip()
                if not stripped:
                    continue
                frames.append(json.loads(stripped))
        return frames

    def _normalize_frame(self, frame: dict[str, Any]) -> dict[str, Any] | None:
        if not isinstance(frame, dict):
            raise RealtimeSessionStateError("Each frame must be a JSON object.")

        pose_detected = bool(frame.get("pose_detected"))
        if not pose_detected:
            return None

        pose_landmarks = frame.get("pose_landmarks")
        pose_world_landmarks = frame.get("pose_world_landmarks")
        if not isinstance(pose_landmarks, list) or not isinstance(pose_world_landmarks, list):
            raise RealtimeSessionStateError("Detected frames must include pose_landmarks and pose_world_landmarks.")

        frame_index = frame.get("frame_index")
        timestamp_ms = frame.get("timestamp_ms")
        if frame_index is None or timestamp_ms is None:
            raise RealtimeSessionStateError("Detected frames must include frame_index and timestamp_ms.")

        normalized_frame = {
            "frame_index": int(frame_index),
            "timestamp_ms": int(timestamp_ms),
            "pose_detected": True,
            "pose_landmarks": deepcopy(pose_landmarks),
            "pose_world_landmarks": deepcopy(pose_world_landmarks),
        }
        return normalized_frame

    def _normalize_video_metadata(
        self,
        metadata: dict[str, Any],
        *,
        processed_frames: int,
    ) -> dict[str, Any]:
        normalized = deepcopy(metadata)
        normalized["processed_frames"] = int(processed_frames)
        normalized["frame_step"] = max(1, int(normalized.get("frame_step", 1)))
        if normalized.get("total_frames") is None:
            normalized["total_frames"] = int(processed_frames)
        return normalized

    def _ensure_active(self, session: dict[str, Any]) -> None:
        status = str(session.get("status", "")).upper()
        if status != "ACTIVE":
            raise RealtimeSessionStateError(f"Session is not active. Current status: {status}")

    def _normalize_mode(self, mode: str | None) -> str:
        normalized = str(mode or "physics").lower()
        if normalized not in {"fast", "physics"}:
            return "physics"
        return normalized

    def _optional_int(self, value: Any) -> int | None:
        if value is None:
            return None
        try:
            return int(value)
        except (TypeError, ValueError):
            return None

    def _frame_sort_key(self, frame: dict[str, Any]) -> int:
        if not isinstance(frame, dict):
            return -1
        try:
            return int(frame.get("frame_index", -1))
        except (TypeError, ValueError):
            return -1

    def _session_dir(self, session_id: str) -> Path:
        return self.root / session_id

    def _session_path(self, session_id: str) -> Path:
        return self._session_dir(session_id) / "session.json"

    def _pose_frames_path(self, session_id: str) -> Path:
        return self._session_dir(session_id) / "pose_frames.jsonl"

    def _final_result_path(self, session_id: str) -> Path:
        return self._session_dir(session_id) / "final_result.json"

    def _write_session(self, session_id: str, session: dict[str, Any]) -> None:
        self._write_json(self._session_path(session_id), session)

    def _write_json(self, path: Path, payload: dict[str, Any]) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        temp_path = path.with_suffix(path.suffix + ".tmp")
        temp_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        temp_path.replace(path)

    def _write_text(self, path: Path, content: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def _now_iso(self) -> str:
        return datetime.now(timezone.utc).isoformat()


realtime_session_store = MujocoRealtimeSessionStore()
