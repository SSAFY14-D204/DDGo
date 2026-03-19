from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import cv2
import mujoco
import numpy as np

import run_json_service_benchmark as bench
from polygon_hold_contact_state import PolygonHoldContactTracker, compute_contact_points_px, load_polygon_service_holds
from pose_sequence_correction import correct_pose_sequence_payload


ROOT = Path(__file__).resolve().parent
DEFAULT_XML = ROOT.parent / "custom_articulated_human" / "custom_articulated_human.xml"
DEFAULT_HOLDS_JSON = ROOT / "benchmark_inputs" / "polygon" / "holds_polygon.json"
DEFAULT_POSE_JSON = ROOT / "benchmark_inputs" / "polygon" / "pose3d_sequence.json"
DEFAULT_USER_BODY_JSON = ROOT / "benchmark_inputs" / "polygon" / "user_body.json"
DEFAULT_CORRECTED_JSON = ROOT / "benchmark_inputs" / "polygon" / "pose3d_sequence_corrected_visualize.json"
DEFAULT_OUTPUT = ROOT / "corrected_benchmark_side_by_side.mp4"
DEFAULT_CACHE_DIR = ROOT / "cache"
MAX_RENDER_WIDTH = 640
MAX_RENDER_HEIGHT = 480


def draw_text_block(
    image: np.ndarray,
    lines: list[str],
    origin: tuple[int, int],
    color: tuple[int, int, int] = (255, 255, 255),
) -> None:
    x, y = origin
    for idx, line in enumerate(lines):
        yy = y + idx * 24
        cv2.putText(image, line, (x + 1, yy + 1), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 0), 2, cv2.LINE_AA)
        cv2.putText(image, line, (x, yy), cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 1, cv2.LINE_AA)


def render_mujoco_frame(
    renderer: mujoco.Renderer,
    model: mujoco.MjModel,
    data: mujoco.MjData,
    qpos: np.ndarray,
    camera: mujoco.MjvCamera,
) -> np.ndarray:
    data.qpos[:] = np.asarray(qpos, dtype=np.float64)
    data.qvel[:] = 0.0
    data.qacc[:] = 0.0
    mujoco.mj_forward(model, data)
    renderer.update_scene(data, camera=camera)
    rgb = renderer.render()
    return cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)


