from __future__ import annotations

import argparse
import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import mujoco
import numpy as np


ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
ARTIC_ROOT = PROJECT_ROOT / "custom_articulated_human"
CUSTOM_SKELETON_ROOT = PROJECT_ROOT / "custom_skeleton_verify"

sys.path.insert(0, str(ARTIC_ROOT))
sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))

from evaluate_static_fit import compute_high_step_score, compute_knee_flexion_target  # noqa: E402
from mediapipe_custom_skeleton_verify import MetricSkeletonMapper  # noqa: E402
from pose_sequence_correction import correct_pose_sequence_payload  # noqa: E402


DEFAULT_REPORT_JSON = ROOT / "json_service_benchmark_report_audit_final_10fps_corrected_v9.json"
DEFAULT_POSE_JSON = ROOT / "benchmark_inputs" / "audit_final_10fps" / "pose3d_sequence.json"
DEFAULT_USER_BODY_JSON = ROOT / "benchmark_inputs" / "audit_final_10fps" / "user_body.json"
DEFAULT_OUTPUT_JSON = ROOT / "high_step_candidate_report.json"
DEFAULT_OUTPUT_MD = PROJECT_ROOT / "AUDIT_03_HIGH_STEP_CANDIDATES.md"

VISUALIZER_COMMAND = (
    "C:/Users/SSAFY/miniforge3/envs/mujoco_env/python.exe "
    "visualize_corrected_benchmark_live.py --start-frame {start_frame} --max-frames {max_frames} --pause-at-start"
)


@dataclass(slots=True)
class Landmark:
    x: float
    y: float
    z: float


def normalize(v: np.ndarray, eps: float = 1e-8) -> np.ndarray:
    arr = np.asarray(v, dtype=np.float64)
    norm = float(np.linalg.norm(arr))
    if norm < eps:
        return np.zeros_like(arr)
    return arr / norm


def horizontal_signed_angle_deg(vec_a: np.ndarray, vec_b: np.ndarray) -> float:
    a = normalize(np.array([float(vec_a[0]), float(vec_a[1]), 0.0], dtype=np.float64))
    b = normalize(np.array([float(vec_b[0]), float(vec_b[1]), 0.0], dtype=np.float64))
    if float(np.linalg.norm(a)) < 1e-8 or float(np.linalg.norm(b)) < 1e-8:
        return 0.0
    cross_z = float(np.cross(a, b)[2])
    dot = float(np.clip(np.dot(a, b), -1.0, 1.0))
    return float(np.degrees(np.arctan2(cross_z, dot)))


def build_mapper(user_body_payload: dict[str, Any]) -> MetricSkeletonMapper:
    calibration = user_body_payload.get("calibration_compat")
    if not isinstance(calibration, dict):
        raise KeyError("user_body.json is missing calibration_compat")
    return MetricSkeletonMapper({str(key): float(value) for key, value in calibration.items()})


def load_landmarks(payload: list[dict[str, Any]] | None) -> list[Landmark] | None:
    if payload is None:
        return None
    return [
        Landmark(
            x=float(item["x"]),
            y=float(item["y"]),
            z=float(item.get("z", 0.0)),
        )
        for item in payload
    ]


def joint_qpos(model: mujoco.MjModel, joint_name: str, qpos: np.ndarray) -> float:
    joint_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_JOINT, joint_name)
    return float(qpos[int(model.jnt_qposadr[joint_id])])


def joint_range(model: mujoco.MjModel, joint_name: str) -> tuple[float, float]:
    joint_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_JOINT, joint_name)
    return float(model.jnt_range[joint_id, 0]), float(model.jnt_range[joint_id, 1])


def near_limit(value: float, low: float, high: float, ratio: float = 0.9) -> str | None:
    span = max(high - low, 1e-8)
    lower_threshold = low + span * (1.0 - ratio)
    upper_threshold = high - span * (1.0 - ratio)
    if value <= lower_threshold:
        return "low"
    if value >= upper_threshold:
        return "high"
    return None


