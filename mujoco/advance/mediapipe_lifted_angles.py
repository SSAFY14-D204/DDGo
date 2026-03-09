from __future__ import annotations

import argparse
import json
import math
import time
from collections import deque
from pathlib import Path

import cv2
import mediapipe as mp
import mujoco
import mujoco.viewer
import numpy as np

from mediapipe_mujoco_verify import (
    MAJOR_JOINTS,
    OneEuroFilter,
    actuator_id,
    body_id,
    build_model,
    joint_id,
    make_landmarker,
    normalize,
    quat_from_axes,
)

# MediaPipe indices
L_SH, R_SH = 11, 12
L_EL, R_EL = 13, 14
L_WR, R_WR = 15, 16
L_TH, R_TH = 21, 22
L_PK, R_PK = 17, 18
L_HI, R_HI = 23, 24
L_KN, R_KN = 25, 26
L_AN, R_AN = 27, 28
L_HE, R_HE = 29, 30
L_TO, R_TO = 31, 32

# H36M-17 order
P, RH, RK, RA, LH, LK, LA, SP, TH, NK, HD, LS, LE, LW, RS, RE, RW = range(17)


def vis(lm) -> float:
    return float(getattr(lm, "visibility", 1.0))


def mp3d_to_mj(v: np.ndarray) -> np.ndarray:
    # MP-like coords (x right, y down, z depth) -> MuJoCo (x forward, y left, z up)
    return np.array([v[2], -v[0], -v[1]], dtype=np.float64)


def lerp_targets(a: dict[str, float], b: dict[str, float], t: float) -> dict[str, float]:
    t = float(np.clip(t, 0.0, 1.0))
    return {k: (1.0 - t) * float(a.get(k, b.get(k, 0.0))) + t * float(b.get(k, a.get(k, 0.0))) for k in MAJOR_JOINTS}


def map_mp33_to_h36m17(landmarks) -> tuple[np.ndarray, np.ndarray, dict[str, np.ndarray], dict[str, float]]:
    pts = np.array([[float(lm.x), float(lm.y)] for lm in landmarks], dtype=np.float64)
    vv = np.array([vis(lm) for lm in landmarks], dtype=np.float64)

    pelvis = 0.5 * (pts[L_HI] + pts[R_HI])
    thorax = 0.5 * (pts[L_SH] + pts[R_SH])
    neck = 0.5 * (pelvis + thorax)
    spine = 0.5 * (pelvis + thorax)
    head = pts[0]

    j = np.zeros((17, 2), dtype=np.float64)
    c = np.zeros((17,), dtype=np.float64)
    j[P], c[P] = pelvis, 0.5 * (vv[L_HI] + vv[R_HI])
    j[RH], c[RH] = pts[R_HI], vv[R_HI]
    j[RK], c[RK] = pts[R_KN], vv[R_KN]
    j[RA], c[RA] = pts[R_AN], vv[R_AN]
    j[LH], c[LH] = pts[L_HI], vv[L_HI]
    j[LK], c[LK] = pts[L_KN], vv[L_KN]
    j[LA], c[LA] = pts[L_AN], vv[L_AN]
    j[SP], c[SP] = spine, c[P]
    j[TH], c[TH] = thorax, 0.5 * (vv[L_SH] + vv[R_SH])
    j[NK], c[NK] = neck, c[TH]
    j[HD], c[HD] = head, vv[0]
    j[LS], c[LS] = pts[L_SH], vv[L_SH]
    j[LE], c[LE] = pts[L_EL], vv[L_EL]
    j[LW], c[LW] = pts[L_WR], vv[L_WR]
    j[RS], c[RS] = pts[R_SH], vv[R_SH]
    j[RE], c[RE] = pts[R_EL], vv[R_EL]
    j[RW], c[RW] = pts[R_WR], vv[R_WR]

    shoulder_w = max(float(np.linalg.norm(pts[L_SH] - pts[R_SH])), 1e-4)
    j = (j - pelvis[None, :]) / shoulder_w

    aux2d = {
        "left_thumb": pts[L_TH], "right_thumb": pts[R_TH],
        "left_pinky": pts[L_PK], "right_pinky": pts[R_PK],
        "left_heel": pts[L_HE], "right_heel": pts[R_HE],
        "left_toe": pts[L_TO], "right_toe": pts[R_TO],
        "left_wrist": pts[L_WR], "right_wrist": pts[R_WR],
        "left_ankle": pts[L_AN], "right_ankle": pts[R_AN],
    }
    auxc = {
        "left_wrist": float(vv[L_WR]), "right_wrist": float(vv[R_WR]),
        "left_ankle": float(vv[L_AN]), "right_ankle": float(vv[R_AN]),
    }
    return j, c, aux2d, auxc


