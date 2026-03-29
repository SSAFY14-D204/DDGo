from __future__ import annotations

import argparse
import json
import math
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import mujoco
import numpy as np

# MediaPipe world landmark indices (BlazePose 33)
LEFT_SHOULDER = 11
RIGHT_SHOULDER = 12
LEFT_WRIST = 15
RIGHT_WRIST = 16
LEFT_HIP = 23
RIGHT_HIP = 24
LEFT_ANKLE = 27
RIGHT_ANKLE = 28

LIMB_TO_LM = {
    "left_wrist": LEFT_WRIST,
    "right_wrist": RIGHT_WRIST,
    "left_ankle": LEFT_ANKLE,
    "right_ankle": RIGHT_ANKLE,
}

LIMB_TO_MOCAP_BODY = {
    "left_wrist": "mocap_wrist_left",
    "right_wrist": "mocap_wrist_right",
    "left_ankle": "mocap_ankle_left",
    "right_ankle": "mocap_ankle_right",
}

LIMB_TO_BODY = {
    "left_wrist": "hand_left",
    "right_wrist": "hand_right",
    "left_ankle": "foot_left",
    "right_ankle": "foot_right",
}

MONITOR_JOINTS = [
    "shoulder1_left",
    "shoulder2_left",
    "elbow_left",
    "shoulder1_right",
    "shoulder2_right",
    "elbow_right",
]


@dataclass
class FrameResult:
    timestamp_ms: int
    com_stability: float
    effective_contact: bool
    failed: bool


def mp_to_mj(point_xyz: np.ndarray) -> np.ndarray:
    """Convert MediaPipe world coordinates to MuJoCo frame.

    MediaPipe world: x(right), y(down), z(depth from camera).
    MuJoCo here: x(lateral), y(depth), z(vertical).
    """
    x, y, z = point_xyz
    return np.array([x, -z, -y], dtype=np.float64)


def point_in_polygon_2d(point_xy: np.ndarray, polygon: np.ndarray) -> bool:
    x, y = point_xy
    n = polygon.shape[0]
    inside = False
    j = n - 1
    for i in range(n):
        xi, yi = polygon[i]
        xj, yj = polygon[j]
        intersects = ((yi > y) != (yj > y)) and (
            x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi
        )
        if intersects:
            inside = not inside
        j = i
    return inside


def convex_hull_2d(points: np.ndarray) -> np.ndarray:
    if len(points) <= 2:
        return points

    pts = points[np.lexsort((points[:, 1], points[:, 0]))]

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower = []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)

    upper = []
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)

    return np.array(lower[:-1] + upper[:-1], dtype=np.float64)


def distance_point_to_segment(p: np.ndarray, a: np.ndarray, b: np.ndarray) -> float:
    ab = b - a
    t = np.dot(p - a, ab) / (np.dot(ab, ab) + 1e-12)
    t = float(np.clip(t, 0.0, 1.0))
    proj = a + t * ab
    return float(np.linalg.norm(p - proj))


def support_stability_score(com_xy: np.ndarray, contacts_xy: np.ndarray, margin: float = 0.15) -> float:
    if contacts_xy.shape[0] == 0:
        return 0.0
    if contacts_xy.shape[0] == 1:
        d = np.linalg.norm(com_xy - contacts_xy[0])
        return float(np.clip(1.0 - d / margin, 0.0, 1.0))
    if contacts_xy.shape[0] == 2:
        d = distance_point_to_segment(com_xy, contacts_xy[0], contacts_xy[1])
        return float(np.clip(1.0 - d / margin, 0.0, 1.0))

    hull = convex_hull_2d(contacts_xy)
    if hull.shape[0] < 3:
        return 0.0

    inside = point_in_polygon_2d(com_xy, hull)
    # Distance to hull edges for soft score.
    dmin = 1e9
    for i in range(hull.shape[0]):
        a = hull[i]
        b = hull[(i + 1) % hull.shape[0]]
        dmin = min(dmin, distance_point_to_segment(com_xy, a, b))

    if inside:
        return float(np.clip(0.5 + dmin / margin, 0.0, 1.0))
    return float(np.clip(0.5 - dmin / margin, 0.0, 1.0))


