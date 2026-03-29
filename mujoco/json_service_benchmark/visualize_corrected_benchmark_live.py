from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from typing import Any

import cv2
import mujoco
import mujoco.viewer
import numpy as np

import run_json_service_benchmark as bench
from evaluate_static_fit import compute_high_step_score, compute_knee_flexion_target
from mediapipe_custom_skeleton_verify import MetricSkeletonMapper
from polygon_hold_contact_state import (
    PolygonHoldContactTracker,
    compute_contact_points_px,
    load_polygon_service_holds,
)
from pose_sequence_correction import correct_pose_sequence_payload


ROOT = Path(__file__).resolve().parent
DEFAULT_XML = ROOT.parent / "custom_articulated_human" / "custom_articulated_human.xml"
DEFAULT_HOLDS_JSON = ROOT / "benchmark_inputs" / "audit_final_10fps" / "holds_polygon.json"
DEFAULT_POSE_JSON = ROOT / "benchmark_inputs" / "audit_final_10fps" / "pose3d_sequence.json"
DEFAULT_USER_BODY_JSON = ROOT / "benchmark_inputs" / "audit_final_10fps" / "user_body.json"
DEFAULT_CACHE_DIR = ROOT / "cache" / "corrected_live"
OVERLAY_WINDOW = "benchmark-live-overlay"
POSE_CONNECTIONS = (
    (0, 1), (1, 2), (2, 3), (3, 7),
    (0, 4), (4, 5), (5, 6), (6, 8),
    (9, 10),
    (11, 12),
    (11, 13), (13, 15), (15, 17), (15, 19), (15, 21),
    (17, 19),
    (12, 14), (14, 16), (16, 18), (16, 20), (16, 22),
    (18, 20),
    (11, 23), (12, 24), (23, 24),
    (23, 25), (24, 26),
    (25, 27), (26, 28),
    (27, 29), (29, 31),
    (28, 30), (30, 32),
    (27, 31), (28, 32),
)

KEY_ESC = 27
KEY_Q = ord("q")
KEY_SPACE = 32
KEY_A = ord("a")
KEY_D = ord("d")

AXIS_ARROW_SPECS = {
    "torso_front": {"rgba": (1.0, 1.0, 1.0, 0.95), "length": 0.22},
    "left_elbow_front": {"rgba": (0.10, 1.0, 0.60, 0.95), "length": 0.12},
    "right_elbow_front": {"rgba": (1.0, 0.45, 0.15, 0.95), "length": 0.12},
    "left_thigh_front": {"rgba": (1.0, 0.90, 0.25, 0.95), "length": 0.18},
    "right_thigh_front": {"rgba": (0.55, 0.65, 1.0, 0.95), "length": 0.18},
    "left_knee_front": {"rgba": (1.0, 0.96, 0.55, 0.95), "length": 0.13},
    "right_knee_front": {"rgba": (0.82, 0.88, 1.0, 0.95), "length": 0.13},
    "left_shin_front": {"rgba": (0.35, 1.0, 0.55, 0.95), "length": 0.16},
    "right_shin_front": {"rgba": (1.0, 0.62, 0.22, 0.95), "length": 0.16},
    "left_foot_front": {"rgba": (0.24, 0.94, 0.64, 0.95), "length": 0.18},
    "right_foot_front": {"rgba": (1.0, 0.70, 0.22, 0.95), "length": 0.18},
}


def draw_text_block(
    image: np.ndarray,
    lines: list[str],
    origin: tuple[int, int],
    color: tuple[int, int, int] = (255, 255, 255),
    line_step: int = 24,
) -> None:
    x, y = origin
    for idx, line in enumerate(lines):
        yy = y + idx * line_step
        cv2.putText(image, line, (x + 1, yy + 1), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 0), 2, cv2.LINE_AA)
        cv2.putText(image, line, (x, yy), cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 1, cv2.LINE_AA)


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    arr = np.asarray(v, dtype=np.float64)
    n = float(np.linalg.norm(arr))
    if n < eps:
        return np.zeros_like(arr)
    return arr / n


