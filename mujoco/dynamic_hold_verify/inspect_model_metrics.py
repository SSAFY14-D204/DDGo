from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import mujoco
import numpy as np


def geom_vertical_extent(model: mujoco.MjModel, data: mujoco.MjData, geom_id: int) -> tuple[float, float]:
    geom_type = int(model.geom_type[geom_id])
    size = np.asarray(model.geom_size[geom_id], dtype=np.float64)
    xpos = np.asarray(data.geom_xpos[geom_id], dtype=np.float64)
    xmat = np.asarray(data.geom_xmat[geom_id], dtype=np.float64).reshape(3, 3)
    z_axis = np.array([0.0, 0.0, 1.0], dtype=np.float64)

    if geom_type == int(mujoco.mjtGeom.mjGEOM_SPHERE):
        radius = float(size[0])
        return float(xpos[2] - radius), float(xpos[2] + radius)

    if geom_type == int(mujoco.mjtGeom.mjGEOM_CAPSULE):
        radius = float(size[0])
        half_length = float(size[1])
        axis = xmat[:, 2]
        extent = abs(float(np.dot(axis, z_axis))) * half_length + radius
        return float(xpos[2] - extent), float(xpos[2] + extent)

    if geom_type in (int(mujoco.mjtGeom.mjGEOM_BOX), int(mujoco.mjtGeom.mjGEOM_CYLINDER), int(mujoco.mjtGeom.mjGEOM_ELLIPSOID)):
        extent = abs(xmat[2, 0]) * float(size[0]) + abs(xmat[2, 1]) * float(size[1]) + abs(xmat[2, 2]) * float(size[2])
        return float(xpos[2] - extent), float(xpos[2] + extent)

    generic_extent = float(np.max(size))
    return float(xpos[2] - generic_extent), float(xpos[2] + generic_extent)


def compute_model_metrics(xml_path: Path) -> dict[str, float]:
    model = mujoco.MjModel.from_xml_path(str(xml_path))
    data = mujoco.MjData(model)
    mujoco.mj_forward(model, data)

    zmins: list[float] = []
    zmaxs: list[float] = []
    for geom_id in range(int(model.ngeom)):
        zmin, zmax = geom_vertical_extent(model, data, geom_id)
        zmins.append(zmin)
        zmaxs.append(zmax)

    total_mass = float(np.sum(model.body_mass))
    return {
        "total_mass_kg": total_mass,
        "geom_height_m": float(max(zmaxs) - min(zmins)),
        "geom_zmin_m": float(min(zmins)),
        "geom_zmax_m": float(max(zmaxs)),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Inspect MuJoCo humanoid total mass and geometric height")
    parser.add_argument("--xml", default=str(Path(__file__).with_name("humanoid_shoulder3_175.xml")))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    xml_path = Path(args.xml).resolve()
    metrics = compute_model_metrics(xml_path)
    print(json.dumps({"xml": str(xml_path), **metrics}, indent=2))


if __name__ == "__main__":
    main()
