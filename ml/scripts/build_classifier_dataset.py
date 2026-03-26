from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from ecosort_ml.data.manifest import load_manifest


CSV_FIELDS = [
    "crop_path",
    "image_id",
    "instance_id",
    "object_id",
    "split_group",
    "object_class",
    "material_primary",
    "state_cleanliness",
    "state_wetness",
    "state_food_residue",
    "state_liquid",
    "deformation",
    "visible_part",
    "interior_visible",
    "food_present",
    "liquid_present",
    "background_complexity",
    "occlusion",
    "hand_present",
    "target_bin",
    "ambiguity",
    "view_slot",
]


def main() -> None:
    parser = argparse.ArgumentParser(description="Genera crops y CSV para el clasificador multitarea.")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--split-map", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--padding", type=float, default=0.12)
    args = parser.parse_args()

    records = load_manifest(args.manifest)
    split_map = json.loads(args.split_map.read_text(encoding="utf-8"))

    for split in ("train", "val", "test"):
        (args.output_dir / split).mkdir(parents=True, exist_ok=True)

    rows_by_split: dict[str, list[dict[str, str]]] = {"train": [], "val": [], "test": []}
    calibration_rows: list[dict[str, str]] = []

    for record in records:
        image_path = Path(record.image_path)
        if not image_path.exists():
            continue
        split_name = split_map[record.split_group]
        with Image.open(image_path) as image:
            image = image.convert("RGB")
            for instance in record.instances:
                if instance.classifier_ignore:
                    continue
                crop = crop_with_padding(image, instance.bbox_xyxy, args.padding)
                crop_name = f"{instance.instance_id}.jpg"
                crop_path = args.output_dir / split_name / crop_name
                crop.save(crop_path, format="JPEG", quality=95)
                row = {
                    "crop_path": str(crop_path.as_posix()),
                    "image_id": record.image_id,
                    "instance_id": instance.instance_id,
                    "object_id": instance.object_id,
                    "split_group": record.split_group,
                    "object_class": instance.object_class,
                    "material_primary": instance.material_primary,
                    "state_cleanliness": instance.state_cleanliness,
                    "state_wetness": instance.state_wetness,
                    "state_food_residue": instance.state_food_residue,
                    "state_liquid": instance.state_liquid,
                    "deformation": instance.deformation,
                    "visible_part": instance.visible_part,
                    "interior_visible": str(instance.interior_visible),
                    "food_present": str(instance.food_present),
                    "liquid_present": str(instance.liquid_present),
                    "background_complexity": instance.background_complexity,
                    "occlusion": instance.occlusion,
                    "hand_present": str(instance.hand_present),
                    "target_bin": instance.target_bin,
                    "ambiguity": instance.ambiguity,
                    "view_slot": instance.view_slot,
                }
                rows_by_split[split_name].append(row)
                if len(calibration_rows) < 256:
                    calibration_rows.append(row)

    for split_name, rows in rows_by_split.items():
        write_csv(args.output_dir / f"{split_name}.csv", rows)
    write_csv(args.output_dir / "calibration.csv", calibration_rows)
    print(f"Dataset de clasificador creado en {args.output_dir}")


def crop_with_padding(image: Image.Image, bbox_xyxy: list[float], padding: float) -> Image.Image:
    left, top, right, bottom = bbox_xyxy
    pad_x = (right - left) * padding
    pad_y = (bottom - top) * padding
    crop_box = (
        max(int(left - pad_x), 0),
        max(int(top - pad_y), 0),
        min(int(right + pad_x), image.width),
        min(int(bottom + pad_y), image.height),
    )
    return image.crop(crop_box)


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


if __name__ == "__main__":
    main()