def parse_landmarks(frame: dict[str, Any]) -> np.ndarray:
    raw = frame.get("pose_world_landmarks") or frame.get("landmarks")
    if raw is None:
        raise ValueError("Frame is missing pose_world_landmarks")
    if len(raw) < 29:
        raise ValueError("pose_world_landmarks must contain at least 29 points")

    pts = np.zeros((len(raw), 3), dtype=np.float64)
    for i, p in enumerate(raw):
        if isinstance(p, dict):
            pts[i] = [float(p["x"]), float(p["y"]), float(p["z"])]
        else:
            pts[i] = [float(p[0]), float(p[1]), float(p[2])]
    return pts


def add_mocap_and_equality(xml_text: str) -> str:
    mocap_block = """
    <body name="mocap_wrist_right" mocap="true" pos="0 -0.2 1.2">
      <geom type="sphere" size="0.02" rgba="1 0 0 0.5" contype="0" conaffinity="0"/>
    </body>
    <body name="mocap_wrist_left" mocap="true" pos="0 0.2 1.2">
      <geom type="sphere" size="0.02" rgba="0 1 0 0.5" contype="0" conaffinity="0"/>
    </body>
    <body name="mocap_ankle_right" mocap="true" pos="0 -0.1 0.3">
      <geom type="sphere" size="0.02" rgba="0 0 1 0.5" contype="0" conaffinity="0"/>
    </body>
    <body name="mocap_ankle_left" mocap="true" pos="0 0.1 0.3">
      <geom type="sphere" size="0.02" rgba="1 1 0 0.5" contype="0" conaffinity="0"/>
    </body>
"""

    equality_block = """
  <equality>
    <weld name="weld_hand_right" body1="hand_right" body2="mocap_wrist_right" solref="0.02 1" solimp="0.9 0.95 0.01"/>
    <weld name="weld_hand_left" body1="hand_left" body2="mocap_wrist_left" solref="0.02 1" solimp="0.9 0.95 0.01"/>
    <weld name="weld_foot_right" body1="foot_right" body2="mocap_ankle_right" solref="0.02 1" solimp="0.9 0.95 0.01"/>
    <weld name="weld_foot_left" body1="foot_left" body2="mocap_ankle_left" solref="0.02 1" solimp="0.9 0.95 0.01"/>
  </equality>
"""

    if "mocap_wrist_right" not in xml_text:
        xml_text = xml_text.replace("</worldbody>", f"{mocap_block}\n  </worldbody>")
    if "<equality>" not in xml_text:
        xml_text = xml_text.replace("</mujoco>", f"{equality_block}\n</mujoco>")
    return xml_text


def body_id(model: mujoco.MjModel, name: str) -> int:
    bid = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_BODY, name)
    if bid < 0:
        raise ValueError(f"Body not found: {name}")
    return bid


def joint_id(model: mujoco.MjModel, name: str) -> int:
    jid = mujoco.mj_name2id(model, mujoco.mjtObj.mjOBJ_JOINT, name)
    if jid < 0:
        raise ValueError(f"Joint not found: {name}")
    return jid


def actuator_joint_torque_limits(model: mujoco.MjModel) -> dict[str, float]:
    limits: dict[str, float] = {}
    for aid in range(model.nu):
        trnid = model.actuator_trnid[aid][0]
        if trnid < 0:
            continue
        jname = mujoco.mj_id2name(model, mujoco.mjtObj.mjOBJ_JOINT, int(trnid))
        if not jname:
            continue
        gear = float(abs(model.actuator_gear[aid][0]))
        if model.actuator_ctrllimited[aid]:
            cmin, cmax = model.actuator_ctrlrange[aid]
            torque_limit = gear * max(abs(float(cmin)), abs(float(cmax)))
        else:
            torque_limit = gear
        limits[jname] = max(limits.get(jname, 0.0), torque_limit)
    return limits


def compute_com(model: mujoco.MjModel, data: mujoco.MjData) -> np.ndarray:
    masses = model.body_mass[:, None]
    total = float(np.sum(masses))
    return np.sum(data.xipos * masses, axis=0) / max(total, 1e-9)


def normalize_and_map(
    landmarks_mp: np.ndarray,
    scale: float,
    root_offset: np.ndarray,
) -> tuple[np.ndarray, np.ndarray]:
    mapped = np.array([mp_to_mj(p) for p in landmarks_mp], dtype=np.float64) * scale
    hip_center = 0.5 * (mapped[LEFT_HIP] + mapped[RIGHT_HIP])
    mapped += (root_offset - hip_center)
    return mapped, hip_center


