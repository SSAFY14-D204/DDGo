from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import cv2
import mediapipe as mp
import mujoco
import mujoco.viewer
import numpy as np

from evaluate_static_fit import AUX_SITE_TARGETS, POLE_TARGETS, SITE_TARGETS, fit_static_pose
from visualize_static_fit import draw_overlay
from mediapipe_custom_skeleton_verify import MetricSkeletonMapper, make_landmarker
from physics_worker import load_calibration_json

ROOT = Path(__file__).resolve().parent
CUSTOM_SKELETON_ROOT = ROOT.parent / "custom_skeleton_verify"
DEFAULT_PERSONALIZED_XML = ROOT / "custom_articulated_human_personalized.xml"
DEFAULT_XML = DEFAULT_PERSONALIZED_XML if DEFAULT_PERSONALIZED_XML.exists() else ROOT / "custom_articulated_human.xml"

OVERLAY_WINDOW = "articulated-fit-overlay"
KEY_ESC = 27
KEY_SPACE = 32
KEY_A = ord("a")
KEY_D = ord("d")
KEY_H = ord("h")
KEY_J = ord("j")
KEY_K = ord("k")
KEY_L = ord("l")
KEY_Q = ord("q")
KEY_LEFT = 2424832
KEY_RIGHT = 2555904
KEY_HOME = 2359296
KEY_END = 2293760

JUMP_KEYS = (
    "pelvis",
    "thorax",
    "left_shoulder",
    "right_shoulder",
    "left_hip",
    "right_hip",
    "left_knee",
    "right_knee",
    "left_ankle",
    "right_ankle",
    "left_foot",
    "right_foot",
    "left_hand",
    "right_hand",
)
MAX_TARGET_JUMP_M = 0.45
MEAN_TARGET_JUMP_M = 0.16
BAD_FIT_MEAN_ERROR_M = 0.28
BAD_FIT_MAX_ERROR_M = 0.80
BAD_FOOT_FORWARD_DOT = -0.05
BAD_KNEE_FOOT_ALIGNMENT_DOT = -0.20


def fetch_frame_at(cap: cv2.VideoCapture, frame_index: int) -> np.ndarray | None:
    cap.set(cv2.CAP_PROP_POS_FRAMES, int(frame_index))
    ok, frame_bgr = cap.read()
    if not ok:
        return None
    return frame_bgr


def clone_bundle(bundle: dict[str, object]) -> dict[str, object]:
    return {
        "qpos": np.asarray(bundle["qpos"], dtype=np.float64).copy(),
        "target_points": {key: value.copy() for key, value in bundle["target_points"].items()},  # type: ignore[index]
        "fitted_sites": {key: value.copy() for key, value in bundle["fitted_sites"].items()},  # type: ignore[index]
        "pose_landmarks_xy": list(bundle["pose_landmarks_xy"]),  # type: ignore[index]
        "fit": dict(bundle["fit"]),  # type: ignore[arg-type]
        "frozen": bool(bundle.get("frozen", False)),
    }


def target_jump_stats(current: dict[str, np.ndarray], previous: dict[str, np.ndarray] | None) -> tuple[float, float]:
    if previous is None:
        return 0.0, 0.0
    diffs = [
        float(np.linalg.norm(np.asarray(current[key], dtype=np.float64) - np.asarray(previous[key], dtype=np.float64)))
        for key in JUMP_KEYS
        if key in current and key in previous
    ]
    if not diffs:
        return 0.0, 0.0
    return float(np.mean(diffs)), float(np.max(diffs))


def has_bad_lower_limb_consistency(fit: dict[str, object]) -> bool:
    consistency = fit.get("lower_limb_consistency")
    if not isinstance(consistency, dict):
        return False
    for side in ("left", "right"):
        payload = consistency.get(side)
        if not isinstance(payload, dict):
            continue
        bend_dot = float(payload.get("bend_alignment_dot", 1.0))
        foot_dot = float(payload.get("foot_vs_pelvis_forward_dot", 1.0))
        bend_norm = float(payload.get("bend_norm", 0.0))
        if foot_dot < BAD_FOOT_FORWARD_DOT:
            return True
        if bend_norm > 0.05 and bend_dot < BAD_KNEE_FOOT_ALIGNMENT_DOT:
            return True
    return False