def horizontal_signed_angle_deg(vec_a: np.ndarray, vec_b: np.ndarray) -> float:
    a = normalize(np.array([float(vec_a[0]), float(vec_a[1]), 0.0], dtype=np.float64))
    b = normalize(np.array([float(vec_b[0]), float(vec_b[1]), 0.0], dtype=np.float64))
    if float(np.linalg.norm(a)) < 1e-8 or float(np.linalg.norm(b)) < 1e-8:
        return 0.0
    cross_z = float(np.cross(a, b)[2])
    dot = float(np.clip(np.dot(a, b), -1.0, 1.0))
    return float(np.degrees(np.arctan2(cross_z, dot)))


def format_hold_map(active_hold_ids: dict[str, Any]) -> str:
    if not active_hold_ids:
        return "-"
    parts: list[str] = []
    for limb_name in ("left_hand", "right_hand", "left_foot", "right_foot"):
        hold_id = active_hold_ids.get(limb_name)
        if hold_id is not None:
            parts.append(f"{limb_name}={hold_id}")
    return ", ".join(parts) if parts else "-"


def build_overlay(frame_bgr: np.ndarray, frame: dict[str, Any]) -> np.ndarray:
    overlay = frame_bgr.copy()
    panel = overlay.copy()
    cv2.rectangle(panel, (10, 10), (overlay.shape[1] - 10, 300), (18, 18, 18), -1)
    overlay = cv2.addWeighted(panel, 0.35, overlay, 0.65, 0.0)

    support = frame.get("support_stability", {}) or {}
    limb_states = frame.get("limb_states", {}) or {}
    left_foot = limb_states.get("left_foot", {}) or {}
    right_foot = limb_states.get("right_foot", {}) or {}
    alignment = frame.get("debug_alignment", {}) or {}
    high_step = frame.get("debug_high_step", {}) or {}

    lines = [
        f"frame={frame.get('frame_index')}  ts={frame.get('timestamp_ms')}ms",
        f"pose_mode(자세 처리 상태)={frame.get('pose_mode', '-')}",
        f"freeze_reason(동결 이유)={frame.get('freeze_reason', '-') or '-'}",
        f"phase(동작 구간)={frame.get('phase', '-')}, confidence(신뢰도)={frame.get('analysis_confidence', '-')}",
        f"target_jump_mean(목표 점프 평균)={float(frame.get('target_jump_mean_m') or 0.0):.4f}m",
        f"target_jump_max(목표 점프 최대)={float(frame.get('target_jump_max_m') or 0.0):.4f}m",
        f"support(지지 형태)={support.get('support_type', '-')}, inside(지지영역 내부)={support.get('inside_support', '-')}",
        f"margin(안정 여유)={float(support.get('stability_margin_m') or 0.0) * 100.0:.2f}cm",
        f"contact_status(반력 계산 상태)={frame.get('contact_force_status', '-')}, residual(오차 비율)={float(frame.get('contact_force_relative_residual') or 0.0):.3f}",
        f"active_holds(활성 홀드)={format_hold_map(frame.get('active_hold_ids', {}) or {})}",
        f"left_foot STEP(왼발 디딤)={left_foot.get('state', '-')}, presence={left_foot.get('contact_presence_confidence', '-')}, identity={left_foot.get('hold_identity_confidence', '-')}",
        f"right_foot STEP(오른발 디딤)={right_foot.get('state', '-')}, presence={right_foot.get('contact_presence_confidence', '-')}, identity={right_foot.get('hold_identity_confidence', '-')}",
        f"left knee-foot yaw(왼무릎-왼발 정면 각도차)={float(alignment.get('left_knee_foot_yaw_deg') or 0.0):.1f}deg",
        f"right knee-foot yaw(오른무릎-오른발 정면 각도차)={float(alignment.get('right_knee_foot_yaw_deg') or 0.0):.1f}deg",
    ]
    lines.append(
        f"L high-step(?쇰컻 high-step)={float(high_step.get('left_high_step_score') or 0.0):.2f}, "
        f"target knee={float(high_step.get('left_target_knee_flex_deg') or 0.0):.1f}deg, "
        f"fit knee={float(high_step.get('left_fit_knee_flex_deg') or 0.0):.1f}deg"
    )
    lines.append(
        f"R high-step(?ㅻⅨ諛?high-step)={float(high_step.get('right_high_step_score') or 0.0):.2f}, "
        f"target knee={float(high_step.get('right_target_knee_flex_deg') or 0.0):.1f}deg, "
        f"fit knee={float(high_step.get('right_fit_knee_flex_deg') or 0.0):.1f}deg"
    )
    draw_text_block(overlay, lines, (24, 36), color=(230, 245, 255), line_step=20)
    return overlay