def build_camera(model: mujoco.MjModel) -> mujoco.MjvCamera:
    camera = mujoco.MjvCamera()
    mujoco.mjv_defaultCamera(camera)
    camera.type = mujoco.mjtCamera.mjCAMERA_FREE
    camera.lookat[:] = np.array([0.2, -0.2, 1.0], dtype=np.float64)
    camera.distance = 3.2 * float(model.stat.extent)
    camera.azimuth = 145.0
    camera.elevation = -18.0
    return camera


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--holds-json", type=Path, default=DEFAULT_HOLDS_JSON)
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--user-body-json", type=Path, default=DEFAULT_USER_BODY_JSON)
    parser.add_argument("--corrected-pose-json", type=Path, default=DEFAULT_CORRECTED_JSON)
    parser.add_argument("--output-video", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR)
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--sample-count", type=int, default=24)
    parser.add_argument("--ik-iterations", type=int, default=25)
    parser.add_argument("--damping", type=float, default=1e-3)
    parser.add_argument("--smoothing-window", type=int, default=5)
    parser.add_argument("--top-k-joints", type=int, default=8)
    parser.add_argument("--fit-frame-step", type=int, default=2)
    parser.add_argument("--retry-high-confidence-only", action="store_true", default=True)
    parser.add_argument("--retry-all-frames", dest="retry_high_confidence_only", action="store_false")
    parser.add_argument("--max-frames", type=int, default=0)
    args = parser.parse_args()

    raw_pose_payload = json.loads(args.pose_json.read_text(encoding="utf-8"))
    user_body_payload = json.loads(args.user_body_json.read_text(encoding="utf-8"))
    corrected_payload = correct_pose_sequence_payload(raw_pose_payload, user_body_payload)
    args.corrected_pose_json.parent.mkdir(parents=True, exist_ok=True)
    args.corrected_pose_json.write_text(json.dumps(corrected_payload, ensure_ascii=False, indent=2), encoding="utf-8")

    bench.load_service_holds = load_polygon_service_holds
    bench.HoldContactTracker = PolygonHoldContactTracker
    bench.compute_contact_points_px = compute_contact_points_px

    report = bench.evaluate_from_json_inputs(
        xml_path=args.xml,
        holds_json=args.holds_json,
        pose_json=args.corrected_pose_json,
        user_body_json=args.user_body_json,
        frame_step=args.frame_step,
        sample_count=args.sample_count,
        ik_iterations=args.ik_iterations,
        damping=args.damping,
        smoothing_window=args.smoothing_window,
        top_k_joints=args.top_k_joints,
        cache_dir=args.cache_dir,
        fit_frame_step=args.fit_frame_step,
        retry_high_confidence_only=bool(args.retry_high_confidence_only),
        keep_qpos=True,
    )

    video_path = Path(
        corrected_payload.get("video_metadata", {}).get("video_path")
        or raw_pose_payload.get("video_metadata", {}).get("video_path")
        or ROOT.parent / "video" / "주황.mp4"
    )
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")

    frame_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    fps = float(cap.get(cv2.CAP_PROP_FPS) or 30.0)
    render_scale = min(MAX_RENDER_WIDTH / max(frame_width, 1), MAX_RENDER_HEIGHT / max(frame_height, 1), 1.0)
    render_width = max(1, int(round(frame_width * render_scale)))
    render_height = max(1, int(round(frame_height * render_scale)))

    personalized_xml = Path(report["inputs"]["personalized_xml"])
    model = mujoco.MjModel.from_xml_path(str(personalized_xml.resolve()))
    data = mujoco.MjData(model)
    renderer = mujoco.Renderer(model, height=render_height, width=render_width)
    camera = build_camera(model)

    output_size = (frame_width + render_width, max(frame_height, render_height))
    args.output_video.parent.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(
        str(args.output_video),
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        output_size,
    )

    frames: list[dict[str, Any]] = report["frames"]
    if args.max_frames > 0:
        frames = frames[: args.max_frames]

    try:
        for frame in frames:
            frame_index = int(frame["frame_index"])
            cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
            ok, video_frame = cap.read()
            if not ok:
                break

            qpos = np.asarray(frame["qpos"], dtype=np.float64)
            rendered = render_mujoco_frame(renderer, model, data, qpos, camera)

            left_panel = video_frame.copy()
            right_panel = rendered.copy()

            draw_text_block(
                left_panel,
                [
                    f"frame={frame_index}  ts={frame['timestamp_ms']}ms",
                    f"phase={frame.get('phase', '-')}",
                    f"confidence={frame.get('analysis_confidence', '-')}",
                    f"pose_mode={frame.get('pose_mode', '-')}",
                    f"contacts={','.join(frame.get('active_contact_limbs', [])) or '-'}",
                ],
                (16, 28),
                color=(80, 255, 255),
            )
            draw_text_block(
                right_panel,
                [
                    f"fit_error={float(frame.get('fit_mean_error_m') or 0.0) * 100.0:.2f}cm",
                    f"contact_status={frame.get('contact_force_distribution', {}).get('status', '-')}",
                    f"residual={frame.get('contact_force_distribution', {}).get('relative_residual', 0.0) or 0.0:.3f}",
                    f"support={frame.get('support_stability', {}).get('support_type', '-')}",
                    f"margin={float(frame.get('support_stability', {}).get('stability_margin_m', 0.0)) * 100.0:.2f}cm",
                    f"com_z={float(frame.get('com_position_m', [0.0, 0.0, 0.0])[2]):.3f}m",
                ],
                (16, 28),
                color=(120, 255, 120),
            )

            canvas = np.zeros((output_size[1], output_size[0], 3), dtype=np.uint8)
            canvas[:frame_height, :frame_width] = left_panel
            canvas[:render_height, frame_width : frame_width + render_width] = right_panel
            cv2.line(canvas, (frame_width, 0), (frame_width, output_size[1]), (255, 255, 255), 2)
            writer.write(canvas)
    finally:
        writer.release()
        cap.release()
        renderer.close()


if __name__ == "__main__":
    main()
