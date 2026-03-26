from __future__ import annotations

import math
from collections import defaultdict
from typing import Any


def fuse_view_probabilities(view_predictions: list[dict[str, Any]], head_name: str) -> dict[str, float]:
    """Weighted geometric mean over multiple views for a single prediction head."""
    if not view_predictions:
        return {}

    log_sums: dict[str, float] = defaultdict(float)
    weight_sum = 0.0
    for item in view_predictions:
        weight = float(item.get("view_weight", 1.0))
        probabilities = item["heads"].get(head_name, {})
        for label, probability in probabilities.items():
            safe_probability = max(float(probability), 1e-8)
            log_sums[label] += math.log(safe_probability) * weight
        weight_sum += weight

    fused = {label: math.exp(score / max(weight_sum, 1e-8)) for label, score in log_sums.items()}
    total = sum(fused.values()) or 1.0
    return {label: value / total for label, value in fused.items()}


def fuse_state_signals(view_predictions: list[dict[str, Any]]) -> dict[str, float]:
    """Use max for contamination cues and mean for quality-related cues."""
    if not view_predictions:
        return {}
    cue_values: dict[str, list[float]] = defaultdict(list)
    for item in view_predictions:
        for cue_name, cue_value in item.get("signals", {}).items():
            cue_values[cue_name].append(float(cue_value))

    fused: dict[str, float] = {}
    for cue_name, values in cue_values.items():
        if cue_name in {"liquid_score", "food_residue_score", "grease_score", "organic_score"}:
            fused[cue_name] = max(values)
        else:
            fused[cue_name] = sum(values) / len(values)
    return fused

