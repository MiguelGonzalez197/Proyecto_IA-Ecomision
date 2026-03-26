from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from ecosort_ml.data.manifest import load_manifest
from ecosort_ml.data.taxonomy import load_taxonomy
from ecosort_ml.data.validation import validate_manifest


def main() -> None:
    parser = argparse.ArgumentParser(description="Valida el manifest del dataset EcoSort.")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--taxonomy", type=Path, required=True)
    parser.add_argument("--report-json", type=Path, default=None)
    args = parser.parse_args()

    taxonomy = load_taxonomy(args.taxonomy)
    records = load_manifest(args.manifest)
    report = validate_manifest(records, taxonomy)

    payload = {
        "images": report.image_count,
        "instances": report.instance_count,
        "errors": report.errors,
        "warnings": report.warnings,
        "class_distribution": dict(report.class_distribution),
        "target_bin_distribution": dict(report.target_bin_distribution),
    }

    print(json.dumps(payload, indent=2, ensure_ascii=False))
    if args.report_json:
        args.report_json.parent.mkdir(parents=True, exist_ok=True)
        args.report_json.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")

    if report.errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()

