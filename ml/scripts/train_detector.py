from __future__ import annotations

import argparse
import sys
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))


def main() -> None:
    parser = argparse.ArgumentParser(description="Entrena el detector de residuos con Ultralytics.")
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()

    try:
        from ultralytics import YOLO
    except ImportError as exc:
        raise SystemExit("Ultralytics no esta instalado. Ejecuta: pip install -r ml/requirements.txt") from exc

    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))
    model = YOLO(config["base_model"])

    train_kwargs = {
        "data": config["dataset_yaml"],
        "imgsz": config["imgsz"],
        "epochs": config["epochs"],
        "batch": config["batch"],
        "patience": config["patience"],
        "project": config["project"],
        "name": config["name"],
        "workers": config["workers"],
        "save_period": config["save_period"],
    }
    train_kwargs.update(config.get("augment", {}))
    model.train(**train_kwargs)


if __name__ == "__main__":
    main()

