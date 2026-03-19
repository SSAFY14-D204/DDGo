from __future__ import annotations

import argparse
import copy
import xml.etree.ElementTree as ET
from pathlib import Path


BASE_HEIGHT_M = 1.562

SCALABLE_TAG_ATTRS = {
    "body": ("pos",),
    "joint": ("pos",),
    "geom": ("pos", "fromto", "size"),
    "camera": ("pos",),
    "light": ("pos",),
}


def parse_floats(text: str) -> list[float]:
    return [float(part) for part in text.strip().split()]


def format_floats(values: list[float]) -> str:
    return " ".join(f"{value:.6f}".rstrip("0").rstrip(".") if "." in f"{value:.6f}" else f"{value:.6f}" for value in values)


def scale_attr(elem: ET.Element, attr_name: str, scale: float) -> None:
    raw = elem.get(attr_name)
    if not raw:
        return
    values = parse_floats(raw)
    scaled = [value * scale for value in values]
    elem.set(attr_name, format_floats(scaled))


def scale_subtree(elem: ET.Element, scale: float) -> None:
    attrs = SCALABLE_TAG_ATTRS.get(elem.tag, ())
    for attr_name in attrs:
        scale_attr(elem, attr_name, scale)
    for child in list(elem):
        scale_subtree(child, scale)


def build_scaled_tree(input_path: Path, target_height_m: float) -> ET.ElementTree:
    tree = ET.parse(str(input_path))
    root = tree.getroot()
    scale = float(target_height_m) / BASE_HEIGHT_M

    worldbody = root.find("worldbody")
    if worldbody is None:
        raise RuntimeError("XML missing <worldbody>")

    torso_body = None
    for child in worldbody.findall("body"):
        if child.get("name") == "torso":
            torso_body = child
            break
    if torso_body is None:
        raise RuntimeError("XML missing torso body")

    scale_subtree(torso_body, scale)
    root.set("model", f"{root.get('model', 'Humanoid')}_{int(round(target_height_m * 100))}cm")
    return tree


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Scale humanoid XML uniformly to a target height")
    parser.add_argument("--input", default=str(Path(__file__).with_name("humanoid_shoulder3.xml")))
    parser.add_argument("--output", default=str(Path(__file__).with_name("humanoid_shoulder3_175.xml")))
    parser.add_argument("--target-height-m", type=float, default=1.75)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()

    tree = build_scaled_tree(input_path, float(args.target_height_m))
    ET.indent(tree, space="  ")
    tree.write(output_path, encoding="utf-8", xml_declaration=False)

    scale = float(args.target_height_m) / BASE_HEIGHT_M
    print(
        {
            "input": str(input_path),
            "output": str(output_path),
            "base_height_m": BASE_HEIGHT_M,
            "target_height_m": float(args.target_height_m),
            "scale_factor": scale,
        }
    )


if __name__ == "__main__":
    main()
