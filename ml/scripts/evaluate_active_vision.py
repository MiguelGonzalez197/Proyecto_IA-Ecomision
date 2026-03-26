from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from ecosort_ml.data.taxonomy import load_taxonomy
from ecosort_ml.training.multiview import fuse_view_probabilities
from ecosort_ml.training.uncertainty import choose_next_view_slot, compute_uncertainty


def main() -> None:
    parser = argparse.ArgumentParser(description="Simula active vision y fusion multivista sobre predicciones.")
    parser.add_argument("--predictions-jsonl", type=Path, required=True)
    parser.add_argument("--metadata-csv", type=Path, required=True)
    parser.add_argument("--taxonomy", type=Path, required=True)
    parser.add_argument("--report-json", type=Path, required=True)
    args = parser.parse_args()

    taxonomy = load_taxonomy(args.taxonomy)
    taxonomy_by_id = {item.id: item for item in taxonomy.object_classes}
    metadata = pd.read_csv(args.metadata_csv)
    predictions = load_predictions(args.predictions_jsonl)

    object_rows: dict[str, list[dict]] = defaultdict(list)
    for row in metadata.to_dict(orient="records"):
        prediction = predictions.get(row["instance_id"])
        if prediction is None:
            continue
        object_rows[row["object_id"]].append({**row, **prediction})

    total = 0
    initial_correct = 0
    final_correct = 0
    requested_view = 0
    justified_request = 0
    overconfident_wrong = 0

    for object_id, rows in object_rows.items():
        rows.sort(key=view_sort_key)
        first = rows[0]
        object_class = first["object_class"]
        target_bin = first["target_bin"]
        required_slots = taxonomy_by_id.get(object_class).required_view_slots if object_class in taxonomy_by_id else []

        initial_probs = first["heads"].get("target_bin", {})
        initial_pred = max(initial_probs, key=initial_probs.get)
        if initial_pred == target_bin:
            initial_correct += 1

        uncertainty = compute_uncertainty(
            fused_bin_probs=initial_probs,
            view_level_bin_probs=[initial_probs],
            required_slots=required_slots,
            observed_slots=[first["view_slot"]],
        )
        if uncertainty.confidence >= 0.85 and initial_pred != target_bin:
            overconfident_wrong += 1

        used_rows = [first]
        observed_slots = [first["view_slot"]]
        if uncertainty.should_request_view and len(rows) > 1:
            requested_view += 1
            if first["ambiguity"] in {"medium", "high"}:
                justified_request += 1
            next_slot = choose_next_view_slot(
                object_class_id=object_class,
                required_slots=required_slots,
                observed_slots=observed_slots,
            )
            next_row = pick_next_row(rows[1:], next_slot)
            if next_row is not None:
                used_rows.append(next_row)
                observed_slots.append(next_row["view_slot"])

        fused_probs = fuse_view_probabilities(used_rows, "target_bin")
        final_pred = max(fused_probs, key=fused_probs.get) if fused_probs else initial_pred
        if final_pred == target_bin:
            final_correct += 1
        total += 1

    report = {
        "objects_evaluated": total,
        "initial_bin_accuracy": safe_div(initial_correct, total),
        "final_bin_accuracy": safe_div(final_correct, total),
        "view_request_rate": safe_div(requested_view, total),
        "justified_view_request_rate": safe_div(justified_request, max(requested_view, 1)),
        "overconfident_wrong_rate": safe_div(overconfident_wrong, total),
    }
    args.report_json.parent.mkdir(parents=True, exist_ok=True)
    args.report_json.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))


def load_predictions(path: Path) -> dict[str, dict]:
    payload: dict[str, dict] = {}
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            item = json.loads(line)
            payload[item["instance_id"]] = item
    return payload


def pick_next_row(rows: list[dict], next_slot: str | None) -> dict | None:
    if not rows:
        return None
    if next_slot is None:
        return rows[0]
    for row in rows:
        if row["view_slot"] == next_slot:
            return row
    return rows[0]


def view_sort_key(row: dict) -> tuple[int, str]:
    order = {
        "outer_full": 0,
        "both_faces": 1,
        "inner_view": 2,
        "opening_neck": 3,
        "back_side": 4,
        "bottom_view": 5,
        "close_texture": 6,
        "unfolded_view": 7,
    }
    return (order.get(row["view_slot"], 99), row["instance_id"])


def safe_div(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 0.0
    return numerator / denominator


if __name__ == "__main__":
    main()

