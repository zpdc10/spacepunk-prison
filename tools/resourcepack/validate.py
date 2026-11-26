#!/usr/bin/env python3
"""
Checks the resource pack before it ships (run by CI, standard library only):

- every JSON file parses,
- every asset key listed in docs/ARCHITECTURE.md has an item definition (assets/spacepunk/items/<key>.json),
- every model an item definition points to exists, and every texture a model points to exists,
- every animated texture has a frame strip whose height is a multiple of its width.

    python3 tools/resourcepack/validate.py
"""
from __future__ import annotations

import json
import re
import struct
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
PACK = REPO / "resourcepack"
ASSETS = PACK / "assets"
errors: list[str] = []


def err(msg):
    errors.append(msg)


def split_id(rid: str, default_ns="minecraft"):
    ns, _, path = rid.partition(":") if ":" in rid else (default_ns, "", rid)
    return ns, path


def png_size(p: Path):
    with p.open("rb") as f:
        head = f.read(24)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")
    return struct.unpack(">II", head[16:24])


def walk_models(node, out: set[str]):
    """Collects model ids referenced by an item definition tree."""
    if isinstance(node, dict):
        if node.get("type") in ("minecraft:model", "model") and isinstance(node.get("model"), str):
            out.add(node["model"])
        for v in node.values():
            walk_models(v, out)
    elif isinstance(node, list):
        for v in node:
            walk_models(v, out)


def check_model(model_id: str, seen: set[str]):
    if model_id in seen:
        return
    seen.add(model_id)
    ns, path = split_id(model_id)
    f = ASSETS / ns / "models" / f"{path}.json"
    if not f.exists():
        if ns == "minecraft":
            return  # vanilla model, shipped by the client
        err(f"missing model {model_id} ({f.relative_to(REPO)})")
        return
    data = json.loads(f.read_text(encoding="utf-8"))
    for tex in (data.get("textures") or {}).values():
        if tex.startswith("#"):
            continue
        tns, tpath = split_id(tex)
        t = ASSETS / tns / "textures" / f"{tpath}.png"
        if not t.exists() and tns != "minecraft":
            err(f"model {model_id} uses missing texture {tex}")
    parent = data.get("parent")
    if parent:
        check_model(parent if ":" in parent else f"minecraft:{parent}", seen)


def main():
    if not (PACK / "pack.mcmeta").exists():
        err("resourcepack/pack.mcmeta missing")

    for f in PACK.rglob("*"):
        if f.suffix in (".json", ".mcmeta"):
            try:
                json.loads(f.read_text(encoding="utf-8"))
            except Exception as e:  # noqa: BLE001
                err(f"bad JSON {f.relative_to(REPO)}: {e}")

    arch = (REPO / "docs" / "ARCHITECTURE.md").read_text(encoding="utf-8")
    keys = sorted(set(re.findall(r"`spacepunk:([a-z0-9_/]+)`", arch)))
    if not keys:
        err("no asset keys found in docs/ARCHITECTURE.md")
    for key in keys:
        if not (ASSETS / "spacepunk" / "items" / f"{key}.json").exists():
            err(f"asset key spacepunk:{key} has no item definition")

    seen: set[str] = set()
    items = sorted((ASSETS / "spacepunk" / "items").rglob("*.json"))
    for f in items:
        refs: set[str] = set()
        walk_models(json.loads(f.read_text(encoding="utf-8")), refs)
        if not refs:
            err(f"{f.relative_to(REPO)} references no model")
        for m in refs:
            check_model(m, seen)

    for meta in ASSETS.rglob("*.png.mcmeta"):
        png = meta.with_suffix("")
        if not png.exists():
            err(f"{meta.relative_to(REPO)} has no texture")
            continue
        w, h = png_size(png)
        if h % w:
            err(f"animated texture {png.relative_to(REPO)} is {w}x{h}, height must be a multiple of width")

    if errors:
        print("Resource pack check FAILED:")
        for e in errors:
            print("  - " + e)
        sys.exit(1)
    print(f"Resource pack OK: {len(keys)} asset keys, {len(items)} item definitions, {len(seen)} models checked")


if __name__ == "__main__":
    main()
