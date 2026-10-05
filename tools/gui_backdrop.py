#!/usr/bin/env python3
"""
Makes the #0d0d0d (and #0c0d0d) backdrop round captured GUI panels transparent, filled in from the image edges so
the same dark inside a panel is kept. The capture (client/dev/WikiScreenshots.java) now does this
itself; this is for images captured before it did.

    ./tools/gui_backdrop.py wiki/pages/assets/gui/*.png
"""

import sys
from collections import deque

from PIL import Image

# #0d0d0d, and #0c0d0d in places.
BACKDROP = {(13, 13, 13, 255), (12, 13, 13, 255)}


def clear(path: str) -> int:
    image = Image.open(path).convert("RGBA")
    width, height = image.size
    pixels = image.load()
    queue = deque([(x, 0) for x in range(width)] + [(x, height - 1) for x in range(width)]
                  + [(0, y) for y in range(height)] + [(width - 1, y) for y in range(height)])
    cleared = 0
    seen = set()
    while queue:
        x, y = queue.popleft()
        if not (0 <= x < width and 0 <= y < height) or (x, y) in seen:
            continue
        seen.add((x, y))
        # Through what an earlier run already cleared, so a patch beyond it is reached too.
        if pixels[x, y][3] != 0:
            if pixels[x, y] not in BACKDROP:
                continue
            pixels[x, y] = (0, 0, 0, 0)
            cleared += 1
        queue.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))
    if cleared:
        image.save(path)
    return cleared


if __name__ == "__main__":
    for arg in sys.argv[1:]:
        print(f"{arg}: {clear(arg)} pixels cleared")
