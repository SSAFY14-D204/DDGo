from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import cv2

from polygon_hold_contact_state import load_polygon_service_holds
from prepare_benchmark_inputs import (
    DEFAULT_OUTPUT_DIR,
    DEFAULT_TASK_MODEL,
    DEFAULT_TPOSE_IMAGE,
    DEFAULT_VIDEO,
    build_pose_sequence_payload,
    build_user_body_payload,
)


DEFAULT_SEG_DETECTIONS = Path(__file__).resolve().parent.parent / "seg_detect.json"


def build_polygon_holds_payload(seg_detections_json: Path, video_path: Path) -> dict[str, Any]:
    hold_payload = load_polygon_service_holds(seg_detections_json)

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")
    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    cap.release()

    holds: list[dict[str, Any]] = []
    for hold in hold_payload["holds"]:
        polygon_px = [
            {"x": float(point[0]), "y": float(point[1])}
            for point in hold.polygon_px.tolist()
        ]
        holds.append(
            {
                "hold_id": int(hold.hold_id),
                "bbox_px": {
                    "x1": float(hold.x1),
                    "y1": float(hold.y1),
                    "x2": float(hold.x2),
                    "y2": float(hold.y2),
                },
                "center_px": {
                    "x": float(hold.cx_px),
                    "y": float(hold.cy_px),
                },
                "radius_px": float(hold.radius_px),
                "polygon_px": polygon_px,
                "confidence": float(hold.confidence),
            }
        )

    return {
        "schema_version": "1.1.0",
        "source": {
            "type": "segmentation_polygon_json",
            "path": str(seg_detections_json.resolve()),
            "legacy_source_file": hold_payload.get("source_file"),
        },
        "video_metadata": {
            "video_path": str(video_path.resolve()),
            "frame_width": frame_width,
            "frame_height": frame_height,
        },
        "holds": holds,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Prepare polygon-aware JSON inputs for MuJoCo service benchmark.")
    parser.add_argument("--input-video", type=Path, default=DEFAULT_VIDEO)
    parser.add_argument("--seg-detections-json", type=Path, default=DEFAULT_SEG_DETECTIONS)
    parser.add_argument("--tpose-image", type=Path, default=DEFAULT_TPOSE_IMAGE)
    parser.add_argument("--task-model", type=Path, default=DEFAULT_TASK_MODEL)
    parser.add_argument("--height-m", type=float, default=1.75)
    parser.add_argument("--weight-kg", type=float, default=80.0)
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--max-frames", type=int, default=None)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR / "polygon")
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)

    holds_payload = build_polygon_holds_payload(args.seg_detections_json, args.input_video)
    pose_payload = build_pose_sequence_payload(
        video_path=args.input_video,
        task_model=args.task_model,
        frame_step=args.frame_step,
        max_frames=args.max_frames,
    )
    user_body_payload = build_user_body_payload(
        image_path=args.tpose_image,
        task_model=args.task_model,
        height_m=args.height_m,
        weight_kg=args.weight_kg,
    )

    holds_path = args.output_dir / "holds_polygon.json"
    pose_path = args.output_dir / "pose3d_sequence.json"
    user_path = args.output_dir / "user_body.json"
    manifest_path = args.output_dir / "benchmark_input_manifest.json"

    holds_path.write_text(json.dumps(holds_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    pose_path.write_text(json.dumps(pose_payload, ensure_ascii=False, indent=2), encoding="utf-8")
    user_path.write_text(json.dumps(user_body_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    manifest = {
        "mode": "polygon_service_benchmark_inputs",
        "input_video": str(args.input_video.resolve()),
        "seg_detections_json": str(args.seg_detections_json.resolve()),
        "tpose_image": str(args.tpose_image.resolve()),
        "task_model": str(args.task_model.resolve()),
        "frame_step": int(args.frame_step),
        "max_frames": args.max_frames,
        "height_m": float(args.height_m),
        "weight_kg": float(args.weight_kg),
        "outputs": {
            "holds_json": str(holds_path.resolve()),
            "pose_json": str(pose_path.resolve()),
            "user_body_json": str(user_path.resolve()),
        },
    }
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"[OK] Wrote {holds_path.resolve()}")
    print(f"[OK] Wrote {pose_path.resolve()}")
    print(f"[OK] Wrote {user_path.resolve()}")
    print(f"[OK] Wrote {manifest_path.resolve()}")


if __name__ == "__main__":
    main()
