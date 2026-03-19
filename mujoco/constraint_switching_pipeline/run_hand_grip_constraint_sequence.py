from __future__ import annotations

import argparse
import json
import sys
import time
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

import mujoco
import numpy as np

ROOT = Path(__file__).resolve().parent
PROJECT_ROOT = ROOT.parent
ARTIC_ROOT = PROJECT_ROOT / "custom_articulated_human"
CUSTOM_SKELETON_ROOT = PROJECT_ROOT / "custom_skeleton_verify"
DYNAMIC_ROOT = PROJECT_ROOT / "dynamic_sequence_pipeline"
PHYSICS_ROOT = PROJECT_ROOT / "dynamic_hold_verify"

DEFAULT_BASE_XML = ARTIC_ROOT / "custom_articulated_human_personalized.xml"
if not DEFAULT_BASE_XML.exists():
    DEFAULT_BASE_XML = ARTIC_ROOT / "custom_articulated_human.xml"

sys.path.insert(0, str(DYNAMIC_ROOT))
sys.path.insert(0, str(CUSTOM_SKELETON_ROOT))
sys.path.insert(0, str(PHYSICS_ROOT))

from physics_worker import load_calibration_json  # noqa: E402
from run_dynamic_sequence_analysis import (  # noqa: E402
    collect_joint_inverse_forces,
    evaluate_video as evaluate_dynamic_video,
    summarize_body_loads,
    top_joint_loads,
)

HAND_LIMBS = ("left_hand", "right_hand")
HAND_BODY_NAMES = {
    "left_hand": "left_hand",
    "right_hand": "right_hand",
}
ANCHOR_BODY_NAMES = {
    "left_hand": "left_grip_anchor",
    "right_hand": "right_grip_anchor",
}
ANCHOR_SITE_NAMES = {
    "left_hand": "left_grip_anchor_site",
    "right_hand": "right_grip_anchor_site",
}
WELD_NAMES = {
    "left_hand": "left_hand_grip_weld",
    "right_hand": "right_hand_grip_weld",
}


def _find_named_child(parent: ET.Element, tag: str, name: str) -> ET.Element | None:
    for child in parent.findall(tag):
        if child.get("name") == name:
            return child
    return None


def build_hand_grip_constraint_xml(base_xml: Path, output_xml: Path) -> Path:
    tree = ET.parse(base_xml)
    root = tree.getroot()
    worldbody = root.find("worldbody")
    if worldbody is None:
        raise RuntimeError("Expected <worldbody> in MuJoCo XML.")

    for limb_name in HAND_LIMBS:
        body_name = ANCHOR_BODY_NAMES[limb_name]
        site_name = ANCHOR_SITE_NAMES[limb_name]
        anchor_body = _find_named_child(worldbody, "body", body_name)
        if anchor_body is None:
            anchor_body = ET.SubElement(
                worldbody,
                "body",
                {
                    "name": body_name,
                    "mocap": "true",
                    "pos": "0 0 0",
                    "quat": "1 0 0 0",
                },
            )
        if _find_named_child(anchor_body, "site", site_name) is None:
            ET.SubElement(
                anchor_body,
                "site",
                {
                    "name": site_name,
                    "pos": "0 0 0",
                    "size": "0.012",
                    "rgba": "0.20 0.96 1 0.35" if limb_name == "left_hand" else "1 0.58 0.58 0.35",
                },
            )

    equality = root.find("equality")
    if equality is None:
        equality = ET.SubElement(root, "equality")

    for limb_name in HAND_LIMBS:
        weld_name = WELD_NAMES[limb_name]
        weld = _find_named_child(equality, "weld", weld_name)
        if weld is None:
            weld = ET.SubElement(
                equality,
                "weld",
                {
                    "name": weld_name,
                    "body1": HAND_BODY_NAMES[limb_name],
                    "body2": ANCHOR_BODY_NAMES[limb_name],
                    "solref": "0.004 1",
                    "solimp": "0.95 0.99 0.001",
                },
            )
        else:
            weld.set("body1", HAND_BODY_NAMES[limb_name])
            weld.set("body2", ANCHOR_BODY_NAMES[limb_name])
            weld.set("solref", "0.004 1")
            weld.set("solimp", "0.95 0.99 0.001")

    ET.indent(tree, space="  ")
    output_xml.parent.mkdir(parents=True, exist_ok=True)
    tree.write(output_xml, encoding="utf-8", xml_declaration=False)
    return output_xml