def side_metrics(
    model: mujoco.MjModel,
    data: mujoco.MjData,
    qpos: np.ndarray,
    points: dict[str, np.ndarray],
    frame: dict[str, Any],
    side: str,
) -> dict[str, Any]:
    hip_site_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, f"{side}_hip_site")
    knee_site_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, f"{side}_knee_site")
    ankle_site_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, f"{side}_ankle_site")
    foot_site_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, f"{side}_foot_site")
    heel_site_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, f"{side}_heel_site")
    pelvis_site_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, "pelvis_site")
    knee_forward_site_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_SITE, f"{side}_knee_forward_site")
    thigh_body_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, f"{side}_thigh")
    shin_body_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, f"{side}_knee")
    foot_body_id = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, f"{side}_foot")

    hip = data.site_xpos[hip_site_id].copy()
    knee = data.site_xpos[knee_site_id].copy()
    ankle = data.site_xpos[ankle_site_id].copy()
    foot = data.site_xpos[foot_site_id].copy()
    heel = data.site_xpos[heel_site_id].copy()
    pelvis = data.site_xpos[pelvis_site_id].copy()

    knee_front = normalize(data.site_xpos[knee_forward_site_id] - knee)
    thigh_front = normalize(data.xmat[thigh_body_id].reshape(3, 3)[:, 0].copy())
    shin_front = normalize(data.xmat[shin_body_id].reshape(3, 3)[:, 0].copy())
    foot_front = normalize(data.xmat[foot_body_id].reshape(3, 3)[:, 0].copy())

    target_knee_flex_deg = float(math.degrees(abs(compute_knee_flexion_target(points, side))))
    fitted_knee_flex_deg = float(math.degrees(abs(joint_qpos(model, f"knee_{side}", qpos))))
    high_step_score = float(compute_high_step_score(points, side))

    joint_names = (
        f"hip_x_{side}",
        f"hip_y_{side}",
        f"hip_z_{side}",
        f"knee_{side}",
        f"ankle_x_{side}",
        f"ankle_y_{side}",
    )
    joint_payload: dict[str, Any] = {}
    saturated: list[str] = []
    for joint_name in joint_names:
        value = joint_qpos(model, joint_name, qpos)
        low, high = joint_range(model, joint_name)
        limit_state = near_limit(value, low, high, ratio=0.9)
        payload = {
            "deg": float(math.degrees(value)),
            "range_deg": [float(math.degrees(low)), float(math.degrees(high))],
            "near_limit": limit_state,
        }
        joint_payload[joint_name] = payload
        if limit_state is not None:
            saturated.append(f"{joint_name}:{limit_state}")

    limb_state = (frame.get("limb_states") or {}).get(f"{side}_foot", {}) or {}
    metrics = {
        "side": side,
        "state": limb_state.get("state"),
        "active_hold_id": limb_state.get("active_hold_id"),
        "contact_presence_confidence": limb_state.get("contact_presence_confidence"),
        "hold_identity_confidence": limb_state.get("hold_identity_confidence"),
        "high_step_score": high_step_score,
        "target_knee_flex_deg": target_knee_flex_deg,
        "fitted_knee_flex_deg": fitted_knee_flex_deg,
        "knee_flex_gap_deg": target_knee_flex_deg - fitted_knee_flex_deg,
        "hip_to_ankle_drop_m": float(hip[2] - ankle[2]),
        "pelvis_to_foot_drop_m": float(pelvis[2] - foot[2]),
        "knee_foot_yaw_deg": horizontal_signed_angle_deg(knee_front, foot_front),
        "shin_foot_yaw_deg": horizontal_signed_angle_deg(shin_front, foot_front),
        "thigh_shin_yaw_deg": horizontal_signed_angle_deg(thigh_front, shin_front),
        "joints": joint_payload,
        "saturated_joints": saturated,
    }
    return metrics


def is_candidate(metrics: dict[str, Any], min_high_step: float, min_gap_deg: float) -> bool:
    if str(metrics.get("state")) != "STEP":
        return False
    if float(metrics.get("high_step_score", 0.0)) < min_high_step:
        return False
    if float(metrics.get("knee_flex_gap_deg", 0.0)) < min_gap_deg:
        return False
    return True


