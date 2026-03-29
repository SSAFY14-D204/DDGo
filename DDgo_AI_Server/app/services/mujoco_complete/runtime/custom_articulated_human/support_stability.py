from __future__ import annotations

from typing import Any

import numpy as np


def project_yz(point_xyz: np.ndarray) -> np.ndarray:
    point_xyz = np.asarray(point_xyz, dtype=np.float64)
    return point_xyz[[1, 2]]


def project_xz(point_xyz: np.ndarray) -> np.ndarray:
    point_xyz = np.asarray(point_xyz, dtype=np.float64)
    return point_xyz[[0, 2]]


def polygon_area(points_2d: np.ndarray) -> float:
    if len(points_2d) < 3:
        return 0.0
    x = points_2d[:, 0]
    y = points_2d[:, 1]
    return 0.5 * float(np.sum(x * np.roll(y, -1) - y * np.roll(x, -1)))


def convex_hull(points_2d: np.ndarray, eps: float = 1e-9) -> np.ndarray:
    points_2d = np.asarray(points_2d, dtype=np.float64)
    if len(points_2d) <= 1:
        return points_2d.copy()

    unique_points = np.unique(np.round(points_2d, 9), axis=0)
    if len(unique_points) <= 1:
        return unique_points.copy()

    pts = sorted((float(p[0]), float(p[1])) for p in unique_points)

    def cross(o: tuple[float, float], a: tuple[float, float], b: tuple[float, float]) -> float:
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower: list[tuple[float, float]] = []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= eps:
            lower.pop()
        lower.append(p)

    upper: list[tuple[float, float]] = []
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= eps:
            upper.pop()
        upper.append(p)

    hull = lower[:-1] + upper[:-1]
    return np.asarray(hull, dtype=np.float64)


def distance_point_to_segment(point_2d: np.ndarray, a_2d: np.ndarray, b_2d: np.ndarray) -> tuple[float, float]:
    point_2d = np.asarray(point_2d, dtype=np.float64)
    a_2d = np.asarray(a_2d, dtype=np.float64)
    b_2d = np.asarray(b_2d, dtype=np.float64)
    ab = b_2d - a_2d
    denom = float(np.dot(ab, ab))
    if denom <= 1e-12:
        return float(np.linalg.norm(point_2d - a_2d)), 0.0
    t = float(np.clip(np.dot(point_2d - a_2d, ab) / denom, 0.0, 1.0))
    closest = a_2d + t * ab
    distance = float(np.linalg.norm(point_2d - closest))
    return distance, t


def is_point_inside_convex_polygon(point_2d: np.ndarray, polygon_2d: np.ndarray, eps: float = 1e-8) -> bool:
    if len(polygon_2d) < 3:
        return False
    point_2d = np.asarray(point_2d, dtype=np.float64)
    polygon_2d = np.asarray(polygon_2d, dtype=np.float64)
    area = polygon_area(polygon_2d)
    if abs(area) <= eps:
        return False
    sign = 1.0 if area > 0.0 else -1.0
    for idx in range(len(polygon_2d)):
        a = polygon_2d[idx]
        b = polygon_2d[(idx + 1) % len(polygon_2d)]
        edge = b - a
        rel = point_2d - a
        cross = sign * (edge[0] * rel[1] - edge[1] * rel[0])
        if cross < -eps:
            return False
    return True


def signed_distance_to_polygon(point_2d: np.ndarray, polygon_2d: np.ndarray) -> float:
    if len(polygon_2d) < 3:
        return float("nan")
    distances = []
    for idx in range(len(polygon_2d)):
        a = polygon_2d[idx]
        b = polygon_2d[(idx + 1) % len(polygon_2d)]
        distance, _ = distance_point_to_segment(point_2d, a, b)
        distances.append(distance)
    min_distance = float(min(distances)) if distances else float("nan")
    inside = is_point_inside_convex_polygon(point_2d, polygon_2d)
    return min_distance if inside else -min_distance


def build_support_points_yz(support_points_xyz: dict[str, np.ndarray]) -> dict[str, np.ndarray]:
    return {
        limb_name: project_yz(position_xyz)
        for limb_name, position_xyz in support_points_xyz.items()
    }


def build_support_points_xz(support_points_xyz: dict[str, np.ndarray]) -> dict[str, np.ndarray]:
    return {
        limb_name: project_xz(position_xyz)
        for limb_name, position_xyz in support_points_xyz.items()
    }