def load_or_generate_dynamic_report(
    *,
    dynamic_report_path: Path | None,
    dynamic_report_output: Path,
    xml_path: Path,
    video_path: Path,
    task_model: Path,
    calibration_json: Path,
    detections_json: Path | None,
    ik_iterations: int,
    damping: float,
    frame_step: int,
    smoothing_window: int,
    top_k_joints: int,
    force_regenerate: bool,
) -> dict[str, Any]:
    if dynamic_report_path is not None and dynamic_report_path.exists() and not force_regenerate:
        loaded = json.loads(dynamic_report_path.read_text(encoding="utf-8"))
        frames = loaded.get("frames")
        if isinstance(frames, list) and frames and "qpos" in frames[0] and "qvel" in frames[0] and "qacc" in frames[0]:
            return loaded

    calibration = load_calibration_json(calibration_json)
    report = evaluate_dynamic_video(
        xml_path=xml_path,
        video_path=video_path,
        task_path=task_model,
        calibration=calibration,
        detections_json=detections_json,
        ik_iterations=ik_iterations,
        damping=damping,
        frame_step=frame_step,
        smoothing_window=smoothing_window,
        top_k_joints=top_k_joints,
        store_state_vectors=True,
    )
    dynamic_report_output.parent.mkdir(parents=True, exist_ok=True)
    dynamic_report_output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def _body_pos_quat(data: mujoco.MjData, body_id: int) -> tuple[np.ndarray, np.ndarray]:
    return (
        np.asarray(data.xpos[body_id], dtype=np.float64).copy(),
        np.asarray(data.xquat[body_id], dtype=np.float64).copy(),
    )


def _set_mocap_pose(data: mujoco.MjData, mocap_id: int, pos: np.ndarray, quat: np.ndarray) -> None:
    data.mocap_pos[mocap_id, :] = np.asarray(pos, dtype=np.float64)
    data.mocap_quat[mocap_id, :] = np.asarray(quat, dtype=np.float64)


def _limb_payload(frame: dict[str, Any], limb_name: str) -> dict[str, Any]:
    payload = (frame.get("limb_states") or {}).get(limb_name)
    return payload if isinstance(payload, dict) else {}


def _is_grip_active(payload: dict[str, Any]) -> bool:
    return str(payload.get("state")) == "GRIP" and payload.get("active_hold_id") is not None


def _constraint_mode(left_active: bool, right_active: bool) -> str:
    if left_active and right_active:
        return "both"
    if left_active:
        return "left_only"
    if right_active:
        return "right_only"
    return "none"


def _delta_body_loads(constrained: dict[str, float], baseline: dict[str, float]) -> dict[str, float]:
    keys = set(constrained) | set(baseline)
    return {
        key: float(constrained.get(key, 0.0) - baseline.get(key, 0.0))
        for key in sorted(keys)
    }


def _top_joint_deltas(
    constrained: dict[str, dict[str, float]],
    baseline: dict[str, dict[str, float]],
    top_k: int,
) -> list[dict[str, float | str]]:
    names = set(constrained) | set(baseline)
    deltas: list[dict[str, float | str]] = []
    for joint_name in names:
        constrained_value = float(constrained.get(joint_name, {}).get("qfrc_inverse", 0.0))
        baseline_value = float(baseline.get(joint_name, {}).get("qfrc_inverse", 0.0))
        delta = constrained_value - baseline_value
        deltas.append(
            {
                "joint": joint_name,
                "delta_qfrc_inverse": delta,
                "abs_delta_qfrc_inverse": abs(delta),
            }
        )
    deltas.sort(key=lambda item: float(item["abs_delta_qfrc_inverse"]), reverse=True)
    return deltas[:top_k]


def _safe_nefc(data: mujoco.MjData) -> int:
    nefc = int(getattr(data, "nefc", 0))
    if nefc < 0:
        return 0
    return nefc


