from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

from ecosort_ml.schema import TaxonomySpec, WasteClassDef


def load_yaml(path: str | Path) -> dict[str, Any]:
    with Path(path).open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def load_taxonomy(path: str | Path) -> TaxonomySpec:
    data = load_yaml(path)
    label_spaces = data["label_spaces"]
    object_classes = [
        WasteClassDef(
            id=item["id"],
            display_name=item["display_name"],
            detector_group=item["detector_group"],
            material_candidates=list(item["material_candidates"]),
            likely_bins=list(item["likely_bins"]),
            required_view_slots=list(item["required_view_slots"]),
            ambiguity_triggers=list(item.get("ambiguity_triggers", [])),
            notes=item.get("notes"),
        )
        for item in data["object_classes"]
    ]
    return TaxonomySpec(
        version=data["version"],
        object_classes=object_classes,
        material_labels=list(label_spaces["material_primary"]),
        cleanliness_labels=list(label_spaces["state_cleanliness"]),
        wetness_labels=list(label_spaces["state_wetness"]),
        food_residue_labels=list(label_spaces["state_food_residue"]),
        liquid_labels=list(label_spaces["state_liquid"]),
        deformation_labels=list(label_spaces["deformation"]),
        visible_part_labels=list(label_spaces["visible_part"]),
        background_complexity_labels=list(label_spaces["background_complexity"]),
        occlusion_labels=list(label_spaces["occlusion"]),
        ambiguity_labels=list(label_spaces["ambiguity"]),
        target_bins=list(label_spaces["target_bin"]),
    )


def class_index(taxonomy: TaxonomySpec) -> dict[str, WasteClassDef]:
    return {item.id: item for item in taxonomy.object_classes}

