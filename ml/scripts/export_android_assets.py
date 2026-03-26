from __future__ import annotations

import argparse
import sys
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))

from ecosort_ml.android.assets import export_android_assets


def main() -> None:
    parser = argparse.ArgumentParser(description="Exporta taxonomia y reglas del modulo ML a assets Android.")
    parser.add_argument("--export-config", type=Path, required=True)
    parser.add_argument("--taxonomy", type=Path, required=True)
    parser.add_argument("--rules", type=Path, required=True)
    args = parser.parse_args()

    config = yaml.safe_load(args.export_config.read_text(encoding="utf-8"))
    android_assets = config["android_assets"]
    export_android_assets(
        taxonomy_path=args.taxonomy,
        rules_path=args.rules,
        taxonomy_output=Path(android_assets["taxonomy_output"]),
        rules_output=Path(android_assets["rules_output"]),
    )
    print("Assets Android actualizados.")


if __name__ == "__main__":
    main()
