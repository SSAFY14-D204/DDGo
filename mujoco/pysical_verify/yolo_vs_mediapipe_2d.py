from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from ultralytics import YOLO

# MediaPipe BlazePose landmark indices
MP_LEFT_HIP = 23
MP_RIGHT_HIP = 24
MP_LEFT_WRIST = 15
MP_RIGHT_WRIST = 16
MP_LEFT_ANKLE = 27
MP_RIGHT_ANKLE = 28

# COCO keypoint indices (YOLO pose)
YOLO_LEFT_HIP = 11
YOLO_RIGHT_HIP = 12
YOLO_LEFT_WRIST = 9
YOLO_RIGHT_WRIST = 10
YOLO_LEFT_ANKLE = 15
YOLO_RIGHT_ANKLE = 16

YOLO_SKELETON = [
    (5, 6),
    (5, 7),
    (7, 9),
    (6, 8),
    (8, 10),
    (5, 11),
    (6, 12),
    (11, 12),
    (11, 13),
    (13, 15),
    (12, 14),
    (14, 16),
]

MP_SKELETON = [
    (11, 12),
    (11, 13),
    (13, 15),
    (12, 14),
    (14, 16),
    (11, 23),
    (12, 24),
    (23, 24),
    (23, 25),
    (25, 27),
    (27, 29),
    (27, 31),
    (24, 26),
    (26, 28),
    (28, 30),
    (28, 32),
]

class StabilityStats:
    def __init__(self) -> None:
        self.frame_count = 0
        self.dropout_count = 0
        self.prev_joint: np.ndarray | None = None
        self.jitter_sum = 0.0
        self.jitter_count = 0
        self.conf_sum = 0.0
        self.conf_count = 0

    def update(
        self,
        critical_ok: bool,
        joint_xy: np.ndarray | None,
        conf_value: float,
    ) -> float | None:
        self.frame_count += 1
        if not critical_ok:
            self.dropout_count += 1

        jitter_px = None
        if joint_xy is not None:
            if self.prev_joint is not None:
                jitter_px = float(np.linalg.norm(joint_xy - self.prev_joint))
                self.jitter_sum += jitter_px
                self.jitter_count += 1
            self.prev_joint = joint_xy.copy()

        self.conf_sum += float(conf_value)
        self.conf_count += 1
        return jitter_px

    @property
    def avg_jitter(self) -> float:
        if self.jitter_count == 0:
            return 0.0
        return self.jitter_sum / self.jitter_count

    @property
    def avg_conf(self) -> float:
        if self.conf_count == 0:
            return 0.0
        return self.conf_sum / self.conf_count

    @property
    def dropout_rate(self) -> float:
        if self.frame_count == 0:
            return 0.0
        return self.dropout_count / self.frame_count


def make_landmarker(task_path: Path) -> vision.PoseLandmarker:
    options = vision.PoseLandmarkerOptions(
        base_options=python.BaseOptions(model_asset_path=str(task_path)),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )
    return vision.PoseLandmarker.create_from_options(options)


def draw_text_block(frame: np.ndarray, title: str, stats: StabilityStats, color: tuple[int, int, int]) -> None:
    cv2.putText(frame, title, (10, 24), cv2.FONT_HERSHEY_SIMPLEX, 0.7, color, 2)
    cv2.putText(
        frame,
        f"Jitter(avg): {stats.avg_jitter:.2f}px",
        (10, 52),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.6,
        color,
        2,
    )
    cv2.putText(
        frame,
        f"Confidence(avg): {stats.avg_conf:.3f}",
        (10, 78),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.6,
        color,
        2,
    )
    cv2.putText(
        frame,
        f"Dropout: {stats.dropout_count}/{stats.frame_count} ({stats.dropout_rate * 100.0:.1f}%)",
        (10, 104),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.6,
        color,
        2,
    )


def get_mp_visibility(lm) -> float:
    if hasattr(lm, "visibility"):
        return float(lm.visibility)
    return 1.0