class TemporalLifter:
    """VideoPose3D-like temporal stabilizer base."""

    def __init__(self, window: int = 27, alpha: float = 0.35) -> None:
        self.buf: deque[np.ndarray] = deque(maxlen=max(window, 3))
        self.alpha = float(np.clip(alpha, 0.05, 1.0))
        self.prev: np.ndarray | None = None
        self.bones = [
            (P, RH, 0.27, 0.00), (RH, RK, 0.45, 0.35), (RK, RA, 0.44, 0.35),
            (P, LH, 0.27, 0.00), (LH, LK, 0.45, 0.35), (LK, LA, 0.44, 0.35),
            (P, SP, 0.25, 0.00), (SP, TH, 0.22, 0.00), (TH, NK, 0.18, -0.05), (NK, HD, 0.20, -0.05),
            (TH, LS, 0.25, 0.05), (LS, LE, 0.35, 0.45), (LE, LW, 0.30, 0.45),
            (TH, RS, 0.25, 0.05), (RS, RE, 0.35, 0.45), (RE, RW, 0.30, 0.45),
        ]
        self.lengths = {(a, b): d for a, b, d, _ in self.bones}

    def update(self, j2d: np.ndarray) -> np.ndarray:
        self.buf.append(j2d.copy())
        arr = np.stack(list(self.buf), axis=0)
        w = np.linspace(0.5, 1.0, arr.shape[0], dtype=np.float64)
        w /= np.sum(w)
        s2d = np.tensordot(w, arr, axes=(0, 0))

        for a, b, _, _ in self.bones:
            dxy = float(np.linalg.norm(s2d[b] - s2d[a]))
            self.lengths[(a, b)] = 0.95 * self.lengths[(a, b)] + 0.05 * max(dxy, 1e-3)

        xyz = np.zeros((17, 3), dtype=np.float64)
        xyz[:, :2] = s2d
        for a, b, _, s in self.bones:
            dxy = float(np.linalg.norm(xyz[b, :2] - xyz[a, :2]))
            L = max(float(self.lengths[(a, b)]), dxy + 1e-3)
            dz = math.sqrt(max(L * L - dxy * dxy, 0.0))
            xyz[b, 2] = xyz[a, 2] + s * dz
        xyz -= xyz[P]

        if self.prev is None:
            out = xyz
        else:
            out = (1.0 - self.alpha) * self.prev + self.alpha * xyz
        self.prev = out
        return out.copy()


def supplement_hands_feet(core: np.ndarray, aux2d: dict[str, np.ndarray]) -> dict[str, np.ndarray]:
    pts = {
        "left_shoulder": core[LS].copy(), "right_shoulder": core[RS].copy(),
        "left_elbow": core[LE].copy(), "right_elbow": core[RE].copy(),
        "left_wrist": core[LW].copy(), "right_wrist": core[RW].copy(),
        "left_hip": core[LH].copy(), "right_hip": core[RH].copy(),
        "left_knee": core[LK].copy(), "right_knee": core[RK].copy(),
        "left_ankle": core[LA].copy(), "right_ankle": core[RA].copy(),
    }

    for side in ("left", "right"):
        wr = pts[f"{side}_wrist"]
        el = pts[f"{side}_elbow"]
        fore = normalize(wr - el)
        hv2 = normalize(np.array([*(aux2d[f"{side}_pinky"] - aux2d[f"{side}_thumb"]), 0.0], dtype=np.float64))
        hand_dir = normalize(0.65 * fore + 0.35 * hv2)
        hand_len = max(0.45 * float(np.linalg.norm(wr - el)), 0.05)
        pts[f"{side}_hand_tip"] = wr + hand_len * hand_dir

        an = pts[f"{side}_ankle"]
        kn = pts[f"{side}_knee"]
        shank = normalize(an - kn)
        fv2 = normalize(np.array([*(aux2d[f"{side}_toe"] - aux2d[f"{side}_heel"]), 0.0], dtype=np.float64))
        foot_dir = normalize(0.70 * fv2 + 0.30 * shank)
        foot_len = max(0.55 * float(np.linalg.norm(an - kn)), 0.06)
        pts[f"{side}_toe"] = an + foot_len * foot_dir
        pts[f"{side}_heel"] = an - 0.35 * foot_len * foot_dir

    return pts