def draw_pose_skeleton(
    image: np.ndarray,
    pose_landmarks: list[dict[str, Any]] | None,
    color: tuple[int, int, int] = (0, 255, 0),
) -> None:
    if not pose_landmarks:
        return
    height, width = image.shape[:2]
    points: list[tuple[int, int] | None] = []
    for item in pose_landmarks:
        x = item.get("x")
        y = item.get("y")
        if x is None or y is None:
            points.append(None)
            continue
        px = int(round(float(x) * width))
        py = int(round(float(y) * height))
        points.append((px, py))

    for start_idx, end_idx in POSE_CONNECTIONS:
        if start_idx >= len(points) or end_idx >= len(points):
            continue
        p0 = points[start_idx]
        p1 = points[end_idx]
        if p0 is None or p1 is None:
            continue
        cv2.line(image, p0, p1, color, 2, cv2.LINE_AA)

    for point in points:
        if point is None:
            continue
        cv2.circle(image, point, 3, color, -1, cv2.LINE_AA)


def build_viewer_camera(model: mujoco.MjModel) -> mujoco.MjvCamera:
    camera = mujoco.MjvCamera()
    mujoco.mjv_defaultCamera(camera)
    camera.type = mujoco.mjtCamera.mjCAMERA_FREE
    camera.lookat[:] = np.array([0.15, -0.2, 1.0], dtype=np.float64)
    camera.distance = 3.2 * float(model.stat.extent)
    camera.azimuth = 145.0
    camera.elevation = -18.0
    return camera


def add_arrow_geom(
    scene: mujoco.MjvScene,
    start: np.ndarray,
    direction: np.ndarray,
    length: float,
    rgba: tuple[float, float, float, float],
) -> None:
    if scene.ngeom >= scene.maxgeom:
        return
    direction = normalize(direction)
    if float(np.linalg.norm(direction)) < 1e-8:
        return
    end = np.asarray(start, dtype=np.float64) + direction * float(length)
    geom = scene.geoms[scene.ngeom]
    mujoco.mjv_initGeom(
        geom,
        mujoco.mjtGeom.mjGEOM_ARROW,
        np.zeros(3, dtype=np.float64),
        np.zeros(3, dtype=np.float64),
        np.eye(3, dtype=np.float64).reshape(-1),
        np.asarray(rgba, dtype=np.float32),
    )
    mujoco.mjv_connector(
        geom,
        mujoco.mjtGeom.mjGEOM_ARROW,
        0.012,
        np.asarray(start, dtype=np.float64),
        np.asarray(end, dtype=np.float64),
    )
    scene.ngeom += 1


def body_forward(data: mujoco.MjData, body_id: int) -> np.ndarray:
    return data.xmat[body_id].reshape(3, 3)[:, 0].copy()


def site_direction(data: mujoco.MjData, from_site_id: int, to_site_id: int) -> np.ndarray:
    return normalize(data.site_xpos[to_site_id] - data.site_xpos[from_site_id])


