# SPDX-FileCopyrightText: Copyright 2026 EddizEge
# SPDX-License-Identifier: GPL-3.0-or-later

"""The NativeAYN mark: the "N over an infinity" brand, warm orange-red on the left and teal-blue on the right, on black.

The art comes from the owner's logo (android/images/logo.svg; android/tools/anka_mark_src.png is its mark cropped and made
transparent: the colours kept, the black background turned into alpha). anka_icons.py imports render() from this file and writes the
Android resources. Needs only Pillow. render(size, tile=True) is the mark on its rounded black tile; tile=False is the bare mark.
"""

import os
import sys

from PIL import Image, ImageDraw

TILE_TOP = (0, 0, 0)      # the mark's tile is black, the logo's own background
TILE_BOTTOM = (0, 0, 0)

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'anka_mark_src.png')

_cache = {}


def _mark(size):
    img = _cache.get(size)
    if img is None:
        img = Image.open(SRC).convert('RGBA')
        if img.size != (size, size):
            img = img.resize((size, size), Image.LANCZOS)
        _cache[size] = img
    return img


def render(size, simple=None, tile=True):
    """The mark at `size` px (RGBA, transparent where there is no art); tile=True puts it on the rounded black tile."""
    img = _mark(size)
    if not tile:
        return img
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    mask = Image.new('L', (size, size), 0)
    inset = round(size * 0.02)
    ImageDraw.Draw(mask).rounded_rectangle((inset, inset, size - 1 - inset, size - 1 - inset),
                                           radius=round(size * 0.22), fill=255)
    out.paste(Image.new('RGBA', (size, size), (0, 0, 0, 255)), (0, 0), mask)
    out.alpha_composite(img)
    return out


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    render(256).save(os.path.join(out, 'anka_mark_256.png'), optimize=True)
    print(os.path.join(out, 'anka_mark_256.png'))
