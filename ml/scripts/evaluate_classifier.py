from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import pandas as pd
import yaml
from sklearn.metrics import classification_report, confusion_matrix


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from ecosort_ml.training.classifier_utils import HEAD_NAMES, build_label_maps, encode_dataframe


def main() -> None:
    parser = argparse.ArgumentParser(description="Evalua el clasificador multitarea.")
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--csv", type=Path, required=True)
    parser.add_argument("--taxonomy", type=Path, required=True)
    parser.add_argument("--report-dir", type=Path, required=True)
    args = parser.parse_args()

    try:
        import tensorflow as tf
    except ImportError as exc:
        raise SystemExit("TensorFlow no esta instalado. Ejecuta: pip install -r ml/requirements.txt") from exc

    args.report_dir.mkdir(parents=True, exist_ok=True)
    df = pd.read_csv(args.csv)
    label_maps = build_label_maps(args.taxonomy)
    df = encode_dataframe(df, label_maps)

    model = tf.keras.models.load_model(args.model)
    images = load_images(df["crop_path"].tolist())
    raw_predictions = model.predict(images, verbose=0)
    if isinstance(raw_predictions, list):
        predictions = {
            output_name: array for output_name, array in zip(model.output_names, raw_predictions, strict=False)
        }
    else:
        predictions = raw_predictions

    reports: dict[str, dict] = {}
    instance_predictions = []
    for head in HEAD_NAMES:
        output_name = f"{head}_head"
        probs = predictions[output_name]
        pred_ids = probs.argmax(axis=1)
        true_ids = df[f"{head}_id"].to_numpy()
        idx_to_label = {idx: label for label, idx in label_maps[head].items()}
        ordered_labels = [idx_to_label[idx] for idx in sorted(idx_to_label)]
        report = classification_report(
            true_ids,
            pred_ids,
            output_dict=True,
            zero_division=0,
        )
        matrix = confusion_matrix(true_ids, pred_ids).tolist()
        reports[head] = {"classification_report": report, "confusion_matrix": matrix}

        for row_index, pred_id in enumerate(pred_ids):
            while len(instance_predictions) <= row_index:
                instance_predictions.append(
                    {
                        "instance_id": df.iloc[row_index]["instance_id"],
                        "object_id": df.iloc[row_index]["object_id"],
                        "view_slot": df.iloc[row_index]["view_slot"],
                        "heads": {},
                    }
                )
            instance_predictions[row_index]["heads"][head] = {
                label: float(prob)
                for label, prob in zip(ordered_labels, probs[row_index].tolist(), strict=False)
            }

    (args.report_dir / "classifier_metrics.json").write_text(
        json.dumps(reports, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )
    with (args.report_dir / "instance_predictions.jsonl").open("w", encoding="utf-8") as handle:
        for item in instance_predictions:
            handle.write(json.dumps(item, ensure_ascii=False) + "\n")
    print(f"Reportes guardados en {args.report_dir}")


def load_images(paths: list[str]):
    import numpy as np
    from PIL import Image

    batch = []
    for path in paths:
        with Image.open(path) as image:
            image = image.convert("RGB").resize((224, 224))
            batch.append(np.asarray(image, dtype="float32") / 255.0)
    return np.stack(batch, axis=0)


if __name__ == "__main__":
    main()
