from __future__ import annotations

import json
import random
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

from ecosort_ml.schema import ImageRecord


@dataclass(slots=True)
class SplitConfig:
    train_ratio: float = 0.70
    val_ratio: float = 0.15
    test_ratio: float = 0.15
    seed: int = 42


def build_group_label_map(records: list[ImageRecord]) -> dict[str, str]:
    group_to_class: dict[str, str] = {}
    for record in records:
        split_group = record.split_group
        for instance in record.instances:
            group_to_class.setdefault(split_group, instance.object_class)
    return group_to_class


def grouped_split(records: list[ImageRecord], config: SplitConfig) -> dict[str, str]:
    rng = random.Random(config.seed)
    object_to_class = build_group_label_map(records)
    class_groups: dict[str, list[str]] = defaultdict(list)
    for object_id, object_class in object_to_class.items():
        class_groups[object_class].append(object_id)

    split_map: dict[str, str] = {}
    for object_class, object_ids in class_groups.items():
        rng.shuffle(object_ids)
        total = len(object_ids)
        train_cut = round(total * config.train_ratio)
        val_cut = train_cut + round(total * config.val_ratio)
        for object_id in object_ids[:train_cut]:
            split_map[object_id] = "train"
        for object_id in object_ids[train_cut:val_cut]:
            split_map[object_id] = "val"
        for object_id in object_ids[val_cut:]:
            split_map[object_id] = "test"

    return split_map


def summarize_splits(records: list[ImageRecord], split_map: dict[str, str]) -> dict[str, dict[str, int]]:
    summary: dict[str, Counter] = {
        "train": Counter(),
        "val": Counter(),
        "test": Counter(),
    }
    for record in records:
        split_name = split_map[record.split_group]
        for instance in record.instances:
            summary[split_name][instance.object_class] += 1
    return {key: dict(counter) for key, counter in summary.items()}


def save_split_map(path: str | Path, split_map: dict[str, str]) -> None:
    with Path(path).open("w", encoding="utf-8") as handle:
        json.dump(split_map, handle, indent=2, ensure_ascii=False)