def _run_pass(
    model: mujoco.MjModel,
    data: mujoco.MjData,
    *,
    qpos: np.ndarray,
    qvel: np.ndarray,
    qacc: np.ndarray,
    eq_active: dict[str, bool],
    mocap_pose_by_limb: dict[str, tuple[np.ndarray, np.ndarray]],
    eq_ids: dict[str, int],
    mocap_ids: dict[str, int],
    hand_body_ids: dict[str, int],
    top_k_joints: int,
) -> dict[str, Any]:
    data.qpos[:] = qpos
    data.qvel[:] = qvel
    data.qacc[:] = qacc
    data.qfrc_applied[:] = 0.0
    data.xfrc_applied[:] = 0.0

    if model.neq > 0:
        model.eq_active0[:] = 0
        data.eq_active[:] = 0

    for limb_name, (pos, quat) in mocap_pose_by_limb.items():
        _set_mocap_pose(data, mocap_ids[limb_name], pos, quat)

    mujoco.mj_forward(model, data)

    current_hand_pose = {
        limb_name: _body_pos_quat(data, hand_body_ids[limb_name])
        for limb_name in HAND_LIMBS
    }

    if model.neq > 0:
        for limb_name, active in eq_active.items():
            eq_id = eq_ids[limb_name]
            value = 1 if active else 0
            model.eq_active0[eq_id] = value
            data.eq_active[eq_id] = value

    data.qvel[:] = qvel
    data.qacc[:] = qacc
    mujoco.mj_forward(model, data)
    data.qvel[:] = qvel
    data.qacc[:] = qacc
    mujoco.mj_inverse(model, data)

    joint_inverse_forces = collect_joint_inverse_forces(model, data)
    body_loads = summarize_body_loads(joint_inverse_forces)
    root_inverse_force = np.asarray(data.qfrc_inverse[:6], dtype=np.float64)
    root_constraint_force = np.asarray(data.qfrc_constraint[:6], dtype=np.float64)
    nefc = _safe_nefc(data)
    efc_force = np.asarray(data.efc_force[:nefc], dtype=np.float64) if nefc > 0 else np.zeros(0, dtype=np.float64)

    return {
        "hand_pose": current_hand_pose,
        "joint_inverse_forces": joint_inverse_forces,
        "body_loads": body_loads,
        "root_inverse_force": root_inverse_force,
        "root_constraint_force": root_constraint_force,
        "qfrc_constraint_norm": float(np.linalg.norm(np.asarray(data.qfrc_constraint, dtype=np.float64))),
        "root_constraint_force_norm": float(np.linalg.norm(root_constraint_force)),
        "efc_force_norm": float(np.linalg.norm(efc_force)),
        "top_joint_loads": top_joint_loads(joint_inverse_forces, top_k_joints),
    }


def summarize_frames(frames: list[dict[str, Any]]) -> dict[str, Any]:
    mode_counts = Counter(str(frame["constraint_mode"]) for frame in frames)
    active_frames = [frame for frame in frames if frame["constraint_mode"] != "none"]
    delta_body_bucket: dict[str, list[float]] = defaultdict(list)
    residual_bucket: list[float] = []
    qfrc_constraint_bucket: list[float] = []

    for frame in active_frames:
        delta_body_loads = frame["delta"]["body_loads"]
        for body_name, value in delta_body_loads.items():
            delta_body_bucket[body_name].append(float(value))
        qfrc_constraint_bucket.append(float(frame["constrained"]["qfrc_constraint_norm"]))
        residual_bucket.append(float(frame["delta"]["root_inverse_force_norm"]))

    delta_body_summary = {
        body_name: {
            "mean": float(np.mean(values)),
            "median": float(np.median(values)),
            "p95": float(np.percentile(values, 95)),
        }
        for body_name, values in delta_body_bucket.items()
        if values
    }

    summary: dict[str, Any] = {
        "constraint_mode_counts": dict(mode_counts),
        "grip_active_frame_count": len(active_frames),
        "delta_body_load_summary": delta_body_summary,
    }
    if qfrc_constraint_bucket:
        arr = np.asarray(qfrc_constraint_bucket, dtype=np.float64)
        summary["qfrc_constraint_norm_summary"] = {
            "mean": float(np.mean(arr)),
            "median": float(np.median(arr)),
            "p95": float(np.percentile(arr, 95)),
            "max": float(np.max(arr)),
        }
    if residual_bucket:
        arr = np.asarray(residual_bucket, dtype=np.float64)
        summary["delta_root_inverse_norm_summary"] = {
            "mean": float(np.mean(arr)),
            "median": float(np.median(arr)),
            "p95": float(np.percentile(arr, 95)),
            "max": float(np.max(arr)),
        }
    return summary


