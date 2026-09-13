from pathlib import Path
import re
import shutil
import subprocess
import sys

from PIL import Image


ROOT = Path(r"D:\SimpleList")

PNG_DIR = ROOT / "artwork" / "avatars" / "png"
MASK_DIR = ROOT / "artwork" / "avatars" / "mask"
SVG_DIR = ROOT / "artwork" / "avatars" / "svg"
NORMALIZED_SVG_DIR = ROOT / "artwork" / "avatars" / "svg_normalized"
ANDROID_DIR = ROOT / "artwork" / "avatars" / "android"

VTRACER = ROOT / "tools" / "vtracer" / "vtracer.exe"

EXPECTED_AVATARS = [
    "person",
    "flower",
    "cat",
    "horse",
    "lightning",
    "coffee",
    "helmet",
    "paw",
    "book",
    "alien",
    "f1car",
    "music",
    "home",
    "heart",
    "star",
    "wrench",
    "camera",
    "fish",
    "football",
    "smiley",
]

ALPHA_THRESHOLD = 128

VTRACER_ARGS = [
    "--preset", "bw",
    "--mode", "spline",
    "--filter-speckle", "2",
    "--simplify", "1.5",
    "--path-precision", "3",
    "--optimize", "2",
]


def make_mask(input_png: Path, output_mask: Path) -> None:
    with Image.open(input_png) as source:
        image = source.convert("RGBA")
        alpha = image.getchannel("A")
        mask = alpha.point(lambda value: 0 if value >= ALPHA_THRESHOLD else 255)

    output_mask.parent.mkdir(parents=True, exist_ok=True)
    mask.convert("RGB").save(output_mask)


def trace_mask(
    input_mask: Path,
    output_svg: Path,
) -> subprocess.CompletedProcess:
    output_svg.parent.mkdir(parents=True, exist_ok=True)

    command = [
        str(VTRACER),
        *VTRACER_ARGS,
        str(input_mask),
        str(output_svg),
    ]

    return subprocess.run(
        command,
        capture_output=True,
        text=True,
    )


def normalize_svg(
    input_svg: Path,
    output_svg: Path,
) -> None:
    source = input_svg.read_text(encoding="utf-8")

    svg_match = re.search(r"<svg\b[^>]*>", source)

    if svg_match is None:
        raise RuntimeError("SVG opening tag not found")

    svg_tag = svg_match.group(0)

    width_match = re.search(
        r'\bwidth="([0-9]+(?:\.[0-9]+)?)"',
        svg_tag,
    )
    height_match = re.search(
        r'\bheight="([0-9]+(?:\.[0-9]+)?)"',
        svg_tag,
    )

    if width_match is None or height_match is None:
        raise RuntimeError("SVG width or height not found")

    width = width_match.group(1)
    height = height_match.group(1)

    normalized_tag = re.sub(
        r'\s+width="[^"]*"',
        "",
        svg_tag,
    )
    normalized_tag = re.sub(
        r'\s+height="[^"]*"',
        "",
        normalized_tag,
    )
    normalized_tag = re.sub(
        r'\s+viewBox="[^"]*"',
        "",
        normalized_tag,
    )

    normalized_tag = normalized_tag[:-1] + (
        f' width="24"'
        f' height="24"'
        f' viewBox="0 0 {width} {height}">'
    )

    normalized = (
        source[:svg_match.start()]
        + normalized_tag
        + source[svg_match.end():]
    )

    output_svg.parent.mkdir(parents=True, exist_ok=True)
    output_svg.write_text(
        normalized,
        encoding="utf-8",
        newline="\n",
    )


def find_npx() -> str:
    npx = shutil.which("npx.cmd") or shutil.which("npx")

    if npx is None:
        raise RuntimeError("npx was not found on PATH")

    return npx