def determine_contacts(
    mapped_landmarks: np.ndarray,
    hold_points: np.ndarray,
    hold_radius: float,
) -> dict[str, bool]:
    if hold_points.shape[0] == 0:
        return {k: False for k in LIMB_TO_LM}

    out: dict[str, bool] = {}
    for limb, lm_idx in LIMB_TO_LM.items():
        p = mapped_landmarks[lm_idx]
        d = np.linalg.norm(hold_points - p[None, :], axis=1)
        out[limb] = bool(np.any(d <= hold_radius))
    return out


def run_worker(input_json: Path, xml_path: Path, output_json: Path) -> dict[str, Any]:
    payload = json.loads(input_json.read_text(encoding="utf-8-sig"))

    frames = payload.get("frames", [])
    if not frames:
        raise ValueError("Input JSON must include non-empty frames")

    hold_meta = payload.get("hold_metadata", {})
    holds = hold_meta.get("holds", [])
    hold_radius = float(hold_meta.get("hold_radius", 0.08))
    wall_plane_y = float(hold_meta.get("wall_plane_y", 0.0))

    hold_points = []
    for h in holds:
        hold_points.append([float(h["x"]), float(h["y"]), float(h["z"])])
    hold_points_np = np.array(hold_points, dtype=np.float64) if hold_points else np.zeros((0, 3), dtype=np.float64)

    biometrics = payload.get("user_biometrics", {})
    user_height = float(biometrics.get("height_m", 1.75))

    xml_text = xml_path.read_text(encoding="utf-8")
    xml_text = add_mocap_and_equality(xml_text)
    model = mujoco.MjModel.from_xml_string(xml_text)
    data = mujoco.MjData(model)

    torso_bid = body_id(model, "torso")
    pelvis_bid = body_id(model, "pelvis")

    mocap_ids = {}
    limb_body_ids = {}
    for limb, mocap_body in LIMB_TO_MOCAP_BODY.items():
        bid = body_id(model, mocap_body)
        mocapid = model.body_mocapid[bid]
        if mocapid < 0:
            raise ValueError(f"Body {mocap_body} is not mocap-enabled")
        mocap_ids[limb] = int(mocapid)
        limb_body_ids[limb] = body_id(model, LIMB_TO_BODY[limb])

    torque_limits = actuator_joint_torque_limits(model)

    # Scale landmarks by shoulder width to match humanoid proportions.
    first_landmarks = parse_landmarks(frames[0])
    first_map = np.array([mp_to_mj(p) for p in first_landmarks], dtype=np.float64)
    mp_shoulder = np.linalg.norm(first_map[LEFT_SHOULDER] - first_map[RIGHT_SHOULDER])

    mujoco.mj_resetData(model, data)
    mujoco.mj_forward(model, data)
    left_shoulder_bid = body_id(model, "upper_arm_left")
    right_shoulder_bid = body_id(model, "upper_arm_right")
    model_shoulder = np.linalg.norm(data.xpos[left_shoulder_bid] - data.xpos[right_shoulder_bid])

    shoulder_scale = model_shoulder / max(float(mp_shoulder), 1e-6)
    # Reference humanoid height for rough anthropometric correction.
    anthropometric = user_height / 1.75
    scale = shoulder_scale * anthropometric

    # Align hip center to torso root initial position.
    first_mapped = first_map * scale
    first_hip = 0.5 * (first_mapped[LEFT_HIP] + first_mapped[RIGHT_HIP])
    root_offset = model.qpos0[0:3] - first_hip

    stress_log: list[dict[str, Any]] = []
    frame_results: list[FrameResult] = []
    last_contacts = {k: False for k in LIMB_TO_LM}
    t_fail_ms: int | None = None

    for i, frame in enumerate(frames):
        ts = int(frame.get("timestamp_ms", i * 33))
        landmarks_mp = parse_landmarks(frame)
        mapped, _ = normalize_and_map(landmarks_mp, scale=scale, root_offset=root_offset)

        # Root alignment: map hip center to freejoint translation.
        hip_center = 0.5 * (mapped[LEFT_HIP] + mapped[RIGHT_HIP])
        data.qpos[0:3] = hip_center

        contacts = determine_contacts(mapped, hold_points_np, hold_radius)

        # Mocap targeting with depth snap on contact start.
        for limb, lm_idx in LIMB_TO_LM.items():
            target = mapped[lm_idx].copy()
            if contacts[limb] and not last_contacts[limb]:
                target[1] = wall_plane_y
            data.mocap_pos[mocap_ids[limb]] = target

        for limb in contacts:
            last_contacts[limb] = contacts[limb]

        mujoco.mj_forward(model, data)

        # Advance simulation a bit for constraint resolution.
        for _ in range(3):
            mujoco.mj_step(model, data)

        # Inverse dynamics torque estimation.
        mujoco.mj_inverse(model, data)

        for jname in MONITOR_JOINTS:
            jid = joint_id(model, jname)
            dofadr = model.jnt_dofadr[jid]
            tau = float(abs(data.qfrc_inverse[dofadr]))
            limit = float(max(torque_limits.get(jname, 1.0), 1e-6))
            ratio = tau / limit
            if ratio >= 0.8:
                stress_log.append(
                    {
                        "joint_id": jname,
                        "timestamp_ms": ts,
                        "torque": tau,
                        "torque_limit": limit,
                        "ratio": ratio,
                    }
                )

        com = compute_com(model, data)
        active_contact_points = []
        for limb, active in contacts.items():
            if active:
                active_contact_points.append(data.xpos[limb_body_ids[limb]].copy())
        contact_pts = (
            np.array(active_contact_points, dtype=np.float64)
            if active_contact_points
            else np.zeros((0, 3), dtype=np.float64)
        )
        com_score = support_stability_score(com[:2], contact_pts[:, :2])

        # Effective contact by external wrench magnitude on contact limbs.
        force_thr = float(hold_meta.get("contact_force_threshold", 8.0))
        wrench_norm = 0.0
        for limb, active in contacts.items():
            if not active:
                continue
            bid = limb_body_ids[limb]
            wrench_norm += float(np.linalg.norm(data.cfrc_ext[bid][0:3]))
        effective_contact = wrench_norm > force_thr

        # t_fail detection: downward pelvis velocity + disappearing contacts.
        pelvis_vz = float(data.cvel[pelvis_bid][5])
        active_n = sum(1 for v in contacts.values() if v)
        failed_now = False
        gravity_drop_vz = float(payload.get("gravity_drop_vz_threshold", -1.2))
        if pelvis_vz < gravity_drop_vz and active_n == 0 and t_fail_ms is None:
            t_fail_ms = ts
            failed_now = True

        frame_results.append(
            FrameResult(
                timestamp_ms=ts,
                com_stability=com_score,
                effective_contact=effective_contact,
                failed=failed_now,
            )
        )

    stability_score = float(np.mean([f.com_stability for f in frame_results]))
    contact_efficiency = float(np.mean([1.0 if f.effective_contact else 0.0 for f in frame_results]))

    output = {
        "stability_score": stability_score,
        "joint_stress_log": stress_log,
        "t_fail_timestamp": t_fail_ms,
        "contact_efficiency": contact_efficiency,
        "meta": {
            "frames": len(frame_results),
            "scale_factor": scale,
            "mocap_weld_enabled": True,
            "failure_rule": {
                "pelvis_vz_threshold": payload.get("gravity_drop_vz_threshold", -1.2),
                "requires_no_active_contacts": True,
            },
            "axis_note": "MuJoCo uses z-up. Downward fail check is pelvis vz < threshold.",
        },
    }

    output_json.write_text(json.dumps(output, indent=2), encoding="utf-8")
    return output


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="3D pose lifting -> MuJoCo mapping worker")
    p.add_argument("--input", required=True, help="Input JSON path")
    p.add_argument("--xml", default=str(Path(__file__).with_name("humanoid.xml")), help="Base humanoid XML path")
    p.add_argument("--output", default=str(Path(__file__).with_name("analysis_output.json")), help="Output JSON path")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    result = run_worker(Path(args.input), Path(args.xml), Path(args.output))
    print("[OK] Analysis complete")
    print(json.dumps({
        "stability_score": result["stability_score"],
        "joint_stress_log_count": len(result["joint_stress_log"]),
        "t_fail_timestamp": result["t_fail_timestamp"],
        "contact_efficiency": result["contact_efficiency"],
    }, indent=2))


if __name__ == "__main__":
    main()
