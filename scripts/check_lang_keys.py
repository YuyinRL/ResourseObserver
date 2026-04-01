#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    lang_dir = root / "src" / "main" / "resources" / "assets" / "resourceobserver" / "lang"
    en_path = lang_dir / "en_us.json"
    zh_path = lang_dir / "zh_cn.json"

    en = load_json(en_path)
    zh = load_json(zh_path)

    missing = sorted([k for k in en.keys() if k not in zh])
    extra = sorted([k for k in zh.keys() if k not in en])

    print(f"en_us keys: {len(en)}")
    print(f"zh_cn keys: {len(zh)}")
    print(f"missing_in_zh: {len(missing)}")
    for key in missing:
        print(f"  MISSING {key}")
    print(f"extra_in_zh: {len(extra)}")
    for key in extra:
        print(f"  EXTRA   {key}")

    return 1 if missing or extra else 0


if __name__ == "__main__":
    sys.exit(main())
