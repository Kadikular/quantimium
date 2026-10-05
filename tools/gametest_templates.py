#!/usr/bin/env python3
"""
Writes the GameTest structure templates: plain stone floors with air above, sized for what the
tests build on them. Tests place their machines block by block, so the templates stay empty.

    ./tools/gametest_templates.py

Output: src/main/resources/data/quantimium/structure/<name>.nbt (gzipped NBT, DataVersion 1.21.1).
"""

import gzip
import io
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "quantimium", "structure")
DATA_VERSION = 3955  # Minecraft 1.21.1

# name: (x, y, z) size. The floor is y = 0; everything above is air, so the world's own terrain
# never leaks into a test.
TEMPLATES = {
    "floor_9": (9, 6, 9),
    "floor_17": (17, 8, 17),
}

TAG_END, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 3, 8, 9, 10


def _string(value: str) -> bytes:
    data = value.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def _named(tag: int, name: str, payload: bytes) -> bytes:
    return bytes([tag]) + _string(name) + payload


def _compound(entries: bytes) -> bytes:
    return entries + bytes([TAG_END])


def _int_list(values) -> bytes:
    return bytes([TAG_INT]) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def _compound_list(items) -> bytes:
    return bytes([TAG_COMPOUND]) + struct.pack(">i", len(items)) + b"".join(_compound(i) for i in items)


def template(size) -> bytes:
    sx, sy, sz = size
    palette = [_named(TAG_STRING, "Name", _string("minecraft:smooth_stone")),
               _named(TAG_STRING, "Name", _string("minecraft:air"))]
    blocks = []
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                blocks.append(_named(TAG_LIST, "pos", _int_list([x, y, z]))
                              + _named(TAG_INT, "state", struct.pack(">i", 0 if y == 0 else 1)))
    root = (_named(TAG_INT, "DataVersion", struct.pack(">i", DATA_VERSION))
            + _named(TAG_LIST, "size", _int_list(list(size)))
            + _named(TAG_LIST, "palette", _compound_list(palette))
            + _named(TAG_LIST, "blocks", _compound_list(blocks))
            + _named(TAG_LIST, "entities", bytes([TAG_END]) + struct.pack(">i", 0)))
    return _named(TAG_COMPOUND, "", _compound(root))


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    for name, size in TEMPLATES.items():
        buffer = io.BytesIO()
        with gzip.GzipFile(fileobj=buffer, mode="wb", mtime=0) as handle:
            handle.write(template(size))
        path = os.path.join(OUT, name + ".nbt")
        with open(path, "wb") as handle:
            handle.write(buffer.getvalue())
        print(f"wrote {os.path.relpath(path, ROOT)}")


if __name__ == "__main__":
    main()