def main() -> None:
    parser = argparse.ArgumentParser(description="Play articulated human fitting over a full video sequence.")
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    parser.add_argument("--input-video", type=Path, default=ROOT.parent / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=CUSTOM_SKELETON_ROOT / "calibration.json")
    parser.add_argument("--ik-iters", type=int, default=45)
    parser.add_argument("--ik-damping", type=float, default=1e-3)
    parser.add_argument("--show-overlay", action="store_true")
    parser.add_argument("--overlay-output", type=Path)
    parser.add_argument("--no-viewer", action="store_true")
    parser.add_argument("--pause-at-start", action="store_true")
    parser.add_argument("--mp-only-overlay", action="store_true")
    args = parser.parse_args()

    calibration = load_calibration_json(args.calibration_json)
    model = mujoco.MjModel.from_xml_path(str(args.xml.resolve()))
    data = mujoco.MjData(model)
    required_sites = tuple(SITE_TARGETS.keys()) + tuple(POLE_TARGETS.keys()) + tuple(AUX_SITE_TARGETS.keys())
    site_ids = {
        site_name: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, site_name)
        for site_name in required_sites
    }

    process_cap = cv2.VideoCapture(str(args.input_video))
    if not process_cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {args.input_video}")
    display_cap = cv2.VideoCapture(str(args.input_video))
    if not display_cap.isOpened():
        raise FileNotFoundError(f"Unable to open video: {args.input_video}")

    fps = float(process_cap.get(cv2.CAP_PROP_FPS) or 30.0)
    frame_width = int(process_cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 0)
    frame_height = int(process_cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 0)
    frame_count = int(process_cap.get(cv2.CAP_PROP_FRAME_COUNT) or 0)
    writer = None
    if args.overlay_output is not None:
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        writer = cv2.VideoWriter(str(args.overlay_output), fourcc, fps, (frame_width, frame_height))

    landmarker = make_landmarker(args.task_model)
    mapper = MetricSkeletonMapper(calibration)
    prev_qpos = None
    prev_target_points: dict[str, np.ndarray] | None = None
    last_good_bundle: dict[str, object] | None = None
    mean_errors: list[float] = []
    max_errors: list[float] = []
    processed_cache: dict[int, dict[str, object]] = {}
    next_process_idx = 0
    current_display_idx = -1
    paused = bool(args.pause_at_start)
    end_reached = False
    pending_seek_idx: int | None = None

    if args.show_overlay or args.overlay_output is not None:
        cv2.namedWindow(OVERLAY_WINDOW, cv2.WINDOW_NORMAL)

    def process_frame(frame_bgr: np.ndarray, frame_idx: int) -> dict[str, object] | None:
        nonlocal prev_qpos, prev_target_points, last_good_bundle
        timestamp_ms = int(round((frame_idx / max(fps, 1.0)) * 1000.0))
        frame_rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=frame_rgb)
        result = landmarker.detect_for_video(mp_image, timestamp_ms)
        if not result.pose_world_landmarks:
            if last_good_bundle is None:
                return None
            frozen = clone_bundle(last_good_bundle)
            frozen["frozen"] = True
            return frozen

        mapper_snapshot = mapper.snapshot_state()
        target_points = mapper.map_frame(result.pose_world_landmarks[0])
        mean_jump, max_jump = target_jump_stats(target_points, prev_target_points)
        if prev_target_points is not None and (max_jump > MAX_TARGET_JUMP_M or mean_jump > MEAN_TARGET_JUMP_M):
            mapper.restore_state(mapper_snapshot)
            if last_good_bundle is None:
                return None
            frozen = clone_bundle(last_good_bundle)
            frozen["frozen"] = True
            return frozen

        fit = fit_static_pose(
            model=model,
            data=data,
            site_ids=site_ids,
            target_points=target_points,
            seed_qpos=prev_qpos,
            iterations=args.ik_iters,
            damping=args.ik_damping,
        )
        if prev_qpos is not None and (
            float(fit["mean_error_m"]) > BAD_FIT_MEAN_ERROR_M or float(fit["max_error_m"]) > BAD_FIT_MAX_ERROR_M
        ):
            retry = fit_static_pose(
                model=model,
                data=data,
                site_ids=site_ids,
                target_points=target_points,
                seed_qpos=None,
                iterations=args.ik_iters,
                damping=args.ik_damping,
            )
            if float(retry["mean_error_m"]) < float(fit["mean_error_m"]):
                fit = retry

        if has_bad_lower_limb_consistency(fit):
            retry = fit_static_pose(
                model=model,
                data=data,
                site_ids=site_ids,
                target_points=target_points,
                seed_qpos=None,
                iterations=args.ik_iters,
                damping=args.ik_damping,
            )
            if not has_bad_lower_limb_consistency(retry) or float(retry["mean_error_m"]) < float(fit["mean_error_m"]):
                fit = retry

        if (
            float(fit["mean_error_m"]) > BAD_FIT_MEAN_ERROR_M
            or float(fit["max_error_m"]) > BAD_FIT_MAX_ERROR_M
            or has_bad_lower_limb_consistency(fit)
        ):
            mapper.restore_state(mapper_snapshot)
            if last_good_bundle is None:
                prev_qpos = None
                return None
            frozen = clone_bundle(last_good_bundle)
            frozen["frozen"] = True
            return frozen

        prev_qpos = fit["qpos"].copy()
        prev_target_points = {key: value.copy() for key, value in target_points.items()}
        mean_errors.append(float(fit["mean_error_m"]))
        max_errors.append(float(fit["max_error_m"]))

        fitted_sites = {
            target_key: data.site_xpos[site_ids[site_name]].copy()
            for site_name, (target_key, _) in SITE_TARGETS.items()
        }
        mp_xy = [(float(lm.x), float(lm.y)) for lm in result.pose_landmarks[0]]
        bundle = {
            "qpos": fit["qpos"].copy(),
            "target_points": {key: value.copy() for key, value in target_points.items()},
            "fitted_sites": fitted_sites,
            "pose_landmarks_xy": mp_xy,
            "fit": fit,
            "frozen": False,
        }
        last_good_bundle = clone_bundle(bundle)
        return bundle

    def ensure_processed(frame_idx: int) -> bool:
        nonlocal next_process_idx, end_reached
        if frame_idx < 0:
            return False
        while next_process_idx <= frame_idx and not end_reached:
            ok, frame_bgr = process_cap.read()
            if not ok:
                end_reached = True
                break
            bundle = process_frame(frame_bgr, next_process_idx)
            if bundle is not None:
                processed_cache[next_process_idx] = bundle
            next_process_idx += 1
        return frame_idx in processed_cache

    def find_next_available_frame(start_idx: int, direction: int = 1) -> int | None:
        nonlocal end_reached
        if frame_count <= 0:
            return None
        idx = int(start_idx)
        if direction >= 0:
            while idx < frame_count:
                ensure_processed(idx)
                if idx in processed_cache:
                    return idx
                if end_reached and idx >= next_process_idx:
                    break
                idx += 1
            return None
        idx = min(idx, frame_count - 1)
        while idx >= 0:
            if idx in processed_cache:
                return idx
            idx -= 1
        return None

    def build_overlay_for_frame(frame_idx: int) -> np.ndarray | None:
        raw = fetch_frame_at(display_cap, frame_idx)
        if raw is None:
            return None
        bundle = processed_cache.get(frame_idx)
        if bundle is None:
            return raw
        overlay = raw
        if args.show_overlay or writer is not None:
            overlay = draw_overlay(
                raw,
                bundle["pose_landmarks_xy"],
                bundle["target_points"],
                bundle["fitted_sites"],
                show_target=not args.mp_only_overlay,
                show_fit=not args.mp_only_overlay,
            )
            fit = bundle["fit"]
            cv2.putText(
                overlay,
                f"Frame {frame_idx+1}/{max(frame_count, 1)}  Mean err: {fit['mean_error_m']*100:.1f}cm  Max err: {fit['max_error_m']*100:.1f}cm",
                (24, 60),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.7,
                (245, 245, 245),
                2,
                cv2.LINE_AA,
            )
            cv2.putText(
                overlay,
                "Space: pause/play  A/D or <-/->: +/-1 frame  J/L: +/-15  H/K: first/last  Q: quit",
                (24, 88),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.52,
                (220, 220, 220),
                2,
                cv2.LINE_AA,
            )
            if bool(bundle.get("frozen", False)):
                cv2.putText(
                    overlay,
                    "Pose glitch detected: holding last stable pose",
                    (24, 116),
                    cv2.FONT_HERSHEY_SIMPLEX,
                    0.56,
                    (60, 210, 255),
                    2,
                    cv2.LINE_AA,
                )
        return overlay

    def show_frame(frame_idx: int) -> np.ndarray | None:
        bundle = processed_cache.get(frame_idx)
        if bundle is not None:
            data.qpos[:] = bundle["qpos"]
            data.qvel[:] = 0.0
            mujoco.mj_forward(model, data)
        return build_overlay_for_frame(frame_idx)

    def handle_key(key_code: int) -> bool:
        nonlocal paused, pending_seek_idx, current_display_idx
        if key_code in (-1, 255):
            return True
        if key_code in (KEY_ESC, KEY_Q):
            return False
        if key_code == KEY_SPACE:
            paused = not paused
            return True
        if key_code in (KEY_A, KEY_LEFT):
            paused = True
            pending_seek_idx = max(current_display_idx - 1, 0)
            return True
        if key_code in (KEY_D, KEY_RIGHT):
            paused = True
            pending_seek_idx = min(current_display_idx + 1, max(frame_count - 1, 0))
            return True
        if key_code == KEY_J:
            paused = True
            pending_seek_idx = max(current_display_idx - 15, 0)
            return True
        if key_code == KEY_L:
            paused = True
            pending_seek_idx = min(current_display_idx + 15, max(frame_count - 1, 0))
            return True
        if key_code in (KEY_H, KEY_HOME):
            paused = True
            pending_seek_idx = 0
            return True
        if key_code in (KEY_K, KEY_END):
            paused = True
            pending_seek_idx = max(frame_count - 1, 0)
            return True
        return True

    if args.no_viewer:
        while True:
            if pending_seek_idx is not None:
                target_idx = pending_seek_idx
                if target_idx >= current_display_idx:
                    candidate = find_next_available_frame(target_idx, direction=1)
                else:
                    candidate = find_next_available_frame(target_idx, direction=-1)
                if candidate is not None:
                    current_display_idx = candidate
                pending_seek_idx = None
            elif not paused:
                next_target = max(current_display_idx + 1, 0)
                candidate = find_next_available_frame(next_target, direction=1)
                if candidate is not None:
                    current_display_idx = candidate
                elif end_reached:
                    paused = True

            overlay = None
            if current_display_idx >= 0:
                overlay = show_frame(current_display_idx)
                if overlay is not None and args.show_overlay:
                    cv2.imshow(OVERLAY_WINDOW, overlay)
                if writer is not None and overlay is not None and not paused:
                    writer.write(overlay)

            key = cv2.waitKeyEx(30 if paused else max(1, int(round(1000.0 / max(fps, 1.0)))))
            if not handle_key(key):
                break
        cv2.destroyAllWindows()
    else:
        with mujoco.viewer.launch_passive(model, data) as viewer:
            try:
                while viewer.is_running():
                    loop_start = time.perf_counter()
                    if pending_seek_idx is not None:
                        target_idx = pending_seek_idx
                        if target_idx >= current_display_idx:
                            candidate = find_next_available_frame(target_idx, direction=1)
                        else:
                            candidate = find_next_available_frame(target_idx, direction=-1)
                        if candidate is not None:
                            current_display_idx = candidate
                        pending_seek_idx = None
                    elif not paused:
                        next_target = max(current_display_idx + 1, 0)
                        candidate = find_next_available_frame(next_target, direction=1)
                        if candidate is not None:
                            current_display_idx = candidate
                        elif end_reached:
                            paused = True

                    overlay = None
                    if current_display_idx >= 0:
                        overlay = show_frame(current_display_idx)

                    viewer.sync()
                    if overlay is not None and args.show_overlay:
                        cv2.imshow(OVERLAY_WINDOW, overlay)
                    if writer is not None and overlay is not None and not paused:
                        writer.write(overlay)
                    key = cv2.waitKeyEx(1)
                    if not handle_key(key):
                        break
                    elapsed = time.perf_counter() - loop_start
                    sleep_time = 0.0 if paused else max(0.0, (1.0 / max(fps, 1.0)) - elapsed)
                    if sleep_time > 0:
                        time.sleep(sleep_time)
            finally:
                cv2.destroyAllWindows()

    process_cap.release()
    display_cap.release()
    if writer is not None:
        writer.release()
    landmarker.close()

    summary = {
        "xml": str(args.xml.resolve()),
        "input_video": str(args.input_video.resolve()),
        "frames_processed": next_process_idx,
        "mean_fit_error_m": float(sum(mean_errors) / len(mean_errors)) if mean_errors else None,
        "max_fit_error_m": max(max_errors) if max_errors else None,
        "overlay_output": str(args.overlay_output.resolve()) if args.overlay_output is not None else None,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
