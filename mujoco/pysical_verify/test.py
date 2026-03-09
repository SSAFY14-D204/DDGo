from pathlib import Path

import mujoco


def main() -> None:
    xml_path = Path(__file__).with_name("humanoid.xml")
    model = mujoco.MjModel.from_xml_path(str(xml_path))
    data = mujoco.MjData(model)
    mujoco.mj_forward(model, data)

    print("[OK] humanoid.xml loaded and simulated one forward step")
    print(f"xml: {xml_path}")
    print(f"nq={model.nq}, nv={model.nv}, nu={model.nu}, nbody={model.nbody}, njnt={model.njnt}")


if __name__ == "__main__":
    main()
