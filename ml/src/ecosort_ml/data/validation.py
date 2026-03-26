from __future__ import annotations

from collections import Counter
from dataclasses import dataclass, field

from ecosort_ml.schema import ImageRecord, TaxonomySpec


@dataclass(slots=True)
class ValidationReport:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    image_count: int = 0
    instance_count: int = 0
    class_distribution: Counter = field(default_factory=Counter)
    target_bin_distribution: Counter = field(default_factory=Counter)


def validate_manifest(records: list[ImageRecord], taxonomy: TaxonomySpec) -> ValidationReport:
    report = ValidationReport(image_count=len(records))
    class_ids = {item.id for item in taxonomy.object_classes}
    allowed_materials = set(taxonomy.material_labels)
    allowed_cleanliness = set(taxonomy.cleanliness_labels)
    allowed_wetness = set(taxonomy.wetness_labels)
    allowed_food = set(taxonomy.food_residue_labels)
    allowed_liquid = set(taxonomy.liquid_labels)
    allowed_deformation = set(taxonomy.deformation_labels)
    allowed_visible = set(taxonomy.visible_part_labels)
    allowed_background = set(taxonomy.background_complexity_labels)
    allowed_occlusion = set(taxonomy.occlusion_labels)
    allowed_ambiguity = set(taxonomy.ambiguity_labels)
    allowed_bins = set(taxonomy.target_bins)

    seen_image_ids: set[str] = set()
    seen_instance_ids: set[str] = set()

    for record in records:
        if record.image_id in seen_image_ids:
            report.errors.append(f"image_id duplicado: {record.image_id}")
        seen_image_ids.add(record.image_id)

        if not record.instances:
            report.warnings.append(f"{record.image_id} no contiene instancias anotadas.")

        for instance in record.instances:
            report.instance_count += 1
            report.class_distribution[instance.object_class] += 1
            report.target_bin_distribution[instance.target_bin] += 1

            if instance.instance_id in seen_instance_ids:
                report.errors.append(f"instance_id duplicado: {instance.instance_id}")
            seen_instance_ids.add(instance.instance_id)

            left, top, right, bottom = instance.bbox_xyxy
            if left >= right or top >= bottom:
                report.errors.append(f"{instance.instance_id} bbox invalido: {instance.bbox_xyxy}")
            if left < 0 or top < 0 or right > record.width or bottom > record.height:
                report.errors.append(f"{instance.instance_id} bbox fuera de imagen.")

            if instance.object_class not in class_ids:
                report.errors.append(f"{instance.instance_id} clase desconocida: {instance.object_class}")
            if instance.material_primary not in allowed_materials:
                report.errors.append(f"{instance.instance_id} material desconocido: {instance.material_primary}")
            if instance.state_cleanliness not in allowed_cleanliness:
                report.errors.append(f"{instance.instance_id} state_cleanliness invalido.")
            if instance.state_wetness not in allowed_wetness:
                report.errors.append(f"{instance.instance_id} state_wetness invalido.")
            if instance.state_food_residue not in allowed_food:
                report.errors.append(f"{instance.instance_id} state_food_residue invalido.")
            if instance.state_liquid not in allowed_liquid:
                report.errors.append(f"{instance.instance_id} state_liquid invalido.")
            if instance.deformation not in allowed_deformation:
                report.errors.append(f"{instance.instance_id} deformation invalido.")
            if instance.visible_part not in allowed_visible:
                report.errors.append(f"{instance.instance_id} visible_part invalido.")
            if instance.background_complexity not in allowed_background:
                report.errors.append(f"{instance.instance_id} background_complexity invalido.")
            if instance.occlusion not in allowed_occlusion:
                report.errors.append(f"{instance.instance_id} occlusion invalido.")
            if instance.ambiguity not in allowed_ambiguity:
                report.errors.append(f"{instance.instance_id} ambiguity invalido.")
            if instance.target_bin not in allowed_bins:
                report.errors.append(f"{instance.instance_id} target_bin invalido.")

            if instance.interior_visible and instance.view_slot not in {"inner_view", "opening_neck"}:
                report.warnings.append(
                    f"{instance.instance_id} marca interior_visible=true pero view_slot={instance.view_slot}."
                )

    return report