def summarize_reasons(metrics: dict[str, Any]) -> list[str]:
    reasons: list[str] = []
    if float(metrics["target_knee_flex_deg"]) >= 80.0 and float(metrics["fitted_knee_flex_deg"]) <= 15.0:
        reasons.append("target는 무릎을 크게 굽히라고 하지만 fit은 거의 못 굽힘")
    if any(name.startswith("hip_x_") for name in metrics["saturated_joints"]):
        reasons.append("hip_x(고관절 벌림/모음)가 상한 근처라 다리 벌림 보상이 큼")
    if any(name.startswith("hip_y_") for name in metrics["saturated_joints"]):
        reasons.append("hip_y(고관절 굽힘/폄)가 한계 근처라 허벅지 들어올림만으로 버티는 경향")
    if any(name.startswith("ankle_x_") for name in metrics["saturated_joints"]):
        reasons.append("ankle_x(발목 기울기)가 상한 근처라 발목 옆기울기 보상이 큼")
    if any(name.startswith("ankle_y_") for name in metrics["saturated_joints"]):
        reasons.append("ankle_y(발목 앞뒤 회전)가 상한 근처라 발목 회전 보상이 큼")
    if abs(float(metrics["knee_foot_yaw_deg"])) >= 20.0:
        reasons.append("무릎 정면과 발 정면이 크게 어긋남")
    if not reasons:
        reasons.append("high-step인데 무릎 굽힘 부족")
    return reasons


