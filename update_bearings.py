#!/usr/bin/env python3
"""Update the deep-groove ball bearing catalog from manufacturer PDF tables.

Sources used:
- assets/bearings.json (existing standard 6000/6200/6300/6400 + double-row series)
- Timken Deep Groove Ball Bearing catalog (nomo.com) pages for:
  * 16000 narrow series (page 26)
  * 61700/61800/61900 thin-section series (page 27)
  * 62200/62300/63000 wide series (page 29)
  * 600/620/630/618/619 miniature series (page 30)
- PTI single-row ball-bearing PDFs for 61800/61900 full thin-section tables
- JVB catalog pages 19-20 for the 62000 widen series
- Hand-verified values for a few variants not covered above (62004-62008,
  63009-63010, 62313-62314).
"""
import argparse
import json
import os
import re
from pathlib import Path

try:
    from pypdf import PdfReader
except ImportError:
    raise SystemExit(
        "pypdf is required. Install with: python -m pip install pypdf"
    )

PROJECT = Path(__file__).resolve().parent
ASSET = PROJECT / "app" / "src" / "main" / "assets" / "bearings.json"
OUT = ASSET
DEFAULT_INPUT_DIR = PROJECT / "input"

# Regex for the first token of a bearing table row.
DESIGNATION_RE = re.compile(r"^\d{3,5}$|^\d{3}/\d$")

# Variants not found in the parsed PDF pages.
HARDCODED = [
    # 62000 widen series (10-40 mm bore)
    ("62004", 20, 42, 13),
    ("62005", 25, 47, 14),
    ("62006", 30, 55, 15),
    ("62007", 35, 62, 16),
    ("62008", 40, 68, 17),
    # 63000 widen series (45-50 mm bore)
    ("63009", 45, 75, 23),
    ("63010", 50, 80, 23),
    # 62300 wide series (65-70 mm bore)
    ("62313", 65, 140, 48),
    ("62314", 70, 150, 51),
]


def to_float(token: str) -> float | None:
    """Convert a token to float, treating both '.' and ',' as decimal marks."""
    if token in ("�", "•", "-", "–"):
        return None
    token = token.replace(",", ".")
    try:
        return float(token)
    except ValueError:
        return None


def is_numeric(token: str) -> bool:
    return to_float(token) is not None


def first_three_numbers(tokens: list[str]):
    """Return the first three numeric tokens as (d, D, B)."""
    nums = [to_float(t) for t in tokens if is_numeric(t)]
    if len(nums) >= 3:
        return nums[0], nums[1], nums[2]
    return None


def jvb_mm_dimensions(tokens: list[str]):
    """JVB tables list mm then inch; extract the mm d/D/B (tokens 1, 3, 5)."""
    if len(tokens) < 7:
        return None
    vals = []
    for i in (1, 3, 5):
        v = to_float(tokens[i])
        if v is None:
            return None
        vals.append(v)
    return tuple(vals)


def compute_series(designation: str) -> str:
    """Derive the dimension series from the ISO basic designation."""
    if "/" in designation:
        # Old small-bore notation: 618/3, 619/9 -> 61800, 61900
        return designation.split("/")[0] + "00"
    if len(designation) >= 4:
        # 6000/6200/16000/62200 etc: last two digits are the bore code
        return designation[:-2] + "00"
    # 3-digit extra-small: 603, 623, 633 -> 600, 620, 630
    return designation[:-1] + "0"


def _source_rank(source: str) -> int:
    """Precedence of input sources. Higher numbers override lower ones."""
    if source == "hardcoded":
        return 3
    if source == "JVB catalog":
        return 2
    if source and source != "existing":
        # Other parsed files (Timken/PTI text pages)
        return 1
    return 0


