from __future__ import annotations

import argparse
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

import cv2
import mediapipe as mp

ROOT = Path(__file__).resolve().parent
CUSTOM_SKELETON_ROOT = ROOT.parent / "custom_skeleton_verify"
sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))

from mediapipe_custom_skeleton_verify import make_landmarker  # noqa: E402

from hold_contact_state import HoldContactTracker, compute_contact_points_px, load_hold_detections


def evaluate_video(
    input_video: Path,
    task_model: Path,
    detections_json: Path,
) -> dict[str, object]:
    hold_payload = load_hold_detections(detections_json)
    tracker = HoldContactTracker(hold_payload["holds"])

    cap = cv2.VideoCapture(str(input_video))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {input_video}")

    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
    frame_count = int(cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)

    landmarker = make_landmarker(task_model)

    frame_states: list[dict[str, object]] = []
    state_counts: dict[str, Counter] = defaultdict(Counter)
    hold_usage: dict[str, Counter] = defaultdict(Counter)

    frame_idx = 0
    while True:
        ok, frame_bgr = cap.read()
        if not ok:
            break

        timestamp_ms = int(round(frame_idx * 1000.0 / max(fps, 1.0)))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)

        pose_landmarks = result.pose_landmarks[0] if result.pose_landmarks else None
        contact_points = compute_contact_points_px(pose_landmarks, frame_width, frame_height)
        limb_states = tracker.update_frame(contact_points, timestamp_ms)

        for limb_name, payload in limb_states.items():
            state_counts[limb_name][payload["state"]] += 1
            if payload["active_hold_id"] is not None:
                hold_usage[limb_name][int(payload["active_hold_id"])] += 1

        frame_states.append(
            {
                "frame_index": frame_idx,
                "timestamp_ms": timestamp_ms,
                "detected": pose_landmarks is not None,
                "limbs": {
                    limb_name: {
                        "state": payload["state"],
                        "active_hold_id": payload["active_hold_id"],
                        "candidate_hold_id": payload["candidate_hold_id"],
                        "distance_px": payload["distance_px"],
                        "speed_px_s": payload["speed_px_s"],
                        "transition": payload["transition"],
                    }
                    for limb_name, payload in limb_states.items()
                },
            }
        )
        frame_idx += 1

    cap.release()
    landmarker.close()

    bbox_extent = hold_payload["bbox_extent_px"]
    video_bbox_scale = {
        "video_width_px": frame_width,
        "video_height_px": frame_height,
        "detection_extent_x_px": bbox_extent[0],
        "detection_extent_y_px": bbox_extent[1],
        "coverage_ratio_x": float(bbox_extent[0] / max(frame_width, 1)),
        "coverage_ratio_y": float(bbox_extent[1] / max(frame_height, 1)),
    }

    summary = {
        limb_name: {
            "state_counts": dict(counter),
            "active_hold_usage_frames": dict(hold_usage[limb_name]),
        }
        for limb_name, counter in state_counts.items()
    }

    return {
        "video": str(input_video.resolve()),
        "detections_json": str(detections_json.resolve()),
        "task_model": str(task_model.resolve()),
        "frame_width": frame_width,
        "frame_height": frame_height,
        "fps": fps,
        "frame_count": frame_count,
        "hold_source_file": hold_payload["source_file"],
        "hold_count": hold_payload["detection_count"],
        "detection_video_scale_check": video_bbox_scale,
        "summary": summary,
        "frame_states": frame_states,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate 2D hold contact / grip states from MediaPipe pose and hold detections.")
    parser.add_argument("--input-video", type=Path, default=ROOT.parent / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--detections-json", type=Path, default=ROOT.parent / "detections.json")
    parser.add_argument("--output", type=Path, default=ROOT / "gate2_hold_contact_report.json")
    args = parser.parse_args()

    report = evaluate_video(
        input_video=args.input_video,
        task_model=args.task_model,
        detections_json=args.detections_json,
    )
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    print(f"[OK] Wrote {args.output.resolve()}")


if __name__ == "__main__":
    main()