def draw_mp_pose(frame: np.ndarray, landmarks, color: tuple[int, int, int]) -> None:
    h, w = frame.shape[:2]
    for a, b in MP_SKELETON:
        pa = landmarks[a]
        pb = landmarks[b]
        xa, ya = int(pa.x * w), int(pa.y * h)
        xb, yb = int(pb.x * w), int(pb.y * h)
        if 0 <= xa < w and 0 <= ya < h and 0 <= xb < w and 0 <= yb < h:
            cv2.line(frame, (xa, ya), (xb, yb), color, 2)

    for p in landmarks:
        x, y = int(p.x * w), int(p.y * h)
        if 0 <= x < w and 0 <= y < h:
            cv2.circle(frame, (x, y), 3, color, -1)


def pick_yolo_pose(result) -> tuple[np.ndarray | None, np.ndarray | None]:
    if result.keypoints is None or len(result.keypoints) == 0:
        return None, None

    idx = 0
    if result.boxes is not None and len(result.boxes) > 0 and result.boxes.conf is not None:
        confs = result.boxes.conf.detach().cpu().numpy()
        idx = int(np.argmax(confs))

    xy = result.keypoints.xy[idx].detach().cpu().numpy()  # (K, 2)
    if result.keypoints.conf is None:
        kp_conf = np.ones((xy.shape[0],), dtype=np.float32)
    else:
        kp_conf = result.keypoints.conf[idx].detach().cpu().numpy()  # (K,)
    return xy, kp_conf