class ZSnapper:
    def __init__(self, wall_z: float, speed_thr: float = 0.008) -> None:
        self.wall_z = wall_z
        self.speed_thr = speed_thr
        self.prev: dict[str, np.ndarray] = {}

    def apply(self, pts: dict[str, np.ndarray], aux2d: dict[str, np.ndarray], auxc: dict[str, float]) -> dict[str, bool]:
        flags = {"left_wrist": False, "right_wrist": False, "left_ankle": False, "right_ankle": False}
        for k in flags:
            if auxc.get(k, 0.0) < 0.4:
                continue
            uv = aux2d[k]
            spd = 0.0 if k not in self.prev else float(np.linalg.norm(uv - self.prev[k]))
            self.prev[k] = uv.copy()
            if spd > self.speed_thr:
                continue
            pts[k][2] = self.wall_z
            flags[k] = True
            if k == "left_ankle":
                pts["left_toe"][2] = self.wall_z
                pts["left_heel"][2] = self.wall_z
            if k == "right_ankle":
                pts["right_toe"][2] = self.wall_z
                pts["right_heel"][2] = self.wall_z
        return flags


def extract_joint_targets(points_mj: dict[str, np.ndarray]) -> tuple[dict[str, float], dict[str, np.ndarray]]:
    ls, rs = points_mj["left_shoulder"], points_mj["right_shoulder"]
    le, re = points_mj["left_elbow"], points_mj["right_elbow"]
    lw, rw = points_mj["left_wrist"], points_mj["right_wrist"]
    lh, rh = points_mj["left_hip"], points_mj["right_hip"]
    lk, rk = points_mj["left_knee"], points_mj["right_knee"]
    la, ra = points_mj["left_ankle"], points_mj["right_ankle"]
    lheel, rheel = points_mj["left_heel"], points_mj["right_heel"]
    ltoe, rtoe = points_mj["left_toe"], points_mj["right_toe"]

    shoulder_mid = 0.5 * (ls + rs)
    hip_mid = 0.5 * (lh + rh)
    up = normalize(shoulder_mid - hip_mid)
    left_axis = normalize(ls - rs)
    fwd = normalize(np.cross(left_axis, up))
    if np.linalg.norm(fwd) < 1e-6:
        fwd = np.array([1.0, 0.0, 0.0], dtype=np.float64)
    left_axis = normalize(np.cross(up, fwd))

    def tf(v: np.ndarray) -> np.ndarray:
        return np.array([np.dot(v, fwd), np.dot(v, left_axis), np.dot(v, up)], dtype=np.float64)

    def ang3(a: np.ndarray, b: np.ndarray, c: np.ndarray) -> float:
        ba, bc = a - b, c - b
        d = np.linalg.norm(ba) * np.linalg.norm(bc)
        if d < 1e-8:
            return math.pi
        return math.acos(float(np.clip(np.dot(ba, bc) / d, -1.0, 1.0)))

    torso = normalize(shoulder_mid - hip_mid)
    abdomen_x = math.atan2(torso[1], max(torso[2], 1e-6))
    abdomen_y = math.atan2(torso[0], max(torso[2], 1e-6))

    def arm(sh: np.ndarray, el: np.ndarray, wr: np.ndarray, sgn: float) -> tuple[float, float, float]:
        u = tf(normalize(0.7 * normalize(el - sh) + 0.3 * normalize(wr - sh)))
        return math.atan2(sgn * u[1], max(-u[2], 1e-6)), math.atan2(u[0], max(-u[2], 1e-6)), -(math.pi - ang3(sh, el, wr))

    def leg(hi: np.ndarray, kn: np.ndarray, an: np.ndarray, he: np.ndarray, to: np.ndarray, sgn: float):
        th = tf(normalize(kn - hi))
        ft = tf(normalize(to - he))
        hip_abd = math.atan2(sgn * th[1], max(-th[2], 1e-6))
        hip_flex = math.atan2(th[0], max(-th[2], 1e-6))
        knee_q = -(math.pi - ang3(hi, kn, an))
        ankle_y = math.atan2(ft[0], max(abs(ft[2]), 1e-6))
        ankle_x = math.atan2(sgn * ft[1], max(abs(ft[2]), 1e-6))
        return hip_abd, 0.0, -hip_flex, knee_q, ankle_y, ankle_x

    s1r, s2r, er = arm(rs, re, rw, 1.0)
    s1l, s2l, el = arm(ls, le, lw, -1.0)
    hxr, hzr, hyr, kr, ayr, axr = leg(rh, rk, ra, rheel, rtoe, 1.0)
    hxl, hzl, hyl, kl, ayl, axl = leg(lh, lk, la, lheel, ltoe, -1.0)

    targets = {
        "abdomen_z": 0.0, "abdomen_y": abdomen_y, "abdomen_x": abdomen_x,
        "hip_x_right": hxr, "hip_z_right": hzr, "hip_y_right": hyr, "knee_right": kr, "ankle_y_right": ayr, "ankle_x_right": axr,
        "hip_x_left": hxl, "hip_z_left": hzl, "hip_y_left": hyl, "knee_left": kl, "ankle_y_left": ayl, "ankle_x_left": axl,
        "shoulder1_right": s1r, "shoulder2_right": s2r, "elbow_right": er,
        "shoulder1_left": s1l, "shoulder2_left": s2l, "elbow_left": el,
    }
    axes = {"forward": fwd, "left": left_axis, "up": up}
    return targets, axes


