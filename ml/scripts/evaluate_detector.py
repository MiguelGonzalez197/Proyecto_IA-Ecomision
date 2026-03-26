from __future__ import annotations

import argparse
from pathlib import Path

import yaml


def main() -> None:
    parser = argparse.ArgumentParser(description="Evalua el detector con el split test.")
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--weights", type=Path, default=None)
    args = parser.parse_args()

    try:
        from ultralytics import YOLO
    except ImportError as exc:
        raise SystemExit("Ultralytics no esta instalado. Ejecuta: pip install -r ml/requirements.txt") from exc

    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))
    weights = args.weights or Path(config["project"]) / config["name"] / "weights" / "best.pt"
    model = YOLO(str(weights))
    metrics = model.val(data=config["dataset_yaml"], split="test", imgsz=config["imgsz"])
    print(metrics)


if __name__ == "__main__":
    main()

