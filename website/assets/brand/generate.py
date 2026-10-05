#!/usr/bin/env python3
"""Generates the website brand images from the source artwork.

Source: website/assets/brand/icon-source.png (owner-supplied, 1254x1254, opaque RGB).
Outputs (committed, served by VitePress):
  website/public/logo-round.png      512px circular mark, transparent corners
  website/public/logo-mono.png       96px monogram (navigation bar)
  website/public/favicon-32.png      32px monogram
  website/public/favicon-16.png      16px monogram
  website/public/apple-touch-icon.png 180px rounded-square artwork
  website/public/og-image.png        1200x630 social card

The artwork itself is only cropped, masked and resized; it is not redrawn or
recolored. The monogram is a simple letter mark in the artwork's palette and
exists because the illustration is not legible at 16px.

Run from the repository root:  python3 website/assets/brand/generate.py
Requires Pillow. The text in the monogram images and the social card is drawn
with macOS's Arial Rounded Bold, which is not redistributed. Without that font
the script writes only logo-round.png and apple-touch-icon.png and says why;
--allow-fallback-font writes the other four with a fallback font, and their
text will then not match the committed images.
"""
import argparse
import sys
from functools import cache
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "website" / "assets" / "brand" / "icon-source.png"
OUT = ROOT / "website" / "public"

SLATE = (76, 81, 101, 255)
CREAM = (253, 248, 241, 255)
ROSE = (200, 110, 140, 255)
PAPER = (253, 250, 246)