class AngleController:
    def __init__(self, model: mujoco.MjModel, data: mujoco.MjData, mode: str, min_cutoff: float, beta: float, d_cutoff: float) -> None:
        self.model, self.data, self.mode = model, data, mode
        self.filt = OneEuroFilter(min_cutoff=min_cutoff, beta=beta, d_cutoff=d_cutoff)
        self.jids = {n: joint_id(model, n) for n in MAJOR_JOINTS}
        self.qadr = {n: int(model.jnt_qposadr[j]) for n, j in self.jids.items()}
        self.jlim = {n: (float(model.jnt_range[j][0]), float(model.jnt_range[j][1])) if model.jnt_limited[j] else (-1e9, 1e9) for n, j in self.jids.items()}
        self.aids = {n: actuator_id(model, f"{n}_pos") for n in MAJOR_JOINTS}
        self.root_pos = data.qpos[0:3].copy()
        self.root_quat = data.qpos[3:7].copy()

    def clip(self, t: dict[str, float]) -> dict[str, float]:
        return {k: float(np.clip(v, self.jlim[k][0], self.jlim[k][1])) for k, v in t.items()}

    def filter(self, t: dict[str, float], ts: float) -> dict[str, float]:
        return self.clip(self.filt.apply(self.clip(t), ts))

    def apply_kinematic(self, t: dict[str, float], axes: dict[str, np.ndarray]) -> None:
        q = quat_from_axes(axes["forward"], axes["left"], axes["up"])
        self.root_quat = normalize(0.7 * self.root_quat + 0.3 * q)
        self.data.qpos[3:7] = self.root_quat
        for n, v in t.items():
            self.data.qpos[self.qadr[n]] = v
        self.data.qvel[:] = 0.0
        self.data.qacc[:] = 0.0
        mujoco.mj_forward(self.model, self.data)

    def set_ctrl(self, t: dict[str, float]) -> None:
        for n, v in t.items():
            aid = self.aids[n]
            if self.model.actuator_ctrllimited[aid]:
                lo, hi = self.model.actuator_ctrlrange[aid]
                v = float(np.clip(v, lo, hi))
            self.data.ctrl[aid] = v


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Hybrid lifted-angle pipeline (base structure)")
    p.add_argument("--xml", default=str(Path(__file__).with_name("humanoid.xml")))
    p.add_argument("--task-model", default=str(Path(__file__).with_name("pose_landmarker_lite.task")))
    p.add_argument("--video", default="")
    p.add_argument("--camera", type=int, default=0)
    p.add_argument("--mode", choices=["kinematic", "dynamic"], default="dynamic")
    p.add_argument("--window-size", type=int, default=27)
    p.add_argument("--wall-z", type=float, default=0.85)
    p.add_argument("--max-frames", type=int, default=0)
    p.add_argument("--sync-fps", type=float, default=0.0)
    p.add_argument("--filter-min-cutoff", type=float, default=1.2)
    p.add_argument("--filter-beta", type=float, default=0.08)
    p.add_argument("--filter-d-cutoff", type=float, default=1.0)
    p.add_argument("--error-log", default=str(Path(__file__).with_name("artifacts") / "lifted_angle_log.jsonl"))
    p.add_argument("--no-display", action="store_true")
    return p.parse_args()


