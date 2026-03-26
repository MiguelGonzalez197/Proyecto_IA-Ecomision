from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass(slots=True)
class WasteClassDef:
    id: str
    display_name: str
    detector_group: str
    material_candidates: list[str]
    likely_bins: list[str]
    required_view_slots: list[str]
    ambiguity_triggers: list[str] = field(default_factory=list)
    notes: str | None = None


@dataclass(slots=True)
class TaxonomySpec:
    version: str
    object_classes: list[WasteClassDef]
    material_labels: list[str]
    cleanliness_labels: list[str]
    wetness_labels: list[str]
    food_residue_labels: list[str]
    liquid_labels: list[str]
    deformation_labels: list[str]
    visible_part_labels: list[str]
    background_complexity_labels: list[str]
    occlusion_labels: list[str]
    ambiguity_labels: list[str]
    target_bins: list[str]


@dataclass(slots=True)
class InstanceAnnotation:
    instance_id: str
    object_id: str
    bbox_xyxy: list[float]
    object_class: str
    material_primary: str
    material_secondary: str | None
    state_cleanliness: str
    state_wetness: str
    state_food_residue: str
    state_liquid: str
    deformation: str
    visible_part: str
    interior_visible: bool
    food_present: bool
    liquid_present: bool
    background_complexity: str
    occlusion: str
    hand_present: bool
    target_bin: str
    ambiguity: str
    view_slot: str
    detector_ignore: bool = False
    classifier_ignore: bool = False
    notes: str | None = None
    polygon: list[list[float]] | None = None


@dataclass(slots=True)
class ImageRecord:
    image_id: str
    image_path: str
    width: int
    height: int
    capture_session_id: str
    scene_id: str
    location_id: str
    device_id: str
    lighting: str
    distance: str
    background_complexity: str
    hand_present: bool
    split_group: str
    instances: list[InstanceAnnotation]
    tags: list[str] = field(default_factory=list)


def record_from_dict(data: dict[str, Any]) -> ImageRecord:
    return ImageRecord(
        image_id=data["image_id"],
        image_path=data["image_path"],
        width=int(data["width"]),
        height=int(data["height"]),
        capture_session_id=data["capture_session_id"],
        scene_id=data["scene_id"],
        location_id=data["location_id"],
        device_id=data["device_id"],
        lighting=data["lighting"],
        distance=data["distance"],
        background_complexity=data["background_complexity"],
        hand_present=bool(data["hand_present"]),
        split_group=data["split_group"],
        tags=list(data.get("tags", [])),
        instances=[
            InstanceAnnotation(
                instance_id=instance["instance_id"],
                object_id=instance["object_id"],
                bbox_xyxy=list(instance["bbox_xyxy"]),
                polygon=instance.get("polygon"),
                object_class=instance["object_class"],
                material_primary=instance["material_primary"],
                material_secondary=instance.get("material_secondary"),
                state_cleanliness=instance["state_cleanliness"],
                state_wetness=instance["state_wetness"],
                state_food_residue=instance["state_food_residue"],
                state_liquid=instance["state_liquid"],
                deformation=instance["deformation"],
                visible_part=instance["visible_part"],
                interior_visible=bool(instance["interior_visible"]),
                food_present=bool(instance["food_present"]),
                liquid_present=bool(instance["liquid_present"]),
                background_complexity=instance["background_complexity"],
                occlusion=instance["occlusion"],
                hand_present=bool(instance["hand_present"]),
                target_bin=instance["target_bin"],
                ambiguity=instance["ambiguity"],
                view_slot=instance["view_slot"],
                detector_ignore=bool(instance.get("detector_ignore", False)),
                classifier_ignore=bool(instance.get("classifier_ignore", False)),
                notes=instance.get("notes"),
            )
            for instance in data["instances"]
        ],
    )
