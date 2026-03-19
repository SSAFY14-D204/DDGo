from __future__ import annotations

import argparse
import json
from pathlib import Path

import mujoco

ROOT = Path(__file__).resolve().parent
DEFAULT_PERSONALIZED_XML = ROOT / "custom_articulated_human_personalized.xml"
DEFAULT_XML = DEFAULT_PERSONALIZED_XML if DEFAULT_PERSONALIZED_XML.exists() else ROOT / "custom_articulated_human.xml"


def main() -> None:
    parser = argparse.ArgumentParser(description="Inspect custom articulated human model structure.")
    parser.add_argument("--xml", type=Path, default=DEFAULT_XML)
    args = parser.parse_args()

    model = mujoco.MjModel.from_xml_path(str(args.xml.resolve()))

    joints: list[dict[str, object]] = []
    for jnt_id in range(model.njnt):
        name = mujoco.mj_id2name(model, mujoco.mjtObj.mjOBJ_JOINT, jnt_id)
        jnt_type = int(model.jnt_type[jnt_id])
        joints.append(
            {
                "name": name,
                "type": jnt_type,
                "qposadr": int(model.jnt_qposadr[jnt_id]),
                "dofadr": int(model.jnt_dofadr[jnt_id]),
                "range": [float(model.jnt_range[jnt_id, 0]), float(model.jnt_range[jnt_id, 1])],
            }
        )

    bodies: list[dict[str, object]] = []
    total_mass = 0.0
    for body_id in range(model.nbody):
        name = mujoco.mj_id2name(model, mujoco.mjtObj.mjOBJ_BODY, body_id)
        mass = float(model.body_mass[body_id])
        total_mass += mass
        bodies.append(
            {
                "name": name,
                "mass_kg": mass,
                "parent_id": int(model.body_parentid[body_id]),
            }
        )

    actuators: list[dict[str, object]] = []
    for act_id in range(model.nu):
        name = mujoco.mj_id2name(model, mujoco.mjtObj.mjOBJ_ACTUATOR, act_id)
        actuators.append(
            {
                "name": name,
                "gear": float(model.actuator_gear[act_id, 0]),
                "ctrlrange": [float(model.actuator_ctrlrange[act_id, 0]), float(model.actuator_ctrlrange[act_id, 1])],
            }
        )

    payload = {
        "xml": str(args.xml.resolve()),
        "nq": int(model.nq),
        "nv": int(model.nv),
        "nu": int(model.nu),
        "nbody": int(model.nbody),
        "njnt": int(model.njnt),
        "total_mass_kg": total_mass,
        "joints": joints,
        "bodies": bodies,
        "actuators": actuators,
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