def main() -> None:
    args = parse_args()
    model, data = build_model(Path(args.xml).resolve())
    ctrl = AngleController(model, data, args.mode, args.filter_min_cutoff, args.filter_beta, args.filter_d_cutoff)
    lifter = TemporalLifter(window=args.window_size)
    snapper = ZSnapper(wall_z=args.wall_z)

    cap = cv2.VideoCapture(str(Path(args.video).resolve())) if args.video else cv2.VideoCapture(args.camera)
    if not cap.isOpened():
        raise RuntimeError("Could not open input source")
    src_fps = float(cap.get(cv2.CAP_PROP_FPS) or 0.0)
    sync_fps = args.sync_fps if args.sync_fps > 0 else (src_fps if src_fps > 1 else 30.0)

    log_path = Path(args.error_log).resolve()
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_fp = log_path.open("w", encoding="utf-8")

    prev_targets: dict[str, float] | None = None
    frame_idx = 0
    step_total = 0
    t0 = time.time()

    try:
        with make_landmarker(Path(args.task_model).resolve()) as landmarker, mujoco.viewer.launch_passive(model, data) as viewer:
            while viewer.is_running():
                ok, frame = cap.read()
                if not ok:
                    break
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                ts_ms = int(round(frame_idx * 1000.0 / sync_fps))
                res = landmarker.detect_for_video(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb), ts_ms)

                frame_targets = None
                contact = {"left_wrist": False, "right_wrist": False, "left_ankle": False, "right_ankle": False}
                axes = {"forward": np.array([1.0, 0.0, 0.0]), "left": np.array([0.0, 1.0, 0.0]), "up": np.array([0.0, 0.0, 1.0])}
                if res.pose_landmarks:
                    j2d, _, aux2d, auxc = map_mp33_to_h36m17(res.pose_landmarks[0])
                    core = lifter.update(j2d)
                    pts = supplement_hands_feet(core, aux2d)
                    contact = snapper.apply(pts, aux2d, auxc)
                    pts_mj = {k: mp3d_to_mj(v) for k, v in pts.items()}
                    raw_targets, axes = extract_joint_targets(pts_mj)
                    frame_targets = ctrl.filter(raw_targets, ts_ms / 1000.0)
                    if args.mode == "kinematic":
                        ctrl.apply_kinematic(frame_targets, axes)

                target_time = (frame_idx + 1) / sync_fps
                steps = 0
                if args.mode == "dynamic":
                    to_t = frame_targets if frame_targets is not None else prev_targets
                    from_t = prev_targets if prev_targets is not None else to_t
                    start_t = float(data.time)
                    dur = max(target_time - start_t, model.opt.timestep)
                    while data.time + model.opt.timestep * 0.5 < target_time:
                        if to_t is not None:
                            phase = (data.time + model.opt.timestep - start_t) / dur
                            ctrl.set_ctrl(lerp_targets(from_t or to_t, to_t, phase))
                        mujoco.mj_step(model, data)
                        steps += 1
                    step_total += steps
                else:
                    data.time = target_time

                if frame_targets is not None:
                    prev_targets = dict(frame_targets)

                viewer.sync()
                if res.pose_landmarks:
                    for lm in res.pose_landmarks[0]:
                        x, y = int(lm.x * frame.shape[1]), int(lm.y * frame.shape[0])
                        if 0 <= x < frame.shape[1] and 0 <= y < frame.shape[0]:
                            cv2.circle(frame, (x, y), 2, (0, 255, 0), -1)
                cv2.putText(frame, f"mode={args.mode} window={args.window_size} steps={step_total}", (10, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
                cv2.putText(frame, f"snap Lw={int(contact['left_wrist'])} Rw={int(contact['right_wrist'])} La={int(contact['left_ankle'])} Ra={int(contact['right_ankle'])}", (10, 50), cv2.FONT_HERSHEY_SIMPLEX, 0.52, (0, 255, 255), 2)
                if not args.no_display:
                    cv2.imshow("Hybrid Lifted Angles", frame)
                    if cv2.waitKey(1) & 0xFF == ord("q"):
                        break

                log_fp.write(json.dumps({"frame_index": frame_idx, "timestamp_ms": ts_ms, "targets": frame_targets, "contact_flags": contact, "mj_time_s": float(data.time), "mj_steps_this_frame": steps}, ensure_ascii=False) + "\n")
                frame_idx += 1
                if frame_idx % 30 == 0:
                    fps = frame_idx / max(time.time() - t0, 1e-6)
                    print(f"[INFO] frames={frame_idx} fps~{fps:.1f} steps={step_total}")
                if args.max_frames > 0 and frame_idx >= args.max_frames:
                    break
    finally:
        log_fp.close()
        cap.release()
        cv2.destroyAllWindows()

    print("[OK] Finished lifted-angle base pipeline")
    print(f"[INFO] log={log_path}")


if __name__ == "__main__":
    main()