def build_axis_debug_ids(model: mujoco.MjModel) -> dict[str, int]:
    body_names = {
        "torso": "thorax",
        "left_thigh": "left_thigh",
        "right_thigh": "right_thigh",
        "left_shin": "left_knee",
        "right_shin": "right_knee",
        "left_foot": "left_foot",
        "right_foot": "right_foot",
    }
    site_pairs = {
        "left_elbow": ("left_elbow_site", "left_elbow_pole_site"),
        "right_elbow": ("right_elbow_site", "right_elbow_pole_site"),
        "left_knee": ("left_knee_site", "left_knee_forward_site"),
        "right_knee": ("right_knee_site", "right_knee_forward_site"),
    }
    ids: dict[str, int] = {}
    for key, body_name in body_names.items():
        ids[f"body:{key}"] = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, body_name)
    for key, (from_site, to_site) in site_pairs.items():
        ids[f"site:{key}:from"] = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, from_site)
        ids[f"site:{key}:to"] = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, to_site)
    return ids


def draw_axis_debug(
    viewer: Any,
    model: mujoco.MjModel,
    data: mujoco.MjData,
    axis_ids: dict[str, int],
) -> dict[str, float]:
    scene = viewer.user_scn
    scene.ngeom = 0

    origins = {
        "torso_front": data.xpos[axis_ids["body:torso"]],
        "left_thigh_front": data.xpos[axis_ids["body:left_thigh"]],
        "right_thigh_front": data.xpos[axis_ids["body:right_thigh"]],
        "left_shin_front": data.xpos[axis_ids["body:left_shin"]],
        "right_shin_front": data.xpos[axis_ids["body:right_shin"]],
        "left_foot_front": data.xpos[axis_ids["body:left_foot"]],
        "right_foot_front": data.xpos[axis_ids["body:right_foot"]],
        "left_elbow_front": data.site_xpos[axis_ids["site:left_elbow:from"]],
        "right_elbow_front": data.site_xpos[axis_ids["site:right_elbow:from"]],
        "left_knee_front": data.site_xpos[axis_ids["site:left_knee:from"]],
        "right_knee_front": data.site_xpos[axis_ids["site:right_knee:from"]],
    }
    directions = {
        "torso_front": body_forward(data, axis_ids["body:torso"]),
        "left_thigh_front": body_forward(data, axis_ids["body:left_thigh"]),
        "right_thigh_front": body_forward(data, axis_ids["body:right_thigh"]),
        "left_shin_front": body_forward(data, axis_ids["body:left_shin"]),
        "right_shin_front": body_forward(data, axis_ids["body:right_shin"]),
        "left_foot_front": body_forward(data, axis_ids["body:left_foot"]),
        "right_foot_front": body_forward(data, axis_ids["body:right_foot"]),
        "left_elbow_front": site_direction(data, axis_ids["site:left_elbow:from"], axis_ids["site:left_elbow:to"]),
        "right_elbow_front": site_direction(data, axis_ids["site:right_elbow:from"], axis_ids["site:right_elbow:to"]),
        "left_knee_front": site_direction(data, axis_ids["site:left_knee:from"], axis_ids["site:left_knee:to"]),
        "right_knee_front": site_direction(data, axis_ids["site:right_knee:from"], axis_ids["site:right_knee:to"]),
    }

    for key, spec in AXIS_ARROW_SPECS.items():
        add_arrow_geom(
            scene,
            np.asarray(origins[key], dtype=np.float64),
            np.asarray(directions[key], dtype=np.float64),
            float(spec["length"]),
            tuple(spec["rgba"]),
        )

    return {
        "left_knee_foot_yaw_deg": horizontal_signed_angle_deg(directions["left_knee_front"], directions["left_foot_front"]),
        "right_knee_foot_yaw_deg": horizontal_signed_angle_deg(directions["right_knee_front"], directions["right_foot_front"]),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Live visualize corrected benchmark with MuJoCo viewer + overlay window.")
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--holds-json", type=Path, default=DEFAULT_HOLDS_JSON)
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--user-body-json", type=Path, default=DEFAULT_USER_BODY_JSON)
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE_DIR)
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--sample-count", type=int, default=24)
    parser.add_argument("--ik-iterations", type=int, default=25)
    parser.add_argument("--damping", type=float, default=1e-2)
    parser.add_argument("--smoothing-window", type=int, default=5)
    parser.add_argument("--top-k-joints", type=int, default=8)
    parser.add_argument("--fit-frame-step", type=int, default=1)
    parser.add_argument("--retry-high-confidence-only", action="store_true", default=True)
    parser.add_argument("--retry-all-frames", dest="retry_high_confidence_only", action="store_false")
    parser.add_argument("--start-frame", type=int, default=0)
    parser.add_argument("--max-frames", type=int, default=0)
    parser.add_argument("--pause-at-start", action="store_true")
    parser.add_argument("--overlay-scale", type=float, default=0.6)
    parser.add_argument("--start-at-first-fitted", action="store_true", default=True)
    parser.add_argument("--start-at-zero", dest="start_at_first_fitted", action="store_false")
    parser.add_argument("--fitted-only", action="store_true")
    args = parser.parse_args()

    raw_pose_payload = json.loads(args.pose_json.read_text(encoding="utf-8"))
    user_body_payload = json.loads(args.user_body_json.read_text(encoding="utf-8"))
    raw_frames_by_index = {
        int(frame["frame_index"]): frame for frame in raw_pose_payload.get("frames", [])
    }
    mapper = MetricSkeletonMapper(bench.calibration_from_user_body(user_body_payload))
    corrected_payload = correct_pose_sequence_payload(
        pose_payload=raw_pose_payload,
        user_body_payload=user_body_payload,
        preserve_raw_copy=True,
    )
    corrected_frames_by_index = {
        int(frame["frame_index"]): frame for frame in corrected_payload.get("frames", [])
    }

    bench.load_service_holds = load_polygon_service_holds
    bench.HoldContactTracker = PolygonHoldContactTracker
    bench.compute_contact_points_px = compute_contact_points_px

    report = bench.evaluate_from_json_inputs(
        xml_path=args.xml,
        holds_json=args.holds_json,
        pose_json=args.pose_json,
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
        pose_payload_override=corrected_payload,
        user_body_payload_override=user_body_payload,
        keep_qpos=True,
    )

    video_path_value = (
        corrected_payload.get("video_metadata", {}).get("video_path")
        or corrected_payload.get("source", {}).get("video_path")
        or raw_pose_payload.get("video_metadata", {}).get("video_path")
        or raw_pose_payload.get("source", {}).get("video_path")
    )
    if not video_path_value:
        raise FileNotFoundError("Unable to resolve source.video_path from pose payload")
    video_path = Path(str(video_path_value))
    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {video_path}")

    fps = float(cap.get(cv2.CAP_PROP_FPS) or 10.0)
    frames: list[dict[str, Any]] = report["frames"]
    if bool(args.fitted_only):
        frames = [
            frame
            for frame in frames
            if str(frame.get("pose_mode")) in ("fitted", "interpolated")
        ]
    if args.start_frame > 0:
        frames = [frame for frame in frames if int(frame["frame_index"]) >= int(args.start_frame)]
    if args.max_frames > 0:
        frames = frames[: int(args.max_frames)]
    if not frames:
        raise RuntimeError("No frames available to visualize")

    if args.start_frame <= 0 and bool(args.start_at_first_fitted):
        first_good_idx = next(
            (
                idx
                for idx, frame in enumerate(frames)
                if str(frame.get("pose_mode")) in ("fitted", "interpolated")
            ),
            0,
        )
    else:
        first_good_idx = 0

    personalized_xml = Path(report["inputs"]["personalized_xml"])
    model = mujoco.MjModel.from_xml_path(str(personalized_xml.resolve()))
    data = mujoco.MjData(model)
    axis_ids = build_axis_debug_ids(model)

    cv2.namedWindow(OVERLAY_WINDOW, cv2.WINDOW_NORMAL)
    paused = bool(args.pause_at_start)
    current_idx = int(first_good_idx)
    frame_interval = 1.0 / max(fps, 1.0)
    knee_left_qpos_adr = int(model.jnt_qposadr[mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_JOINT, "knee_left")])
    knee_right_qpos_adr = int(model.jnt_qposadr[mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_JOINT, "knee_right")])

    try:
        with mujoco.viewer.launch_passive(model, data) as viewer:
            camera = build_viewer_camera(model)
            viewer.cam.type = camera.type
            viewer.cam.lookat[:] = camera.lookat
            viewer.cam.distance = camera.distance
            viewer.cam.azimuth = camera.azimuth
            viewer.cam.elevation = camera.elevation
            last_tick = time.perf_counter()

            while viewer.is_running():
                frame = frames[current_idx]
                frame_index = int(frame["frame_index"])
                qpos = np.asarray(frame["qpos"], dtype=np.float64)
                data.qpos[:] = qpos
                data.qvel[:] = 0.0
                data.qacc[:] = 0.0
                mujoco.mj_forward(model, data)
                frame["debug_alignment"] = draw_axis_debug(viewer, model, data, axis_ids)
                raw_frame = raw_frames_by_index.get(frame_index, {})
                corrected_frame = corrected_frames_by_index.get(frame_index, {})
                corrected_world = corrected_frame.get("pose_world_landmarks")
                if corrected_world:
                    class Landmark:
                        def __init__(self, x: float, y: float, z: float) -> None:
                            self.x = x
                            self.y = y
                            self.z = z
                    world_landmarks = [
                        Landmark(float(item["x"]), float(item["y"]), float(item.get("z", 0.0)))
                        for item in corrected_world
                    ]
                    target_points = mapper.map_frame(world_landmarks)
                    frame["debug_high_step"] = {
                        "left_high_step_score": float(compute_high_step_score(target_points, "left")),
                        "right_high_step_score": float(compute_high_step_score(target_points, "right")),
                        "left_target_knee_flex_deg": float(np.degrees(abs(compute_knee_flexion_target(target_points, "left")))),
                        "right_target_knee_flex_deg": float(np.degrees(abs(compute_knee_flexion_target(target_points, "right")))),
                        "left_fit_knee_flex_deg": float(abs(np.degrees(data.qpos[knee_left_qpos_adr]))),
                        "right_fit_knee_flex_deg": float(abs(np.degrees(data.qpos[knee_right_qpos_adr]))),
                    }
                else:
                    frame["debug_high_step"] = {}
                viewer.sync()

                cap.set(cv2.CAP_PROP_POS_FRAMES, frame_index)
                ok, video_frame = cap.read()
                if ok:
                    overlay = build_overlay(video_frame, frame)
                    draw_pose_skeleton(
                        overlay,
                        raw_frame.get("pose_landmarks"),
                        color=(0, 255, 0),
                    )
                    if float(args.overlay_scale) != 1.0:
                        overlay = cv2.resize(
                            overlay,
                            None,
                            fx=float(args.overlay_scale),
                            fy=float(args.overlay_scale),
                            interpolation=cv2.INTER_AREA,
                        )
                    cv2.imshow(OVERLAY_WINDOW, overlay)

                key = cv2.waitKey(1) & 0xFF
                if key in (KEY_ESC, KEY_Q):
                    break
                if key == KEY_SPACE:
                    paused = not paused
                elif key == KEY_A:
                    current_idx = max(0, current_idx - 1)
                    paused = True
                elif key == KEY_D:
                    current_idx = min(len(frames) - 1, current_idx + 1)
                    paused = True

                if paused:
                    last_tick = time.perf_counter()
                    continue

                now = time.perf_counter()
                if now - last_tick >= frame_interval:
                    current_idx += 1
                    last_tick = now
                    if current_idx >= len(frames):
                        break
    finally:
        cap.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    main()
