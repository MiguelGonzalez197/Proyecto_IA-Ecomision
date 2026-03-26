from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SRC_DIR = ROOT / "src"
if str(SRC_DIR) not in sys.path:
    sys.path.insert(0, str(SRC_DIR))


def main() -> None:
    parser = argparse.ArgumentParser(description="Inicializa carpetas base del dataset EcoSort.")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "data" / "raw", help="Directorio raiz raw.")
    parser.add_argument("--with-sample", action="store_true", help="Copia el manifest de ejemplo.")
    args = parser.parse_args()

    (args.output_dir / "images").mkdir(parents=True, exist_ok=True)
    (ROOT / "data" / "interim").mkdir(parents=True, exist_ok=True)
    (ROOT / "data" / "processed").mkdir(parents=True, exist_ok=True)

    if args.with_sample:
        sample_src = ROOT / "data" / "raw" / "sample_manifest.jsonl"
        sample_dst = args.output_dir / "master_manifest.jsonl"
        shutil.copy2(sample_src, sample_dst)
        print(f"Manifest de ejemplo copiado en: {sample_dst}")
    else:
        print("Carpetas creadas. Agrega tu manifest en ml/data/raw/master_manifest.jsonl")


if __name__ == "__main__":
    main()