def build_segments(candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    if not candidates:
        return []
    segments: list[dict[str, Any]] = []
    current = {
        "side": candidates[0]["side"],
        "start_frame": candidates[0]["frame_index"],
        "end_frame": candidates[0]["frame_index"],
        "frames": [candidates[0]],
    }
    for candidate in candidates[1:]:
        prev = current["frames"][-1]
        if candidate["side"] == current["side"] and int(candidate["frame_index"]) <= int(prev["frame_index"]) + 1:
            current["end_frame"] = candidate["frame_index"]
            current["frames"].append(candidate)
            continue
        segments.append(current)
        current = {
            "side": candidate["side"],
            "start_frame": candidate["frame_index"],
            "end_frame": candidate["frame_index"],
            "frames": [candidate],
        }
    segments.append(current)
    return segments


def representative_frame(segment: dict[str, Any]) -> dict[str, Any]:
    return max(segment["frames"], key=lambda item: float(item["metrics"]["knee_flex_gap_deg"]))


def to_serializable(obj: Any) -> Any:
    if isinstance(obj, dict):
        return {key: to_serializable(value) for key, value in obj.items()}
    if isinstance(obj, list):
        return [to_serializable(value) for value in obj]
    if isinstance(obj, np.ndarray):
        return obj.tolist()
    if isinstance(obj, (np.floating, np.integer)):
        return obj.item()
    return obj


def render_markdown(
    report: dict[str, Any],
    output_md: Path,
) -> None:
    lines: list[str] = []
    lines.append("# AUDIT 03: High-Step 후보 프레임 진단")
    lines.append("")
    lines.append("## 목적")
    lines.append("- `high-step(발을 높이 들어 디뎌야 하는 프레임)`에서 왜 `knee flexion(무릎 굽힘)` 대신 `hip/ankle compensation(고관절/발목 보상)`이 선택되는지 직접 확인한다.")
    lines.append("- 입력 target(목표 자세)와 fitted pose(맞춘 자세)를 분리해서 본다.")
    lines.append("")
    lines.append("## 기준")
    lines.append(f"- 후보 프레임 조건: `STEP` + `high_step_score >= {report['thresholds']['min_high_step_score']}` + `target_knee_flex - fitted_knee_flex >= {report['thresholds']['min_knee_flex_gap_deg']}deg`")
    lines.append("")
    lines.append("## 요약")
    summary = report["summary"]
    lines.append(f"- 전체 후보 프레임 수: `{summary['candidate_frame_count']}`")
    lines.append(f"- 후보 segment 수: `{summary['candidate_segment_count']}`")
    lines.append(f"- 가장 큰 gap 프레임: `frame {summary['worst_frame']['frame_index']}` / `{summary['worst_frame']['side']}`")
    lines.append(
        f"- 최악 gap: `target {summary['worst_frame']['metrics']['target_knee_flex_deg']:.1f}deg` vs "
        f"`fit {summary['worst_frame']['metrics']['fitted_knee_flex_deg']:.1f}deg` "
        f"(gap `{summary['worst_frame']['metrics']['knee_flex_gap_deg']:.1f}deg`)"
    )
    lines.append("")
    lines.append("## 대표 segment")
    for segment in report["segments"]:
        rep = segment["representative_frame"]
        metrics = rep["metrics"]
        side = segment["side"]
        hip_y = metrics["joints"][f"hip_y_{side}"]["deg"]
        hip_x = metrics["joints"][f"hip_x_{side}"]["deg"]
        hip_z = metrics["joints"][f"hip_z_{side}"]["deg"]
        ankle_x = metrics["joints"][f"ankle_x_{side}"]["deg"]
        ankle_y = metrics["joints"][f"ankle_y_{side}"]["deg"]
        lines.append(
            f"- `{segment['side']}` / `frame {segment['start_frame']}~{segment['end_frame']}` / "
            f"대표 `frame {rep['frame_index']}` / gap `{metrics['knee_flex_gap_deg']:.1f}deg`"
        )
        lines.append(
            f"  - target knee `{metrics['target_knee_flex_deg']:.1f}deg`, fit knee `{metrics['fitted_knee_flex_deg']:.1f}deg`, "
            f"high_step `{metrics['high_step_score']:.3f}`, hold `{metrics.get('active_hold_id')}`"
        )
        lines.append(
            f"  - hip_y `{hip_y:.1f}deg`, "
            f"hip_x `{hip_x:.1f}deg`, "
            f"hip_z `{hip_z:.1f}deg`, "
            f"ankle_x `{ankle_x:.1f}deg`, "
            f"ankle_y `{ankle_y:.1f}deg`"
        )
        lines.append(f"  - 이유: {', '.join(rep['reason_tags'])}")
        lines.append(f"  - 보기: `{segment['viewer_command']}`")
    lines.append("")
    lines.append("## 해석")
    lines.append("- 이 문서에서 `target knee flex(목표 무릎 굽힘)`는 입력 MediaPipe 3D와 사용자 체형 보정으로 만든 목표 자세가 요구하는 무릎 굽힘이다.")
    lines.append("- `fit knee flex(실제 맞춰진 무릎 굽힘)`가 이 값보다 훨씬 작다면, 입력이 틀린 게 아니라 현재 제약/가중치 때문에 solver가 다른 해를 고른 것이다.")
    lines.append("- `hip_x/hip_y/ankle_x/ankle_y`가 상한이나 하한 근처에 붙어 있으면, 무릎 대신 그 관절들로 보상하고 있다는 뜻이다.")
    lines.append("")
    output_md.write_text("\n".join(lines) + "\n", encoding="utf-8")


def analyze(
    report_json: Path,
    pose_json: Path,
    user_body_json: Path,
    output_json: Path,
    output_md: Path,
    min_high_step_score: float,
    min_knee_flex_gap_deg: float,
    top_k_segments: int,
    viewer_window: int,
) -> dict[str, Any]:
    report_payload = json.loads(report_json.read_text(encoding="utf-8"))
    pose_payload = json.loads(pose_json.read_text(encoding="utf-8"))
    user_body_payload = json.loads(user_body_json.read_text(encoding="utf-8"))
    corrected_pose_payload = correct_pose_sequence_payload(
        pose_payload=pose_payload,
        user_body_payload=user_body_payload,
        preserve_raw_copy=False,
    )

    mapper = build_mapper(user_body_payload)
    personalized_xml = Path(report_payload["inputs"]["personalized_xml"])
    model = mujoco.MjModel.from_xml_path(str(personalized_xml.resolve()))
    data = mujoco.MjData(model)

    pose_frames_by_index = {
        int(frame["frame_index"]): frame
        for frame in corrected_pose_payload.get("frames", [])
    }

    candidates: list[dict[str, Any]] = []
    for frame in report_payload["frames"]:
        frame_index = int(frame["frame_index"])
        if str(frame.get("pose_mode")) not in ("fitted", "interpolated"):
            continue
        raw_frame = pose_frames_by_index.get(frame_index)
        if raw_frame is None:
            continue
        world_landmarks = load_landmarks(raw_frame.get("pose_world_landmarks"))
        if not world_landmarks:
            continue
        points = mapper.map_frame(world_landmarks)
        qpos = np.asarray(frame["qpos"], dtype=np.float64)
        data.qpos[:] = qpos
        data.qvel[:] = 0.0
        data.qacc[:] = 0.0
        mujoco.mj_forward(model, data)

        for side in ("left", "right"):
            metrics = side_metrics(model, data, qpos, points, frame, side)
            if not is_candidate(metrics, min_high_step_score, min_knee_flex_gap_deg):
                continue
            candidates.append(
                {
                    "frame_index": frame_index,
                    "timestamp_ms": int(frame["timestamp_ms"]),
                    "side": side,
                    "metrics": metrics,
                    "reason_tags": summarize_reasons(metrics),
                }
            )

    candidates.sort(key=lambda item: (item["side"], item["frame_index"]))
    segments_raw = build_segments(candidates)
    segments_scored: list[dict[str, Any]] = []
    for segment in segments_raw:
        rep = representative_frame(segment)
        segment_payload = {
            "side": segment["side"],
            "start_frame": int(segment["start_frame"]),
            "end_frame": int(segment["end_frame"]),
            "frame_count": len(segment["frames"]),
            "representative_frame": rep,
            "viewer_command": VISUALIZER_COMMAND.format(
                start_frame=max(0, int(rep["frame_index"]) - 2),
                max_frames=viewer_window,
            ),
        }
        segments_scored.append(segment_payload)

    segments_scored.sort(
        key=lambda item: float(item["representative_frame"]["metrics"]["knee_flex_gap_deg"]),
        reverse=True,
    )
    top_segments = segments_scored[:top_k_segments]

    worst_frame = top_segments[0]["representative_frame"] if top_segments else None
    output = {
        "inputs": {
            "report_json": str(report_json.resolve()),
            "pose_json": str(pose_json.resolve()),
            "user_body_json": str(user_body_json.resolve()),
            "personalized_xml": str(personalized_xml.resolve()),
        },
        "thresholds": {
            "min_high_step_score": float(min_high_step_score),
            "min_knee_flex_gap_deg": float(min_knee_flex_gap_deg),
        },
        "summary": {
            "candidate_frame_count": len(candidates),
            "candidate_segment_count": len(segments_raw),
            "worst_frame": worst_frame,
        },
        "segments": top_segments,
        "all_candidates": candidates,
    }
    output_json.write_text(json.dumps(to_serializable(output), ensure_ascii=False, indent=2), encoding="utf-8")
    render_markdown(output, output_md)
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description="Find high-step frames where target knee flex is large but fitted knee flex stays small.")
    parser.add_argument("--report-json", type=Path, default=DEFAULT_REPORT_JSON)
    parser.add_argument("--pose-json", type=Path, default=DEFAULT_POSE_JSON)
    parser.add_argument("--user-body-json", type=Path, default=DEFAULT_USER_BODY_JSON)
    parser.add_argument("--output-json", type=Path, default=DEFAULT_OUTPUT_JSON)
    parser.add_argument("--output-md", type=Path, default=DEFAULT_OUTPUT_MD)
    parser.add_argument("--min-high-step-score", type=float, default=0.7)
    parser.add_argument("--min-knee-flex-gap-deg", type=float, default=30.0)
    parser.add_argument("--top-k-segments", type=int, default=5)
    parser.add_argument("--viewer-window", type=int, default=18)
    args = parser.parse_args()

    result = analyze(
        report_json=args.report_json,
        pose_json=args.pose_json,
        user_body_json=args.user_body_json,
        output_json=args.output_json,
        output_md=args.output_md,
        min_high_step_score=args.min_high_step_score,
        min_knee_flex_gap_deg=args.min_knee_flex_gap_deg,
        top_k_segments=args.top_k_segments,
        viewer_window=args.viewer_window,
    )
    worst = result["summary"]["worst_frame"]
    if worst is None:
        print("No high-step underflex candidates found.")
        return
    print(
        f"[OK] worst frame={worst['frame_index']} side={worst['side']} "
        f"target={worst['metrics']['target_knee_flex_deg']:.1f}deg "
        f"fit={worst['metrics']['fitted_knee_flex_deg']:.1f}deg "
        f"gap={worst['metrics']['knee_flex_gap_deg']:.1f}deg"
    )
    print(f"[OK] wrote {args.output_json.resolve()}")
    print(f"[OK] wrote {args.output_md.resolve()}")


if __name__ == "__main__":
    main()