def convert_svg_to_vector(
    npx: str,
    input_svg: Path,
    output_xml: Path,
) -> subprocess.CompletedProcess:
    output_xml.parent.mkdir(parents=True, exist_ok=True)

    command = [
        npx,
        "--yes",
        "--package",
        "svg2vectordrawable",
        "s2v",
        "-p",
        "3",
        "-i",
        str(input_svg),
        "-o",
        str(output_xml),
    ]

    return subprocess.run(
        command,
        capture_output=True,
        text=True,
    )


def validate_png_catalogue() -> list[Path]:
    available = {
        path.stem: path
        for path in PNG_DIR.glob("*.png")
        if not path.stem.endswith("-mask")
    }

    missing = [
        name
        for name in EXPECTED_AVATARS
        if name not in available
    ]

    unexpected = sorted(
        name
        for name in available
        if name not in EXPECTED_AVATARS
    )

    if missing:
        print("ERROR: missing PNG masters:")
        for name in missing:
            print(f" - {name}.png")

    if unexpected:
        print("ERROR: unexpected PNG masters:")
        for name in unexpected:
            print(f" - {name}.png")

    if missing or unexpected:
        raise RuntimeError("Avatar PNG catalogue does not match expected 20 IDs")

    return [
        available[name]
        for name in EXPECTED_AVATARS
    ]


def main() -> int:
    if not VTRACER.exists():
        print(f"ERROR: vtracer not found: {VTRACER}")
        return 1

    try:
        npx = find_npx()
    except RuntimeError as error:
        print(f"ERROR: {error}")
        return 1

    for directory in [
        PNG_DIR,
        MASK_DIR,
        SVG_DIR,
        NORMALIZED_SVG_DIR,
        ANDROID_DIR,
    ]:
        directory.mkdir(parents=True, exist_ok=True)

    try:
        png_files = validate_png_catalogue()
    except RuntimeError as error:
        print(f"ERROR: {error}")
        return 1

    failures = []

    for png_path in png_files:
        stem = png_path.stem

        mask_path = MASK_DIR / f"{stem}-mask.png"
        svg_path = SVG_DIR / f"{stem}.svg"
        normalized_svg_path = NORMALIZED_SVG_DIR / f"{stem}.svg"
        android_path = ANDROID_DIR / f"ic_avatar_{stem}.xml"

        try:
            print(f"[MASK ] {png_path.name}")
            make_mask(
                png_path,
                mask_path,
            )

            print(f"[TRACE] {stem}.svg")
            trace_result = trace_mask(
                mask_path,
                svg_path,
            )

            if trace_result.returncode != 0:
                raise RuntimeError(
                    trace_result.stderr.strip()
                    or trace_result.stdout.strip()
                    or "VTracer failed"
                )

            print(f"[NORM ] {stem}.svg")
            normalize_svg(
                svg_path,
                normalized_svg_path,
            )

            print(f"[XML  ] ic_avatar_{stem}.xml")
            convert_result = convert_svg_to_vector(
                npx,
                normalized_svg_path,
                android_path,
            )

            if convert_result.returncode != 0:
                raise RuntimeError(
                    convert_result.stderr.strip()
                    or convert_result.stdout.strip()
                    or "SVG to VectorDrawable conversion failed"
                )

            if not android_path.exists():
                raise RuntimeError(
                    "VectorDrawable converter returned success "
                    "but did not create the XML file"
                )

            print(f"[ OK  ] {stem}")

        except Exception as error:
            print(f"[FAIL ] {stem}: {error}")
            failures.append(stem)

    print()

    if failures:
        print("Completed with failures:")
        for name in failures:
            print(f" - {name}")
        return 1

    print("All 20 avatar assets generated successfully")
    print(f"PNG masters:     {PNG_DIR}")
    print(f"Masks:           {MASK_DIR}")
    print(f"Traced SVGs:     {SVG_DIR}")
    print(f"Normalised SVGs: {NORMALIZED_SVG_DIR}")
    print(f"Android XML:     {ANDROID_DIR}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