def add_record(
    by_desig: dict,
    sources: dict,
    desig: str,
    d: float,
    D: float,
    B: float,
    source: str = "",
):
    """Add or update a bearing record, honouring source precedence.

    Parsed and hardcoded sources override the existing catalog so source
    corrections are applied. Conflicting sources of the same precedence cause
    the generator to fail rather than silently picking a value.
    """
    if not (d > 0 and D > 0 and B > 0):
        return
    if d > 100:
        # The user requested coverage up to 100 mm bore
        return

    existing = by_desig.get(desig)
    if existing is not None:
        if (
            existing["boreMm"] == d
            and existing["odMm"] == D
            and existing["widthMm"] == B
        ):
            return

        existing_source = sources.get(desig, "")
        new_rank = _source_rank(source)
        old_rank = _source_rank(existing_source)

        if new_rank < old_rank:
            return
        if new_rank == old_rank:
            raise SystemExit(
                f"error: conflicting dimensions for {desig} "
                f"(existing {existing['boreMm']} x {existing['odMm']} x {existing['widthMm']} "
                f"from {existing_source}; new {d} x {D} x {B} from {source})"
            )
        print(f"  updating {desig:8s} {d:5.1f} x {D:5.1f} x {B:5.1f}  ({source})")
    else:
        print(f"  added {desig:8s} {d:5.1f} x {D:5.1f} x {B:5.1f}  ({source})")

    if desig.startswith("42") or desig.startswith("43"):
        btype = "Double-row deep groove ball bearing"
    else:
        btype = "Single-row deep groove ball bearing"
    by_desig[desig] = {
        "designation": desig,
        "type": btype,
        "boreMm": float(d),
        "odMm": float(D),
        "widthMm": float(B),
        "series": compute_series(desig),
    }
    sources[desig] = source


def load_existing(asset: Path = ASSET) -> tuple[dict, dict]:
    if asset.exists():
        with open(asset, encoding="utf-8") as f:
            data = json.load(f)
        by_desig = {b["designation"]: b for b in data}
        return by_desig, {desig: "existing" for desig in by_desig}
    return {}, {}


def main():
    parser = argparse.ArgumentParser(
        description="Update the deep-groove ball bearing catalog from manufacturer PDF tables."
    )
    parser.add_argument(
        "--input-dir",
        type=Path,
        default=DEFAULT_INPUT_DIR,
        help="Directory containing timken_page_*.txt, pti_*.txt, and jvb_catalog.pdf (default: %(default)s)",
    )
    args = parser.parse_args()
    input_dir = args.input_dir
    if not input_dir.is_dir():
        raise SystemExit(f"error: input directory not found: {input_dir}")

    by_desig, sources = load_existing()
    initial_count = len(by_desig)

    def add(desig: str, d: float, D: float, B: float, source: str = ""):
        add_record(by_desig, sources, desig, d, D, B, source)

    def parse_text_file(path: Path, mode: str = "generic"):
        if not path.exists():
            raise SystemExit(f"error: required input file not found: {path}")
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                tokens = line.split()
                if not tokens:
                    continue
                first = tokens[0]
                if not DESIGNATION_RE.match(first):
                    continue
                if mode == "jvb":
                    dims = jvb_mm_dimensions(tokens)
                else:
                    dims = first_three_numbers(tokens[1:])
                if dims is None:
                    continue
                d, D, B = dims
                if mode == "jvb" and not re.match(r"^(620|622|623|630)\d{2}$", first):
                    continue
                add(first, d, D, B, source=str(path.name))

    # Timken catalog pages already extracted to text files.
    for page in range(26, 31):
        parse_text_file(input_dir / f"timken_page_{page}.txt")

    # PTI 6800/6900 thin-section series (61800/61900).
    parse_text_file(input_dir / "pti_6800_page_1.txt")
    parse_text_file(input_dir / "pti_6800_page_2.txt")
    parse_text_file(input_dir / "pti_6900_page_1.txt")

    # JVB catalog pages 19-20 for 62000/62200/62300/63000.
    jvb_pdf = input_dir / "jvb_catalog.pdf"
    if not jvb_pdf.exists():
        raise SystemExit(f"error: required input file not found: {jvb_pdf}")
    reader = PdfReader(str(jvb_pdf))
    for page_index in (18, 19):  # 0-indexed for pages 19 and 20
        text = reader.pages[page_index].extract_text()
        for line in text.splitlines():
            line = line.strip()
            if not line:
                continue
            tokens = line.split()
            if not tokens:
                continue
            first = tokens[0]
            if not DESIGNATION_RE.match(first):
                continue
            if not re.match(r"^(620|622|623|630)\d{2}$", first):
                continue
            dims = jvb_mm_dimensions(tokens)
            if dims:
                d, D, B = dims
                add(first, d, D, B, source="JVB catalog")

    # Hand-verified variants.
    for desig, d, D, B in HARDCODED:
        add(desig, d, D, B, source="hardcoded")

    out = sorted(by_desig.values(), key=lambda b: (b["series"], b["designation"]))

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2)

    print(f"\nWrote {len(out)} bearings to {OUT}")
    print(f"Added {len(out) - initial_count} new entries")


if __name__ == "__main__":
    main()