def evaluate_hand_grip_constraint_sequence(
    *,
    constraint_xml: Path,
    dynamic_report: dict[str, Any],
    top_k_joints: int,
) -> dict[str, Any]:
    model = mujoco.MjModel.from_xml_path(str(constraint_xml))
    data = mujoco.MjData(model)

    hand_body_ids = {
        limb_name: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, HAND_BODY_NAMES[limb_name])
        for limb_name in HAND_LIMBS
    }
    anchor_body_ids = {
        limb_name: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, ANCHOR_BODY_NAMES[limb_name])
        for limb_name in HAND_LIMBS
    }
    eq_ids = {
        limb_name: mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_EQUALITY, WELD_NAMES[limb_name])
        for limb_name in HAND_LIMBS
    }
    mocap_ids = {
        limb_name: int(model.body_mocapid[anchor_body_ids[limb_name]])
        for limb_name in HAND_LIMBS
    }

    if any(mocap_id < 0 for mocap_id in mocap_ids.values()):
        raise RuntimeError("Expected mocap anchors for both hands.")

    runtime_state = {
        limb_name: {
            "active": False,
            "hold_id": None,
            "anchor_pos": np.zeros(3, dtype=np.float64),
            "anchor_quat": np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64),
        }
        for limb_name in HAND_LIMBS
    }

    frames_out: list[dict[str, Any]] = []
    started = time.perf_counter()
    for frame in dynamic_report["frames"]:
        qpos = np.asarray(frame["qpos"], dtype=np.float64)
        qvel = np.asarray(frame["qvel"], dtype=np.float64)
        qacc = np.asarray(frame["qacc"], dtype=np.float64)

        base_mocap_pose = {
            limb_name: (runtime_state[limb_name]["anchor_pos"], runtime_state[limb_name]["anchor_quat"])
            for limb_name in HAND_LIMBS
        }
        baseline = _run_pass(
            model,
            data,
            qpos=qpos,
            qvel=qvel,
            qacc=qacc,
            eq_active={limb_name: False for limb_name in HAND_LIMBS},
            mocap_pose_by_limb=base_mocap_pose,
            eq_ids=eq_ids,
            mocap_ids=mocap_ids,
            hand_body_ids=hand_body_ids,
            top_k_joints=top_k_joints,
        )

        eq_active: dict[str, bool] = {}
        active_hold_ids: dict[str, int] = {}
        hand_summary: dict[str, Any] = {}

        for limb_name in HAND_LIMBS:
            payload = _limb_payload(frame, limb_name)
            grip_active = _is_grip_active(payload)
            active_hold_id = payload.get("active_hold_id")
            if not grip_active:
                runtime_state[limb_name]["active"] = False
                runtime_state[limb_name]["hold_id"] = None
            else:
                if (
                    not runtime_state[limb_name]["active"]
                    or runtime_state[limb_name]["hold_id"] != int(active_hold_id)
                ):
                    pos, quat = baseline["hand_pose"][limb_name]
                    runtime_state[limb_name]["anchor_pos"] = pos.copy()
                    runtime_state[limb_name]["anchor_quat"] = quat.copy()
                    runtime_state[limb_name]["active"] = True
                    runtime_state[limb_name]["hold_id"] = int(active_hold_id)
                active_hold_ids[limb_name] = int(active_hold_id)

            eq_active[limb_name] = bool(runtime_state[limb_name]["active"])
            current_hand_pos = baseline["hand_pose"][limb_name][0]
            anchor_pos = np.asarray(runtime_state[limb_name]["anchor_pos"], dtype=np.float64)
            hand_summary[limb_name] = {
                "state": str(payload.get("state", "FREE")),
                "active_hold_id": None if active_hold_id is None else int(active_hold_id),
                "weld_active": eq_active[limb_name],
                "anchor_pos_m": anchor_pos.tolist(),
                "anchor_quat_wxyz": np.asarray(runtime_state[limb_name]["anchor_quat"], dtype=np.float64).tolist(),
                "anchor_gap_translation_m": float(np.linalg.norm(current_hand_pos - anchor_pos)),
            }

        constrained_mocap_pose = {
            limb_name: (
                np.asarray(runtime_state[limb_name]["anchor_pos"], dtype=np.float64),
                np.asarray(runtime_state[limb_name]["anchor_quat"], dtype=np.float64),
            )
            for limb_name in HAND_LIMBS
        }
        constrained = _run_pass(
            model,
            data,
            qpos=qpos,
            qvel=qvel,
            qacc=qacc,
            eq_active=eq_active,
            mocap_pose_by_limb=constrained_mocap_pose,
            eq_ids=eq_ids,
            mocap_ids=mocap_ids,
            hand_body_ids=hand_body_ids,
            top_k_joints=top_k_joints,
        )

        delta_root = constrained["root_inverse_force"] - baseline["root_inverse_force"]
        delta_body = _delta_body_loads(constrained["body_loads"], baseline["body_loads"])
        frame_out = {
            "frame_index": int(frame["frame_index"]),
            "timestamp_ms": float(frame["timestamp_ms"]),
            "pose_mode": frame.get("pose_mode"),
            "phase": frame.get("phase"),
            "analysis_confidence": frame.get("analysis_confidence"),
            "constraint_mode": _constraint_mode(eq_active["left_hand"], eq_active["right_hand"]),
            "active_hold_ids": active_hold_ids,
            "hands": hand_summary,
            "baseline": {
                "root_inverse_force": baseline["root_inverse_force"].tolist(),
                "qfrc_constraint_norm": baseline["qfrc_constraint_norm"],
                "root_constraint_force": baseline["root_constraint_force"].tolist(),
                "root_constraint_force_norm": baseline["root_constraint_force_norm"],
                "efc_force_norm": baseline["efc_force_norm"],
                "top_joint_loads": baseline["top_joint_loads"],
                "body_loads": baseline["body_loads"],
            },
            "constrained": {
                "root_inverse_force": constrained["root_inverse_force"].tolist(),
                "qfrc_constraint_norm": constrained["qfrc_constraint_norm"],
                "root_constraint_force": constrained["root_constraint_force"].tolist(),
                "root_constraint_force_norm": constrained["root_constraint_force_norm"],
                "efc_force_norm": constrained["efc_force_norm"],
                "top_joint_loads": constrained["top_joint_loads"],
                "body_loads": constrained["body_loads"],
            },
            "delta": {
                "root_inverse_force": delta_root.tolist(),
                "root_inverse_force_norm": float(np.linalg.norm(delta_root)),
                "body_loads": delta_body,
                "top_joint_deltas": _top_joint_deltas(
                    constrained["joint_inverse_forces"],
                    baseline["joint_inverse_forces"],
                    top_k=top_k_joints,
                ),
            },
        }
        frames_out.append(frame_out)

    runtime_s = float(time.perf_counter() - started)
    return {
        "constraint_xml": str(constraint_xml.resolve()),
        "processed_frame_count": len(frames_out),
        "runtime_s": runtime_s,
        "summary": summarize_frames(frames_out),
        "frames": frames_out,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Prototype MuJoCo hand GRIP -> weld constraint switching over a dynamic sequence.")
    parser.add_argument("--base-xml", type=Path, default=DEFAULT_BASE_XML)
    parser.add_argument("--constraint-xml-output", type=Path, default=ROOT / "hand_grip_constraint_model.xml")
    parser.add_argument("--dynamic-report", type=Path, default=None)
    parser.add_argument("--dynamic-report-output", type=Path, default=ROOT / "dynamic_sequence_report_with_state.json")
    parser.add_argument("--input-video", type=Path, default=PROJECT_ROOT / "video" / "주황.mp4")
    parser.add_argument("--task-model", type=Path, default=CUSTOM_SKELETON_ROOT / "pose_landmarker_lite.task")
    parser.add_argument("--calibration-json", type=Path, default=CUSTOM_SKELETON_ROOT / "calibration.json")
    parser.add_argument("--detections-json", type=Path, default=PROJECT_ROOT / "detections.json")
    parser.add_argument("--ik-iters", type=int, default=45)
    parser.add_argument("--ik-damping", type=float, default=1e-3)
    parser.add_argument("--frame-step", type=int, default=1)
    parser.add_argument("--smoothing-window", type=int, default=5)
    parser.add_argument("--top-k-joints", type=int, default=5)
    parser.add_argument("--force-regenerate-dynamic-report", action="store_true")
    parser.add_argument("--output", type=Path, default=ROOT / "hand_grip_constraint_report.json")
    args = parser.parse_args()

    constraint_xml = build_hand_grip_constraint_xml(args.base_xml, args.constraint_xml_output)
    dynamic_report = load_or_generate_dynamic_report(
        dynamic_report_path=args.dynamic_report,
        dynamic_report_output=args.dynamic_report_output,
        xml_path=args.base_xml,
        video_path=args.input_video,
        task_model=args.task_model,
        calibration_json=args.calibration_json,
        detections_json=args.detections_json,
        ik_iterations=max(1, int(args.ik_iters)),
        damping=float(args.ik_damping),
        frame_step=max(1, int(args.frame_step)),
        smoothing_window=max(1, int(args.smoothing_window)),
        top_k_joints=max(1, int(args.top_k_joints)),
        force_regenerate=bool(args.force_regenerate_dynamic_report),
    )

    report = evaluate_hand_grip_constraint_sequence(
        constraint_xml=constraint_xml,
        dynamic_report=dynamic_report,
        top_k_joints=max(1, int(args.top_k_joints)),
    )
    report["base_xml"] = str(args.base_xml.resolve())
    report["dynamic_report"] = str(
        (args.dynamic_report if args.dynamic_report is not None else args.dynamic_report_output).resolve()
    )

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    summary = {
        "processed_frame_count": report["processed_frame_count"],
        "runtime_s": report["runtime_s"],
        "summary": report["summary"],
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"[OK] Wrote {args.output.resolve()}")


if __name__ == "__main__":
    main()