def analyze_support_stability(
    com_xyz: np.ndarray,
    support_points_xyz: dict[str, np.ndarray],
) -> dict[str, Any]:
    com_xyz = np.asarray(com_xyz, dtype=np.float64)
    com_proj_yz = project_yz(com_xyz)
    support_points_yz = build_support_points_yz(support_points_xyz)
    support_points_xz = build_support_points_xz(support_points_xyz)
    point_items = list(support_points_yz.items())
    point_count = len(point_items)

    if point_count == 0:
        return {
            "support_type": "unsupported_or_transition",
            "support_geometry": "none",
            "support_point_count": 0,
            "support_points_xyz": {},
            "support_points_yz": {},
            "support_points_xz": {},
            "support_centroid_yz": None,
            "support_centroid_xz": None,
            "com_proj_yz": com_proj_yz.tolist(),
            "com_proj_xz": project_xz(com_xyz).tolist(),
            "inside_support": None,
            "stability_margin_m": None,
            "distance_to_support_m": None,
            "confidence": 0.0,
        }

    points_array = np.asarray([point for _, point in point_items], dtype=np.float64)
    centroid_yz = np.mean(points_array, axis=0)
    centroid_xz = np.mean(np.asarray(list(support_points_xz.values()), dtype=np.float64), axis=0)
    com_proj_xz = project_xz(com_xyz)
    support_points_xyz_json = {
        name: np.asarray(position_xyz, dtype=np.float64).tolist()
        for name, position_xyz in support_points_xyz.items()
    }

    if point_count == 1:
        distance = float(np.linalg.norm(com_proj_yz - points_array[0]))
        return {
            "support_type": "point_support",
            "support_geometry": "point",
            "support_point_count": 1,
            "support_points_xyz": support_points_xyz_json,
            "support_points_yz": {name: point.tolist() for name, point in point_items},
            "support_points_xz": {name: point.tolist() for name, point in support_points_xz.items()},
            "support_centroid_yz": centroid_yz.tolist(),
            "support_centroid_xz": centroid_xz.tolist(),
            "com_proj_yz": com_proj_yz.tolist(),
            "com_proj_xz": com_proj_xz.tolist(),
            "inside_support": False,
            "stability_margin_m": -distance,
            "distance_to_support_m": distance,
            "confidence": 0.25,
        }

    hull = convex_hull(points_array)
    hull_area = abs(polygon_area(hull))

    if point_count == 2 or len(hull) < 3 or hull_area <= 1e-8:
        a = hull[0]
        b = hull[-1]
        distance, t = distance_point_to_segment(com_proj_yz, a, b)
        return {
            "support_type": "line_support",
            "support_geometry": "line",
            "support_point_count": point_count,
            "support_points_xyz": support_points_xyz_json,
            "support_points_yz": {name: point.tolist() for name, point in point_items},
            "support_points_xz": {name: point.tolist() for name, point in support_points_xz.items()},
            "support_centroid_yz": centroid_yz.tolist(),
            "support_centroid_xz": centroid_xz.tolist(),
            "com_proj_yz": com_proj_yz.tolist(),
            "com_proj_xz": com_proj_xz.tolist(),
            "inside_support": False,
            "stability_margin_m": -distance,
            "distance_to_support_m": distance,
            "segment_projection_t": t,
            "hull_vertices_yz": hull.tolist(),
            "confidence": 0.5,
        }

    signed_margin = signed_distance_to_polygon(com_proj_yz, hull)
    return {
        "support_type": "tri_support" if len(hull) == 3 else "quad_support",
        "support_geometry": "polygon",
        "support_point_count": point_count,
        "support_points_xyz": support_points_xyz_json,
        "support_points_yz": {name: point.tolist() for name, point in point_items},
        "support_points_xz": {name: point.tolist() for name, point in support_points_xz.items()},
        "support_centroid_yz": centroid_yz.tolist(),
        "support_centroid_xz": centroid_xz.tolist(),
        "com_proj_yz": com_proj_yz.tolist(),
        "com_proj_xz": com_proj_xz.tolist(),
        "inside_support": bool(signed_margin >= 0.0),
        "stability_margin_m": float(signed_margin),
        "distance_to_support_m": float(abs(signed_margin)),
        "hull_vertices_yz": hull.tolist(),
        "confidence": 0.8 if len(hull) == 3 else 1.0,
    }
