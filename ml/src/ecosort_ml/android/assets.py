from __future__ import annotations

from pathlib import Path

import yaml

from ecosort_ml.data.taxonomy import load_taxonomy, load_yaml


def export_android_assets(
    taxonomy_path: str | Path,
    rules_path: str | Path,
    taxonomy_output: str | Path,
    rules_output: str | Path,
) -> None:
    taxonomy = load_taxonomy(taxonomy_path)
    rules = load_yaml(rules_path)

    android_taxonomy = []
    for item in taxonomy.object_classes:
        default_bin = "WHITE" if "WHITE" in item.likely_bins else item.likely_bins[0]
        android_taxonomy.append(
            {
                "id": item.id,
                "displayName": item.display_name,
                "family": item.detector_group.upper(),
                "defaultBin": default_bin,
                "requiresViews": [slot.upper() for slot in item.required_view_slots],
                "guidanceTags": item.ambiguity_triggers,
            }
        )

    android_rules = []
    for item in rules["rules"]:
        android_rules.append(
            {
                "id": item["id"],
                "categoryId": "*",
                "priority": item["priority"],
                "conditions": map_rule_condition(item["id"]),
                "binType": item["bin"],
                "reason": item["reason"],
            }
        )

    write_json(Path(taxonomy_output), android_taxonomy)
    write_json(Path(rules_output), android_rules)


def write_json(path: Path, payload: list[dict]) -> None:
    import json

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)


def map_rule_condition(rule_id: str) -> str:
    condition_map = {
        "organic_visible": "organic_confident",
        "mixed_organic_requires_check": "organic_confident",
        "visible_liquid_black": "visible_liquid",
        "heavy_food_residue_black": "strong_food_residue",
        "greasy_cardboard_black": "grease_visible",
        "used_napkin_black": "used_or_wet",
        "dry_clean_recyclable_white": "clean_and_dry",
        "drained_tetra_pak_white": "clean_and_drained",
        "multilayer_black": "default",
        "fallback_black": "default",
    }
    return condition_map.get(rule_id, "default")
