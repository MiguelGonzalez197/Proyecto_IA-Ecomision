from __future__ import annotations

import argparse
import json
import shutil
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from ecosort_ml.data.manifest import load_manifest
from ecosort_ml.data.taxonomy import load_taxonomy


def main() -> None:
    parser = argparse.ArgumentParser(description="Convierte el manifest EcoSort a dataset YOLO.")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--split-map", type=Path, required=True)
    parser.add_argument("--taxonomy", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    records = load_manifest(args.manifest)
    split_map = json.loads(args.split_map.read_text(encoding="utf-8"))
    taxonomy = load_taxonomy(args.taxonomy)
    class_names = [item.id for item in taxonomy.object_classes]
    class_to_idx = {name: index for index, name in enumerate(class_names)}

    for split in ("train", "val", "test"):
        (args.output_dir / "images" / split).mkdir(parents=True, exist_ok=True)
        (args.output_dir / "labels" / split).mkdir(parents=True, exist_ok=True)

    for record in records:
        split_name = split_map[record.split_group]
        image_path = Path(record.image_path)
        image_dst = args.output_dir / "images" / split_name / image_path.name
        if image_path.exists():
            shutil.copy2(image_path, image_dst)

        label_lines: list[str] = []
        for instance in record.instances:
            if instance.detector_ignore:
                continue
            class_idx = class_to_idx[instance.object_class]
            line = to_yolo_label(instance.bbox_xyxy, record.width, record.height, class_idx)
            label_lines.append(line)

        label_dst = args.output_dir / "labels" / split_name / f"{image_path.stem}.txt"
        label_dst.write_text("\n".join(label_lines), encoding="utf-8")

    data_yaml = {
        "path": str(args.output_dir.resolve()),
        "train": "images/train",
        "val": "images/val",
        "test": "images/test",
        "names": {index: name for index, name in enumerate(class_names)},
    }
    import yaml

    (args.output_dir / "data.yaml").write_text(yaml.safe_dump(data_yaml, sort_keys=False), encoding="utf-8")
    print(f"Dataset YOLO creado en {args.output_dir}")


def to_yolo_label(bbox_xyxy: list[float], width: int, height: int, class_idx: int) -> str:
    left, top, right, bottom = bbox_xyxy
    x_center = ((left + right) / 2.0) / width
    y_center = ((top + bottom) / 2.0) / height
    box_width = (right - left) / width
    box_height = (bottom - top) / height
    return f"{class_idx} {x_center:.6f} {y_center:.6f} {box_width:.6f} {box_height:.6f}"


if __name__ == "__main__":
    main()

