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
from ecosort_ml.training.splits import SplitConfig, grouped_split, save_split_map, summarize_splits


def main() -> None:
    parser = argparse.ArgumentParser(description="Genera splits sin leakage usando split_group.")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--out-map", type=Path, required=True)
    parser.add_argument("--out-summary", type=Path, default=None)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    records = load_manifest(args.manifest)
    split_map = grouped_split(records, SplitConfig(seed=args.seed))
    summary = summarize_splits(records, split_map)

    args.out_map.parent.mkdir(parents=True, exist_ok=True)
    save_split_map(args.out_map, split_map)
    print(json.dumps(summary, indent=2, ensure_ascii=False))

    if args.out_summary:
        args.out_summary.parent.mkdir(parents=True, exist_ok=True)
        args.out_summary.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()

