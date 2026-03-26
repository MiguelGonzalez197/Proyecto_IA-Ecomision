from __future__ import annotations

import math
from dataclasses import dataclass


@dataclass(slots=True)
class UncertaintyResult:
    confidence: float
    entropy: float
    margin: float
    disagreement: float
    missing_slots: list[str]
    should_request_view: bool


def normalized_entropy(probabilities: dict[str, float]) -> float:
    if not probabilities:
        return 1.0
    labels = list(probabilities.values())
    n = max(len(labels), 1)
    entropy = -sum(p * math.log(max(p, 1e-8)) for p in labels)
    max_entropy = math.log(n)
    if max_entropy == 0:
        return 0.0
    return entropy / max_entropy


def top2_margin(probabilities: dict[str, float]) -> float:
    if not probabilities:
        return 0.0
    ordered = sorted(probabilities.values(), reverse=True)
    if len(ordered) == 1:
        return ordered[0]
    return ordered[0] - ordered[1]


def compute_uncertainty(
    fused_bin_probs: dict[str, float],
    view_level_bin_probs: list[dict[str, float]],
    required_slots: list[str],
    observed_slots: list[str],
    min_confidence: float = 0.80,
    min_margin: float = 0.22,
    max_entropy: float = 0.58,
    max_disagreement: float = 0.30,
) -> UncertaintyResult:
    confidence = max(fused_bin_probs.values()) if fused_bin_probs else 0.0
    entropy = normalized_entropy(fused_bin_probs)
    margin = top2_margin(fused_bin_probs)
    disagreement = disagreement_rate(view_level_bin_probs)
    missing_slots = [slot for slot in required_slots if slot not in set(observed_slots)]
    should_request_view = (
        confidence < min_confidence
        or margin < min_margin
        or entropy > max_entropy
        or disagreement > max_disagreement
        or bool(missing_slots)
    )
    return UncertaintyResult(
        confidence=confidence,
        entropy=entropy,
        margin=margin,
        disagreement=disagreement,
        missing_slots=missing_slots,
        should_request_view=should_request_view,
    )


def disagreement_rate(view_level_bin_probs: list[dict[str, float]]) -> float:
    if len(view_level_bin_probs) <= 1:
        return 0.0
    votes = [max(item, key=item.get) for item in view_level_bin_probs if item]
    if not votes:
        return 0.0
    winner = max(set(votes), key=votes.count)
    mismatches = sum(1 for vote in votes if vote != winner)
    return mismatches / len(votes)


def choose_next_view_slot(
    object_class_id: str,
    required_slots: list[str],
    observed_slots: list[str],
    latest_quality_signals: dict[str, float] | None = None,
) -> str | None:
    latest_quality_signals = latest_quality_signals or {}
    if latest_quality_signals.get("blur_score", 0.0) > 0.55:
        return "separated_background"
    if latest_quality_signals.get("occlusion_score", 0.0) > 0.45:
        return "separated_background"

    for slot in required_slots:
        if slot not in set(observed_slots):
            return slot

    class_fallbacks = {
        "plastic_bottle": "opening_neck",
        "coffee_cup": "inner_view",
        "pizza_box": "inner_view",
        "plastic_bag": "unfolded_view",
        "fruit_peel": "close_texture",
    }
    return class_fallbacks.get(object_class_id)

