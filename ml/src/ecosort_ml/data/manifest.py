from __future__ import annotations

import json
from pathlib import Path
from typing import Iterable

from ecosort_ml.schema import ImageRecord, record_from_dict


def load_manifest(path: str | Path) -> list[ImageRecord]:
    records: list[ImageRecord] = []
    with Path(path).open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            records.append(record_from_dict(json.loads(line)))
    return records


def write_jsonl(path: str | Path, rows: Iterable[dict]) -> None:
    with Path(path).open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=False) + "\n")