# The committed images' text is drawn with the first font. The others, and
# Pillow's built-in font after them, are fallbacks with different lettering.
FONT_CANDIDATES = [
    ("/System/Library/Fonts/Supplemental/Arial Rounded Bold.ttf", 0),
    ("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 0),
    ("/System/Library/Fonts/Helvetica.ttc", 1),
]
TEXT_IMAGES = ("logo-mono.png", "favicon-32.png", "favicon-16.png", "og-image.png")

ROUND_ZOOM = 1.10  # crop past the artwork's own rounded-rectangle border


@cache
def font_source() -> tuple[str, int] | None:
    """The first candidate that loads, or None for Pillow's built-in font."""
    for path, index in FONT_CANDIDATES:
        try:
            ImageFont.truetype(path, 10, index=index)
        except OSError:
            continue
        return path, index
    return None


def font(size: int) -> ImageFont.FreeTypeFont:
    source = font_source()
    if source is None:
        return ImageFont.load_default(size)
    path, index = source
    return ImageFont.truetype(path, size, index=index)


def load_source() -> Image.Image:
    return Image.open(SOURCE).convert("RGB")


def round_mark(src: Image.Image, size: int) -> Image.Image:
    side = src.width
    zoomed = int(side / ROUND_ZOOM)
    offset = (side - zoomed) // 2
    crop = src.crop((offset, offset, offset + zoomed, offset + zoomed))
    mask = Image.new("L", (zoomed, zoomed), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, zoomed - 1, zoomed - 1), fill=255)
    out = Image.new("RGBA", (zoomed, zoomed), (0, 0, 0, 0))
    out.paste(crop, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def rounded_square(src: Image.Image, size: int, radius_ratio: float = 0.22) -> Image.Image:
    side = src.width
    mask = Image.new("L", (side, side), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, side - 1, side - 1), radius=int(side * radius_ratio), fill=255
    )
    out = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    out.paste(src, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def monogram(size: int, radius_ratio: float = 0.26) -> Image.Image:
    scale = 4
    side = size * scale
    img = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle(
        (0, 0, side - 1, side - 1), radius=int(side * radius_ratio), fill=SLATE
    )
    dot = int(side * 0.13)
    margin = int(side * 0.10)
    draw.ellipse(
        (side - margin - dot, margin, side - margin, margin + dot), fill=ROSE
    )
    text_font = font(int(side * 0.60))
    bbox = draw.textbbox((0, 0), "S", font=text_font)
    width = bbox[2] - bbox[0]
    height = bbox[3] - bbox[1]
    draw.text(
        ((side - width) / 2 - bbox[0], (side - height) / 2 - bbox[1] - side * 0.015),
        "S",
        font=text_font,
        fill=CREAM,
    )
    return img.resize((size, size), Image.LANCZOS)


def fitted_font(draw: ImageDraw.ImageDraw, text: str, max_width: int, start: int):
    size = start
    while size > 10:
        candidate = font(size)
        bbox = draw.textbbox((0, 0), text, font=candidate)
        if bbox[2] - bbox[0] <= max_width:
            return candidate
        size -= 1
    return font(10)


def og_image(src: Image.Image, width: int = 1200, height: int = 630) -> Image.Image:
    card = Image.new("RGB", (width, height), PAPER)
    wash = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ImageDraw.Draw(wash).ellipse((-120, 60, 470, 660), fill=(244, 214, 222, 170))
    card = Image.alpha_composite(card.convert("RGBA"), wash).convert("RGB")
    draw = ImageDraw.Draw(card)

    mark = round_mark(src, 400)
    card.paste(mark, (80, (height - 400) // 2), mark)

    x = 552
    available = width - x - 64
    draw.text((x, 150), "Sprig", font=font(100), fill=(60, 64, 80))
    line_one = "An indentation-based, statically typed"
    line_two = "language for the JVM"
    tag = fitted_font(draw, line_one, available, 38)
    draw.text((x, 296), line_one, font=tag, fill=(96, 101, 118))
    draw.text((x, 296 + tag.size + 10), line_two, font=tag, fill=(96, 101, 118))
    bullets = "Sealed variants · exhaustive match · checked numerics"
    draw.text(
        (x, 430), bullets, font=fitted_font(draw, bullets, available, 27), fill=(164, 99, 124)
    )
    link = "github.com/ColinHouse/Sprig"
    draw.text((x, 516), link, font=fitted_font(draw, link, available, 28), fill=SLATE)
    return card


def check_text_font(allow_fallback: bool) -> bool:
    """Whether to write TEXT_IMAGES; warns when their text cannot use the committed font."""
    source = font_source()
    if source == FONT_CANDIDATES[0]:
        return True
    preferred = FONT_CANDIDATES[0][0]
    fallback = " ".join(font(10).getname())
    if source is None:
        fallback += " (Pillow's built-in font)"
    images = ", ".join(TEXT_IMAGES)
    if allow_fallback:
        print(
            f"warning: {preferred} was not found, so the text in {images} is drawn"
            f" in {fallback} and will not match the committed images.",
            file=sys.stderr,
        )
        return True
    print(
        f"warning: skipping {images}: their text needs {preferred}, which was not"
        f" found. Pass --allow-fallback-font to draw it in {fallback} instead;"
        " it will not match the committed images.",
        file=sys.stderr,
    )
    return False


def save(image: Image.Image, name: str) -> None:
    image.save(OUT / name, "PNG", optimize=True)
    print("wrote", (OUT / name).relative_to(ROOT), image.size)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generates the website brand images.")
    parser.add_argument(
        "--allow-fallback-font",
        action="store_true",
        help="also write the images with text when Arial Rounded Bold is missing,"
        " using a fallback font that will not match the committed images",
    )
    args = parser.parse_args()
    write_text = check_text_font(args.allow_fallback_font)

    OUT.mkdir(parents=True, exist_ok=True)
    src = load_source()
    save(round_mark(src, 512), "logo-round.png")
    save(rounded_square(src, 180), "apple-touch-icon.png")
    if write_text:
        save(monogram(96), "logo-mono.png")
        save(monogram(32), "favicon-32.png")
        save(monogram(16), "favicon-16.png")
        save(og_image(src), "og-image.png")


if __name__ == "__main__":
    main()
