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

from ecosort_ml.training.classifier_utils import (
    HEAD_NAMES,
    build_label_maps,
    build_multitask_model,
    build_tf_datasets,
    encode_dataframe,
    save_label_maps,
)


def main() -> None:
    parser = argparse.ArgumentParser(description="Entrena el clasificador multitarea de EcoSort.")
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--taxonomy", type=Path, required=True)
    args = parser.parse_args()

    try:
        import tensorflow as tf
    except ImportError as exc:
        raise SystemExit("TensorFlow no esta instalado. Ejecuta: pip install -r ml/requirements.txt") from exc

    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))
    outputs = config["outputs"]
    model_dir = Path(outputs["model_dir"])
    model_dir.mkdir(parents=True, exist_ok=True)

    if config.get("mixed_precision", False):
        tf.keras.mixed_precision.set_global_policy("mixed_float16")

    train_df = pd.read_csv(config["train_csv"])
    val_df = pd.read_csv(config["val_csv"])
    label_maps = build_label_maps(args.taxonomy)
    train_df = encode_dataframe(train_df, label_maps)
    val_df = encode_dataframe(val_df, label_maps)

    input_size = tuple(config["input_size"])
    train_ds, val_ds = build_tf_datasets(
        train_df=train_df,
        val_df=val_df,
        image_size=input_size,
        batch_size=int(config["batch_size"]),
    )

    model = build_multitask_model(input_size=input_size, label_maps=label_maps)
    losses = {f"{head}_head": tf.keras.losses.SparseCategoricalCrossentropy() for head in HEAD_NAMES}
    metrics = {f"{head}_head": [tf.keras.metrics.SparseCategoricalAccuracy(name="acc")] for head in HEAD_NAMES}
    loss_weights = {
        "object_class_head": 1.0,
        "material_primary_head": 0.6,
        "state_cleanliness_head": 0.8,
        "state_wetness_head": 0.4,
        "state_food_residue_head": 0.8,
        "state_liquid_head": 0.8,
        "target_bin_head": 1.1,
    }

    model.compile(
        optimizer=tf.keras.optimizers.AdamW(
            learning_rate=float(config["learning_rate"]),
            weight_decay=float(config["weight_decay"]),
        ),
        loss=losses,
        metrics=metrics,
        loss_weights=loss_weights,
    )

    callbacks = [
        tf.keras.callbacks.ModelCheckpoint(
            filepath=str(model_dir / "best_model.keras"),
            monitor="val_loss",
            save_best_only=True,
        ),
        tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=6,
            restore_best_weights=True,
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=0.5,
            patience=3,
            min_lr=1e-6,
        ),
        tf.keras.callbacks.CSVLogger(str(model_dir / "history.csv")),
    ]

    history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=int(config["epochs"]),
        callbacks=callbacks,
    )

    model.save(model_dir / "last_model.keras")
    save_label_maps(model_dir / "label_maps.json", label_maps)
    (model_dir / "history.json").write_text(json.dumps(history.history, indent=2), encoding="utf-8")
    print(f"Modelo guardado en {model_dir}")


if __name__ == "__main__":
    main()