def draw_yolo_pose(frame: np.ndarray, xy: np.ndarray, conf: np.ndarray, color: tuple[int, int, int], thr: float) -> None:
    h, w = frame.shape[:2]

    for a, b in YOLO_SKELETON:
        if a >= len(xy) or b >= len(xy):
            continue
        if conf[a] < thr or conf[b] < thr:
            continue
        xa, ya = int(xy[a][0]), int(xy[a][1])
        xb, yb = int(xy[b][0]), int(xy[b][1])
        if 0 <= xa < w and 0 <= ya < h and 0 <= xb < w and 0 <= yb < h:
            cv2.line(frame, (xa, ya), (xb, yb), color, 2)

    for i in range(len(xy)):
        if conf[i] < thr:
            continue
        x, y = int(xy[i][0]), int(xy[i][1])
        if 0 <= x < w and 0 <= y < h:
            cv2.circle(frame, (x, y), 3, color, -1)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Side-by-side 2D robustness benchmark: MediaPipe Pose vs YOLO Pose",
    )
    parser.add_argument("--video", default="", help="Input video path (if empty, webcam is used)")
    parser.add_argument("--camera", type=int, default=0, help="Webcam index")
    parser.add_argument(
        "--task-model",
        default=str(Path(__file__).with_name("pose_landmarker_lite.task")),
        help="MediaPipe Pose Landmarker .task path",
    )
    parser.add_argument("--yolo-model", default="yolo11n-pose.pt", help="YOLO pose model path/name")
    parser.add_argument("--max-frames", type=int, default=0, help="Stop after N frames (0 = no limit)")
    parser.add_argument("--mp-vis-thr", type=float, default=0.5, help="MediaPipe visibility threshold")
    parser.add_argument("--yolo-kpt-thr", type=float, default=0.35, help="YOLO keypoint confidence threshold")
    parser.add_argument("--yolo-det-conf", type=float, default=0.25, help="YOLO detection confidence threshold")
    parser.add_argument("--imgsz", type=int, default=640, help="YOLO inference image size")
    parser.add_argument(
        "--log",
        default=str(Path(__file__).with_name("artifacts") / "yolo_vs_mediapipe_2d_metrics.jsonl"),
        help="JSONL output log path",
    )
    parser.add_argument("--save-video", default="", help="Optional side-by-side output video path")
    parser.add_argument("--no-display", action="store_true", help="Disable OpenCV display window")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    video_path = Path(args.video).resolve() if args.video else None
    task_path = Path(args.task_model).resolve()
    log_path = Path(args.log).resolve()

    if not task_path.exists():
        raise FileNotFoundError(f"MediaPipe task file not found: {task_path}")

    if video_path is not None:
        if not video_path.exists():
            raise FileNotFoundError(f"Video not found: {video_path}")
        cap = cv2.VideoCapture(str(video_path))
        source_desc = f"video={video_path}"
    else:
        cap = cv2.VideoCapture(args.camera)
        source_desc = f"camera={args.camera}"

    if not cap.isOpened():
        raise RuntimeError(f"Could not open input source: {source_desc}")

    yolo = YOLO(args.yolo_model)

    cap_fps = float(cap.get(cv2.CAP_PROP_FPS) or 0.0)
    if cap_fps < 1.0:
        cap_fps = 30.0

    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_fp = log_path.open("w", encoding="utf-8")

    writer = None
    mp_stats = StabilityStats()
    yolo_stats = StabilityStats()

    print(f"[INFO] Source: {source_desc}, FPS={cap_fps:.2f}")
    print(f"[INFO] YOLO model: {args.yolo_model}")

    frame_idx = 0
    t0 = time.time()

    try:
        with make_landmarker(task_path) as landmarker:
            while True:
                ok, frame = cap.read()
                if not ok:
                    print("[INFO] End of input stream or frame read failed.")
                    break

                h, w = frame.shape[:2]
                mp_frame = frame.copy()
                yolo_frame = frame.copy()

                ts_ms = int(round(frame_idx * (1000.0 / cap_fps)))

                # --- MediaPipe ---
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                mp_result = landmarker.detect_for_video(mp_image, ts_ms)

                mp_critical_ok = False
                mp_joint_xy = None
                mp_conf = 0.0
                if mp_result.pose_landmarks:
                    lms = mp_result.pose_landmarks[0]
                    draw_mp_pose(mp_frame, lms, color=(0, 255, 0))

                    vis = np.array([get_mp_visibility(p) for p in lms], dtype=np.float32)
                    mp_conf = float(np.mean(vis))

                    critical_idx = [MP_LEFT_WRIST, MP_RIGHT_WRIST, MP_LEFT_ANKLE, MP_RIGHT_ANKLE]
                    mp_critical_ok = bool(np.all(vis[critical_idx] >= args.mp_vis_thr))

                    if vis[MP_LEFT_HIP] >= args.mp_vis_thr and vis[MP_RIGHT_HIP] >= args.mp_vis_thr:
                        mp_joint_xy = np.array(
                            [
                                (lms[MP_LEFT_HIP].x + lms[MP_RIGHT_HIP].x) * 0.5 * w,
                                (lms[MP_LEFT_HIP].y + lms[MP_RIGHT_HIP].y) * 0.5 * h,
                            ],
                            dtype=np.float32,
                        )

                mp_jitter = mp_stats.update(mp_critical_ok, mp_joint_xy, mp_conf)

                # --- YOLO ---
                yolo_result = yolo.predict(
                    source=frame,
                    verbose=False,
                    conf=args.yolo_det_conf,
                    imgsz=args.imgsz,
                    max_det=1,
                )[0]

                yolo_xy, yolo_kconf = pick_yolo_pose(yolo_result)
                yolo_critical_ok = False
                yolo_joint_xy = None
                yolo_conf = 0.0

                if yolo_xy is not None and yolo_kconf is not None:
                    draw_yolo_pose(yolo_frame, yolo_xy, yolo_kconf, color=(255, 0, 0), thr=args.yolo_kpt_thr)

                    yolo_conf = float(np.mean(yolo_kconf))
                    critical_idx = [YOLO_LEFT_WRIST, YOLO_RIGHT_WRIST, YOLO_LEFT_ANKLE, YOLO_RIGHT_ANKLE]
                    yolo_critical_ok = bool(np.all(yolo_kconf[critical_idx] >= args.yolo_kpt_thr))

                    if yolo_kconf[YOLO_LEFT_HIP] >= args.yolo_kpt_thr and yolo_kconf[YOLO_RIGHT_HIP] >= args.yolo_kpt_thr:
                        yolo_joint_xy = np.array(
                            [
                                (yolo_xy[YOLO_LEFT_HIP][0] + yolo_xy[YOLO_RIGHT_HIP][0]) * 0.5,
                                (yolo_xy[YOLO_LEFT_HIP][1] + yolo_xy[YOLO_RIGHT_HIP][1]) * 0.5,
                            ],
                            dtype=np.float32,
                        )

                yolo_jitter = yolo_stats.update(yolo_critical_ok, yolo_joint_xy, yolo_conf)

                draw_text_block(mp_frame, "MediaPipe Pose", mp_stats, color=(0, 255, 0))
                draw_text_block(yolo_frame, "YOLO Pose", yolo_stats, color=(255, 0, 0))

                if mp_jitter is not None:
                    cv2.putText(
                        mp_frame,
                        f"Jitter(this): {mp_jitter:.2f}px",
                        (10, 130),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.55,
                        (0, 255, 0),
                        2,
                    )
                if yolo_jitter is not None:
                    cv2.putText(
                        yolo_frame,
                        f"Jitter(this): {yolo_jitter:.2f}px",
                        (10, 130),
                        cv2.FONT_HERSHEY_SIMPLEX,
                        0.55,
                        (255, 0, 0),
                        2,
                    )

                side_by_side = np.concatenate([mp_frame, yolo_frame], axis=1)

                if writer is None and args.save_video:
                    save_path = Path(args.save_video).resolve()
                    save_path.parent.mkdir(parents=True, exist_ok=True)
                    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
                    writer = cv2.VideoWriter(str(save_path), fourcc, cap_fps, (side_by_side.shape[1], side_by_side.shape[0]))
                    if not writer.isOpened():
                        raise RuntimeError(f"Could not open output video writer: {save_path}")
                    print(f"[INFO] Writing side-by-side output: {save_path}")

                if writer is not None:
                    writer.write(side_by_side)

                record = {
                    "frame_index": frame_idx,
                    "timestamp_ms": ts_ms,
                    "mediapipe": {
                        "critical_ok": mp_critical_ok,
                        "jitter_px": mp_jitter,
                        "avg_jitter_px": mp_stats.avg_jitter,
                        "confidence": mp_conf,
                        "dropout_count": mp_stats.dropout_count,
                        "dropout_rate": mp_stats.dropout_rate,
                    },
                    "yolo": {
                        "critical_ok": yolo_critical_ok,
                        "jitter_px": yolo_jitter,
                        "avg_jitter_px": yolo_stats.avg_jitter,
                        "confidence": yolo_conf,
                        "dropout_count": yolo_stats.dropout_count,
                        "dropout_rate": yolo_stats.dropout_rate,
                    },
                }
                log_fp.write(json.dumps(record, ensure_ascii=False) + "\n")

                if not args.no_display:
                    cv2.imshow("2D Pose Benchmark: MediaPipe (left) vs YOLO (right)", side_by_side)
                    if cv2.waitKey(1) & 0xFF == ord("q"):
                        break

                frame_idx += 1
                if frame_idx % 30 == 0:
                    dt = max(time.time() - t0, 1e-6)
                    fps = frame_idx / dt
                    print(
                        f"[INFO] frames={frame_idx} fps~{fps:.1f} "
                        f"MP(drop={mp_stats.dropout_rate*100:.1f}%, jitter={mp_stats.avg_jitter:.2f}px) "
                        f"YOLO(drop={yolo_stats.dropout_rate*100:.1f}%, jitter={yolo_stats.avg_jitter:.2f}px)"
                    )

                if args.max_frames > 0 and frame_idx >= args.max_frames:
                    print(f"[INFO] Reached max_frames={args.max_frames}; stopping.")
                    break

    finally:
        log_fp.close()
        cap.release()
        if writer is not None:
            writer.release()
        cv2.destroyAllWindows()

    print("[OK] Benchmark finished")
    print(f"[INFO] Frames: {mp_stats.frame_count}")
    print(
        f"[RESULT] MediaPipe: dropout={mp_stats.dropout_rate*100:.2f}% "
        f"avg_jitter={mp_stats.avg_jitter:.2f}px avg_conf={mp_stats.avg_conf:.3f}"
    )
    print(
        f"[RESULT] YOLO: dropout={yolo_stats.dropout_rate*100:.2f}% "
        f"avg_jitter={yolo_stats.avg_jitter:.2f}px avg_conf={yolo_stats.avg_conf:.3f}"
    )
    print(f"[INFO] Log saved: {log_path}")


if __name__ == "__main__":
    main()



