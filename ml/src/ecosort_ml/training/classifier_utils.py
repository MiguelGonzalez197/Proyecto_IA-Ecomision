from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import pandas as pd
import yaml


HEAD_NAMES = [
    "object_class",
    "material_primary",
    "state_cleanliness",
    "state_wetness",
    "state_food_residue",
    "state_liquid",
    "target_bin",
]


def load_yaml(path: str | Path) -> dict[str, Any]:
    with Path(path).open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def build_label_maps(taxonomy_path: str | Path) -> dict[str, dict[str, int]]:
    data = load_yaml(taxonomy_path)
    label_spaces = data["label_spaces"]
    object_classes = [item["id"] for item in data["object_classes"]]

    spaces = {
        "object_class": object_classes,
        "material_primary": label_spaces["material_primary"],
        "state_cleanliness": label_spaces["state_cleanliness"],
        "state_wetness": label_spaces["state_wetness"],
        "state_food_residue": label_spaces["state_food_residue"],
        "state_liquid": label_spaces["state_liquid"],
        "target_bin": label_spaces["target_bin"],
    }
    return {head: {label: idx for idx, label in enumerate(labels)} for head, labels in spaces.items()}


def save_label_maps(path: str | Path, label_maps: dict[str, dict[str, int]]) -> None:
    with Path(path).open("w", encoding="utf-8") as handle:
        json.dump(label_maps, handle, indent=2, ensure_ascii=False)


def encode_dataframe(df: pd.DataFrame, label_maps: dict[str, dict[str, int]]) -> pd.DataFrame:
    encoded = df.copy()
    for head, mapping in label_maps.items():
        encoded[f"{head}_id"] = encoded[head].map(mapping)
        if encoded[f"{head}_id"].isna().any():
            missing = encoded.loc[encoded[f"{head}_id"].isna(), head].unique().tolist()
            raise ValueError(f"Etiquetas fuera de taxonomia para {head}: {missing}")
        encoded[f"{head}_id"] = encoded[f"{head}_id"].astype("int32")
    return encoded


def build_tf_datasets(
    train_df: pd.DataFrame,
    val_df: pd.DataFrame,
    image_size: tuple[int, int],
    batch_size: int,
):
    import tensorflow as tf

    train_ds = dataframe_to_tf_dataset(train_df, image_size=image_size, batch_size=batch_size, training=True)
    val_ds = dataframe_to_tf_dataset(val_df, image_size=image_size, batch_size=batch_size, training=False)
    return train_ds, val_ds


def dataframe_to_tf_dataset(
    df: pd.DataFrame,
    image_size: tuple[int, int],
    batch_size: int,
    training: bool,
):
    import tensorflow as tf

    inputs = df["crop_path"].astype(str).tolist()
    labels = {f"{head}_head": df[f"{head}_id"].astype("int32").tolist() for head in HEAD_NAMES}
    dataset = tf.data.Dataset.from_tensor_slices((inputs, labels))

    def load_item(path, label_dict):
        image = tf.io.read_file(path)
        image = tf.io.decode_jpeg(image, channels=3)
        image = tf.image.resize(image, image_size)
        image = tf.cast(image, tf.float32) / 255.0
        if training:
            image = tf.image.random_flip_left_right(image)
            image = tf.image.random_brightness(image, 0.08)
            image = tf.image.random_contrast(image, 0.85, 1.15)
            image = tf.image.random_saturation(image, 0.85, 1.15)
        return image, label_dict

    autotune = tf.data.AUTOTUNE
    if training:
        dataset = dataset.shuffle(min(len(df), 2048), reshuffle_each_iteration=True)
    dataset = dataset.map(load_item, num_parallel_calls=autotune)
    dataset = dataset.batch(batch_size).prefetch(autotune)
    return dataset


def build_multitask_model(
    input_size: tuple[int, int],
    label_maps: dict[str, dict[str, int]],
):
    import tensorflow as tf

    inputs = tf.keras.Input(shape=(input_size[0], input_size[1], 3), name="image")
    try:
        backbone = tf.keras.applications.MobileNetV3Small(
            input_shape=(input_size[0], input_size[1], 3),
            include_top=False,
            weights="imagenet",
            pooling="avg",
        )
    except Exception:
        backbone = tf.keras.applications.MobileNetV3Small(
            input_shape=(input_size[0], input_size[1], 3),
            include_top=False,
            weights=None,
            pooling="avg",
        )

    x = backbone(inputs, training=True)
    x = tf.keras.layers.Dropout(0.20)(x)
    x = tf.keras.layers.Dense(256, activation="relu")(x)
    x = tf.keras.layers.Dropout(0.20)(x)

    outputs = {}
    for head in HEAD_NAMES:
        outputs[f"{head}_head"] = tf.keras.layers.Dense(
            len(label_maps[head]),
            activation="softmax",
            name=f"{head}_head",
        )(x)

    model = tf.keras.Model(inputs=inputs, outputs=outputs)
    return model
