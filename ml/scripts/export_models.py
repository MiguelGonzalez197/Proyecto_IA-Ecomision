from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import pandas as pd
import yaml


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))


def main() -> None:
    parser = argparse.ArgumentParser(description="Exporta detector y clasificador para Android.")
    parser.add_argument("--config", type=Path, required=True)
    args = parser.parse_args()

    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))
    detector_artifact = export_detector(config["detector"])
    classifier_artifact = export_classifier(config["classifier"])

    report = {
        "detector_tflite": str(detector_artifact),
        "classifier_tflite": str(classifier_artifact),
        "detector_size_mb": file_size_mb(detector_artifact),
        "classifier_size_mb": file_size_mb(classifier_artifact),
    }
    report_path = ROOT / "exports" / "android" / "export_report.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


def export_detector(config: dict) -> Path:
    try:
        from ultralytics import YOLO
    except ImportError as exc:
        raise SystemExit("Ultralytics no esta instalado.") from exc

    model = YOLO(config["source_checkpoint"])
    export_result = model.export(
        format=config["export_format"],
        imgsz=config["imgsz"],
        int8=config["int8"],
        half=config["half"],
        nms=config["nms"],
    )
    export_path = Path(export_result)
    target_path = Path(config["target_path"])
    target_path.parent.mkdir(parents=True, exist_ok=True)
    target_path.write_bytes(export_path.read_bytes())
    return target_path


def export_classifier(config: dict) -> Path:
    try:
        import tensorflow as tf
    except ImportError as exc:
        raise SystemExit("TensorFlow no esta instalado.") from exc

    source_model = Path(config["source_model"])
    target_path = Path(config["target_path"])
    target_path.parent.mkdir(parents=True, exist_ok=True)
    model = tf.keras.models.load_model(source_model)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    quantization = config.get("quantization", "dynamic")
    if quantization == "dynamic":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
    elif quantization == "float16":
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
    elif quantization == "int8":
        calibration_csv = Path(config["representative_dataset_csv"])
        calibration_df = pd.read_csv(calibration_csv)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.representative_dataset = lambda: representative_dataset(calibration_df)
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        converter.inference_input_type = tf.uint8
        converter.inference_output_type = tf.uint8

    tflite_model = converter.convert()
    target_path.write_bytes(tflite_model)
    return target_path


def representative_dataset(calibration_df):
    import numpy as np
    from PIL import Image

    for path in calibration_df["crop_path"].head(128).tolist():
        with Image.open(path) as image:
            image = image.convert("RGB").resize((224, 224))
            array = np.asarray(image, dtype="float32") / 255.0
            yield [array[None, ...]]


def file_size_mb(path: Path) -> float:
    return round(path.stat().st_size / (1024 * 1024), 3)


if __name__ == "__main__":
    main()
