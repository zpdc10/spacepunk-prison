#!/usr/bin/env python3
"""
Generates every texture, model and item definition in /resourcepack.

All art is procedural pixel art drawn here (original, no vanilla assets), seeded so the output is the
same every run. Re-run after changing anything:

    python3 tools/resourcepack/generate.py

Then commit the regenerated files under resourcepack/. Needs Pillow (pip install pillow).
The generated files are committed so the Gradle build (and CI) never needs Python to zip the pack.
"""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2] / "resourcepack"
SP = ROOT / "assets" / "spacepunk"
MC = ROOT / "assets" / "minecraft"

# ---------------------------------------------------------------------------------------------------------------
# Palette: rust, oil, gunmetal, hazard paint, sickly glow.
# ---------------------------------------------------------------------------------------------------------------
OIL = (16, 14, 14)
SOOT = (30, 27, 26)
GUN_D = (44, 44, 48)
GUN = (66, 67, 72)
GUN_L = (98, 100, 104)
STEEL = (132, 134, 136)
STEEL_L = (172, 174, 172)
RUST_D = (74, 36, 20)
RUST = (122, 58, 28)
RUST_L = (168, 92, 44)
RUST_H = (200, 128, 64)
GRIME = (78, 70, 56)
SLAG_D = (48, 44, 40)
SLAG = (70, 64, 58)
SLAG_L = (94, 86, 76)
HAZ = (214, 166, 40)
HAZ_D = (150, 110, 22)
COPPER = (184, 98, 52)
COPPER_L = (226, 142, 84)
VERDI = (70, 150, 120)
RED_D = (110, 16, 14)
RED = (200, 34, 26)
RED_L = (255, 96, 70)
FROST_D = (70, 94, 120)
FROST = (128, 162, 190)
FROST_L = (196, 222, 236)
CYAN_D = (20, 110, 130)
CYAN = (60, 210, 220)
CYAN_L = (180, 255, 250)
LAPIS = (40, 70, 170)
LAPIS_L = (90, 130, 230)
VOID_D = (12, 8, 22)
VOID = (34, 20, 56)
VIOLET = (120, 70, 190)
VIOLET_L = (200, 150, 255)
MAGMA_D = (120, 40, 10)
MAGMA = (240, 110, 20)
MAGMA_L = (255, 210, 90)
OLIVE_D = (52, 58, 36)
OLIVE = (82, 90, 56)
OLIVE_L = (116, 124, 80)
WHITE = (226, 228, 230)
GREEN = (90, 200, 80)
GREEN_D = (40, 110, 40)
BRASS_D = (110, 80, 30)
BRASS = (176, 134, 56)
BRASS_L = (230, 196, 110)

written: list[Path] = []


def rgba(c, a=255):
    return (c[0], c[1], c[2], a) if len(c) == 3 else c


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


class Canvas:
    def __init__(self, w=16, h=16, seed=0):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rng = random.Random(seed)

    def set(self, x, y, c, a=255):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = rgba(c, a)

    def get(self, x, y):
        return self.px[x % self.w, y % self.h]

    def fill(self, c):
        for y in range(self.h):
            for x in range(self.w):
                self.set(x, y, c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def noise(self, colors, weights=None):
        """Fill with weighted random picks: the base grain of every block."""
        for y in range(self.h):
            for x in range(self.w):
                self.set(x, y, self.rng.choices(colors, weights)[0])

    def speckle(self, c, n):
        for _ in range(n):
            self.set(self.rng.randrange(self.w), self.rng.randrange(self.h), c)

    def blob(self, cx, cy, r, colors, wrap=True):
        """Irregular blob, darker at the rim. Wraps so tiled blocks stay seamless."""
        for dy in range(-r - 1, r + 2):
            for dx in range(-r - 1, r + 2):
                d = math.hypot(dx, dy) + self.rng.uniform(-0.6, 0.6)
                if d <= r:
                    t = d / max(r, 0.01)
                    c = colors[min(len(colors) - 1, int(t * len(colors)))]
                    x, y = cx + dx, cy + dy
                    if wrap:
                        x, y = x % self.w, y % self.h
                    self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c, wrap=True):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            x = round(x0 + (x1 - x0) * i / n)
            y = round(y0 + (y1 - y0) * i / n)
            if wrap:
                x, y = x % self.w, y % self.h
            self.set(x, y, c)

    def crack(self, x, y, length, c, glow=None):
        """Random walk crack, optional glowing core."""
        for _ in range(length):
            self.set(x % self.w, y % self.h, c)
            if glow and self.rng.random() < 0.5:
                self.set((x + 1) % self.w, y % self.h, glow)
            x += self.rng.choice((-1, 0, 1, 1))
            y += self.rng.choice((0, 1, 1))

    def outline(self, c, src_alpha=1):
        """Dark 1px outline around opaque pixels (for items)."""
        pts = []
        for y in range(self.h):
            for x in range(self.w):
                if self.px[x, y][3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < self.w and 0 <= ny < self.h and self.px[nx, ny][3] >= src_alpha:
                            pts.append((x, y))
                            break
        for x, y in pts:
            self.set(x, y, c)

    def save(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.img.save(path, optimize=True)
        written.append(path)


def stack(frames: list[Canvas]) -> Canvas:
    """Vertical strip for animated textures."""
    w, h = frames[0].w, frames[0].h
    out = Canvas(w, h * len(frames))
    for i, f in enumerate(frames):
        out.img.paste(f.img, (0, i * h))
    out.px = out.img.load()
    return out


def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    written.append(path)


def animate(path: Path, frametime: int, frames: list[int] | None = None, interpolate=False):
    anim = {"frametime": frametime}
    if interpolate:
        anim["interpolate"] = True
    if frames:
        anim["frames"] = frames
    write_json(path.with_name(path.name + ".mcmeta"), {"animation": anim})


# ---------------------------------------------------------------------------------------------------------------
# Block bases
# ---------------------------------------------------------------------------------------------------------------

def slag(seed) -> Canvas:
    """Stone becomes slag: clinker grey-brown, pitted, with rust bleeding through."""
    c = Canvas(seed=seed)
    c.noise([SLAG_D, SLAG, SLAG, SLAG_L, GRIME], [3, 5, 4, 2, 2])
    for _ in range(4):
        c.blob(c.rng.randrange(16), c.rng.randrange(16), c.rng.choice((1, 2)), [SLAG_L, SLAG, SLAG_D])
    for _ in range(5):
        c.set(c.rng.randrange(16), c.rng.randrange(16), SOOT)
    c.speckle(RUST, 6)
    c.speckle(RUST_D, 4)
    return c


def hull(seed, base=(GUN_D, GUN, SOOT)) -> Canvas:
    """Deepslate becomes hull plating: 8x8 plates, seams, rivets, scorch."""
    d, m, s = base
    c = Canvas(seed=seed)
    c.noise([d, m, d, s], [5, 3, 2, 1])
    for y in range(16):
        for x in range(16):
            if x % 8 == 0 or y % 8 == 0:
                c.set(x, y, OIL)
            elif x % 8 == 1 or y % 8 == 1:
                c.set(x, y, shade(m, 1.25))
    for ox, oy in ((0, 0), (8, 0), (0, 8), (8, 8)):
        for rx, ry in ((2, 2), (6, 2), (2, 6), (6, 6)):
            c.set(ox + rx, oy + ry, GUN_L)
            c.set(ox + rx + 1, oy + ry + 1, OIL)
    c.speckle(RUST, 5)
    c.speckle(RUST_D, 3)
    return c


def frost_panel(seed) -> Canvas:
    """Packed ice becomes a frosted cryo panel."""
    c = Canvas(seed=seed)
    c.noise([FROST_D, FROST, FROST, FROST_L], [2, 5, 4, 2])
    for y in range(16):
        c.set(0, y, FROST_D)
        c.set(15, y, mix(FROST_D, GUN, 0.5))
    for x in range(16):
        c.set(x, 0, FROST_D)
        c.set(x, 15, mix(FROST_D, GUN, 0.5))
    for _ in range(3):
        c.crack(c.rng.randrange(16), c.rng.randrange(16), 6, WHITE)
    c.speckle(WHITE, 8)
    return c


def ore(base: Canvas, clumps, colors, n=5, r=(1, 2), seed=0) -> Canvas:
    rng = random.Random(seed)
    pts = []
    tries = 0
    while len(pts) < n and tries < 200:
        tries += 1
        p = (rng.randrange(16), rng.randrange(16))
        if all(min(abs(p[0] - q[0]), 16 - abs(p[0] - q[0])) + min(abs(p[1] - q[1]), 16 - abs(p[1] - q[1])) > 5 for q in pts):
            pts.append(p)
    for x, y in pts:
        clumps(base, x, y, rng.choice(r), colors)
    return base


def blob_clump(c, x, y, r, colors):
    c.blob(x, y, r, colors)


def scrap_clump(c, x, y, r, colors):
    """Angular metal shards with a bright edge."""
    hi, mid, lo = colors
    for i in range(-r, r + 1):
        c.set(x + i, y - i, mid)
        c.set(x + i, y - i + 1, lo)
        c.set(x + i + 1, y - i, hi)
    c.set(x, y, hi)


def coal_clump(c, x, y, r, colors):
    c.blob(x, y, r, [OIL, SOOT, (22, 20, 20)])
    c.set(x - 1, y - 1, (60, 58, 62))  # oily sheen


def crystal_clump(c, x, y, r, colors):
    hi, mid, lo = colors
    for dy in range(-r - 1, r + 2):
        w = r + 1 - abs(dy)
        for dx in range(-w, w + 1):
            c.set((x + dx) % 16, (y + dy) % 16, mid if dx < 0 else lo)
    c.set(x % 16, (y - r - 1) % 16, hi)
    c.set((x - 1) % 16, y % 16, hi)


def conduit(base: Canvas, seed, lit: float) -> Canvas:
    """Redstone ore becomes conduit ore: buried red wiring that flickers."""
    rng = random.Random(seed)
    core = mix(RED_D, RED_L, lit)
    edge = mix(RED_D, RED, lit)
    y = rng.randrange(16)
    for x in range(16):
        base.set(x, y, edge)
        if rng.random() < 0.3:
            y = (y + rng.choice((-1, 1))) % 16
    x = rng.randrange(16)
    for yy in range(16):
        base.set(x, yy, edge)
        if rng.random() < 0.3:
            x = (x + rng.choice((-1, 1))) % 16
    for _ in range(4):
        px, py = rng.randrange(16), rng.randrange(16)
        base.set(px, py, core)
        base.set(px + 1, py, edge)
        base.set(px, py + 1, RED_D)
    return base


def flicker_strip(make, levels):
    return stack([make(l) for l in levels])


def void_glass_frame(seed, f) -> Canvas:
    c = Canvas(seed=seed)
    c.noise([VOID_D, VOID_D, VOID, (20, 14, 34)], [5, 3, 2, 2])
    rng = random.Random(seed + 1)
    for _ in range(7):
        x, y = rng.randrange(16), rng.randrange(16)
        c.set((x + f) % 16, y, VIOLET_L if rng.random() < 0.5 else WHITE)
    for i in range(16):
        c.set(i, (i + 3) % 16, (40, 30, 70))  # glass streak
    return c


def blocks():
    out = MC / "textures" / "block"
    slag(1).save(out / "stone.png")

    # Cobblestone: broken slag chunks with oil in the gaps.
    c = Canvas(seed=2)
    c.fill(OIL)
    for _ in range(14):
        c.blob(c.rng.randrange(16), c.rng.randrange(16), c.rng.choice((2, 2, 3)), [SLAG_L, SLAG, SLAG_D, SOOT])
    c.speckle(RUST, 5)
    c.save(out / "cobblestone.png")

    hull(3).save(out / "deepslate.png")
    t = hull(4)
    for y in range(16):
        for x in range(16):
            if (x + y) % 8 == 0:
                t.set(x, y, HAZ_D if (x // 4) % 2 else OIL)
    t.save(out / "deepslate_top.png")

    # Ores on slag (stone) and on hull plating (deepslate).
    for name, base_fn in (("", lambda s: slag(10 + s)), ("deepslate_", lambda s: hull(20 + s))):
        ore(base_fn(1), coal_clump, None, n=5, seed=31).save(out / f"{name}coal_ore.png")
        ore(base_fn(2), scrap_clump, (COPPER_L, COPPER, VERDI), n=5, seed=32).save(out / f"{name}copper_ore.png")
        ore(base_fn(3), scrap_clump, (STEEL_L, STEEL, GUN), n=5, seed=33).save(out / f"{name}iron_ore.png")
        frames = [conduit(base_fn(4), 34, lit) for lit in (0.55, 0.8, 1.0, 0.7, 0.3, 0.9)]
        stack(frames).save(out / f"{name}redstone_ore.png")
        animate(out / f"{name}redstone_ore.png", 3, [0, 1, 2, 2, 1, 0, 0, 3, 4, 4, 3, 5, 2, 1])
        frost = (lambda s: frost_panel(40 + s)) if name == "" else base_fn
        ore(frost(5), crystal_clump, (LAPIS_L, LAPIS, (20, 30, 90)), n=4, seed=35).save(out / f"{name}lapis_ore.png")
        ore(frost(6), crystal_clump, (CYAN_L, CYAN, CYAN_D), n=3, r=(1,), seed=36).save(out / f"{name}diamond_ore.png")
        ore(base_fn(7), blob_clump, (BRASS_L, BRASS, BRASS_D), n=4, r=(1,), seed=37).save(out / f"{name}gold_ore.png")
        ore(base_fn(8), crystal_clump, ((160, 255, 170), GREEN, GREEN_D), n=3, r=(1,), seed=38).save(out / f"{name}emerald_ore.png")

    frost_panel(50).save(out / "packed_ice.png")
    c = frost_panel(51)
    for x in range(16):
        c.set(x, 7, CYAN_D)
        c.set(x, 8, CYAN)
    c.save(out / "blue_ice.png")

    # Amethyst block: violet void crystal, faint shimmer.
    def amethyst(f):
        c = Canvas(seed=60)
        c.noise([VOID, VIOLET, (80, 46, 130)], [3, 2, 3])
        for i in range(5):
            crystal_clump(c, (i * 7 + f) % 16, (i * 5) % 16, 1, (VIOLET_L, VIOLET, VOID))
        return c
    stack([amethyst(f) for f in range(4)]).save(out / "amethyst_block.png")
    animate(out / "amethyst_block.png", 10, interpolate=True)

    # Obsidian becomes void glass: black glass with drifting star glints.
    stack([void_glass_frame(70, f) for f in range(8)]).save(out / "obsidian.png")
    animate(out / "obsidian.png", 6)

    # Ancient debris + netherite become neutronium: dense black alloy with molten seams.
    def neutronium(seed, glow):
        c = Canvas(seed=seed)
        c.noise([OIL, SOOT, (38, 30, 28)], [4, 4, 2])
        rng = random.Random(seed + 1)
        g = mix(MAGMA_D, MAGMA_L, glow)
        for _ in range(4):
            c.rng = random.Random(rng.random())
            c.crack(rng.randrange(16), rng.randrange(16), 9, mix(MAGMA_D, MAGMA, glow), g)
        return c
    for name, seed in (("ancient_debris_side", 80), ("ancient_debris_top", 81)):
        stack([neutronium(seed, g) for g in (0.4, 0.7, 1.0, 0.7)]).save(out / f"{name}.png")
        animate(out / f"{name}.png", 8, interpolate=True)
    c = Canvas(seed=82)
    c.noise([SOOT, GUN_D, (36, 34, 38)], [3, 3, 2])
    for i in range(16):
        c.set(i, 0, GUN_L); c.set(0, i, GUN_L); c.set(i, 15, OIL); c.set(15, i, OIL)
        c.set(i, 7, MAGMA_D); c.set(i, 8, MAGMA)
    for p in ((3, 3), (12, 3), (3, 12), (12, 12)):
        c.set(*p, STEEL)
    c.save(out / "netherite_block.png")

    # Raw ore and storage blocks that mines might use as rare veins.
    c = hull(90, (RUST_D, RUST, GRIME))
    c.speckle(RUST_H, 6)
    c.save(out / "raw_iron_block.png")
    c = hull(91, (COPPER, COPPER_L, VERDI))
    c.speckle(VERDI, 10)
    c.save(out / "raw_copper_block.png")
    c = Canvas(seed=92)
    c.noise([OIL, SOOT], [3, 2])
    for y in range(0, 16, 4):
        for x in range(16):
            c.set(x, y, (50, 48, 52))
    c.save(out / "coal_block.png")


# ---------------------------------------------------------------------------------------------------------------
# Vanilla item retextures (ore drops shown in cargo menus)
# ---------------------------------------------------------------------------------------------------------------

def item_chunk(seed, colors, shape="chunk") -> Canvas:
    hi, mid, lo = colors
    c = Canvas(seed=seed)
    if shape == "crystal":
        for y in range(3, 14):
            w = 5 - abs(y - 7) if y <= 7 else max(1, 5 - (y - 7) // 1)
            w = max(1, min(4, w))
            for x in range(8 - w, 8 + w):
                c.set(x, y, mid if x < 8 else lo)
        c.set(7, 4, hi); c.set(6, 6, hi); c.set(7, 6, hi)
    else:
        c.blob(8, 8, 5, [hi, mid, mid, lo], wrap=False)
        for _ in range(3):
            c.set(c.rng.randrange(5, 11), c.rng.randrange(5, 11), lo)
        c.set(6, 5, hi)
    c.outline(OIL)
    return c


def vanilla_items():
    out = MC / "textures" / "item"
    item_chunk(100, ((60, 58, 62), SOOT, OIL)).save(out / "coal.png")
    item_chunk(101, (COPPER_L, COPPER, VERDI)).save(out / "raw_copper.png")
    item_chunk(102, (STEEL_L, RUST_L, RUST_D)).save(out / "raw_iron.png")
    item_chunk(103, (BRASS_L, BRASS, BRASS_D)).save(out / "raw_gold.png")
    item_chunk(104, (LAPIS_L, LAPIS, (20, 30, 90)), "crystal").save(out / "lapis_lazuli.png")
    item_chunk(105, (CYAN_L, CYAN, CYAN_D), "crystal").save(out / "diamond.png")
    item_chunk(106, ((160, 255, 170), GREEN, GREEN_D), "crystal").save(out / "emerald.png")
    item_chunk(107, (VIOLET_L, VIOLET, VOID), "crystal").save(out / "amethyst_shard.png")
    item_chunk(108, (MAGMA, (60, 40, 34), OIL)).save(out / "netherite_scrap.png")
    # Redstone dust item: a coil of red wire.
    c = Canvas(seed=109)
    for a in range(0, 360, 12):
        r = 3 + a / 120
        c.set(round(8 + r * math.cos(math.radians(a))), round(8 + r * math.sin(math.radians(a))), RED)
    c.set(8, 8, RED_L)
    c.outline(RED_D)
    c.save(out / "redstone.png")


# ---------------------------------------------------------------------------------------------------------------
# Drills (32x32, diagonal like a vanilla tool: grip bottom-left, bit top-right)
# ---------------------------------------------------------------------------------------------------------------

DRILLS = {
    # body hi/mid/lo, bit hi/mid/lo, accent, extra
    "rusted": dict(body=(RUST_H, RUST, RUST_D), bit=(STEEL, GUN_L, GUN_D), accent=GRIME, extra="rust"),
    "salvaged": dict(body=(GUN_L, GUN, GUN_D), bit=(STEEL_L, STEEL, GUN), accent=VERDI, extra="patch"),
    "industrial": dict(body=(BRASS_L, HAZ, HAZ_D), bit=(STEEL_L, STEEL, GUN), accent=OIL, extra="hazard"),
    "military": dict(body=(OLIVE_L, OLIVE, OLIVE_D), bit=(GUN_L, GUN, GUN_D), accent=RED, extra="military"),
    "prototype": dict(body=(WHITE, STEEL_L, STEEL), bit=(CYAN_L, CYAN, CYAN_D), accent=CYAN, extra="glow"),
}


def drill(tier: str, frame: int = 0) -> Canvas:
    p = DRILLS[tier]
    c = Canvas(32, 32)
    c.rng = random.Random(sum(map(ord, tier)))
    rng = c.rng
    rust_spots = {(rng.randrange(32), rng.randrange(32)) for _ in range(40)}
    s2 = math.sqrt(2)
    for y in range(32):
        for x in range(32):
            # u: along the tool axis (bottom-left -> top-right), v: across it (+ is toward top-left / lit side)
            fx, fy = x + 0.5, y + 0.5
            u = (fx + (32 - fy)) / s2 - 1.5
            v = ((32 - fy) - fx) / s2
            col = None
            if 1.5 <= u < 12 and abs(v) <= 1.6:  # grip
                col = SOOT if int(u) % 3 else GUN
                if v > 0.6:
                    col = shade(col, 1.5)
            elif 12 <= u < 13.5 and abs(v) <= 3.2:  # trigger guard / flange
                col = GUN_L if v > 0 else GUN_D
            elif 13.5 <= u < 26 and abs(v) <= 4.6:  # motor housing
                hi, mid, lo = p["body"]
                col = hi if v > 2.6 else (mid if v > -1.8 else lo)
                if abs(v) > 4.0:
                    col = shade(lo, 0.7)
                if 16 <= u < 23 and -1.8 < v < 1.2 and int(u) % 2 == 0:  # cooling vents
                    col = OIL
                ex = p["extra"]
                if ex == "rust" and (x, y) in rust_spots:
                    col = RUST_D if rng.random() < 0.5 else GRIME
                elif ex == "patch" and 19 <= u < 24 and v > 0.8:
                    col = VERDI if v > 2.6 else shade(VERDI, 0.7)
                elif ex == "hazard" and 23.5 <= u < 26 and int(u * 1.4 + v) % 2 == 0:
                    col = OIL
                elif ex == "military" and 14.5 <= u < 16 and abs(v) < 1.2:
                    col = RED if frame % 2 == 0 else RED_D
                elif ex == "glow" and abs(v) < 0.7:
                    col = mix(CYAN_D, CYAN_L, (math.sin((u + frame * 2) * 0.9) + 1) / 2)
            elif 14.5 <= u < 18 and -9.5 <= v < -4.6:  # side pistol grip
                col = SOOT if int(-v) % 2 else GUN_D
                if u >= 17:
                    col = GUN
            elif 17 <= u < 24 and 4.6 < v <= 6.2:  # exhaust pipe on the lit side
                col = STEEL_L if v > 5.4 else STEEL
                if u >= 23:
                    col = OIL
            elif 26 <= u < 28 and abs(v) <= 3.6:  # chuck collar
                col = STEEL_L if v > 1.5 else (STEEL if v > -1.5 else GUN)
            elif 28 <= u < 41:  # conical bit with spiral flutes
                w = 3.4 * (1 - (u - 28) / 13)
                if abs(v) <= w:
                    hi, mid, lo = p["bit"]
                    flute = (u * 1.1 + v * 1.3 + frame * 0.75) % 3
                    col = hi if flute < 0.9 else (mid if flute < 2.0 else lo)
                    if v < -w + 0.8:
                        col = shade(col, 0.7)
            if col is not None:
                c.set(x, y, col)
    c.outline(OIL)
    return c


def drills():
    out = SP / "textures" / "item" / "drill"
    for tier in DRILLS:
        if tier == "prototype":
            stack([drill(tier, f) for f in range(8)]).save(out / f"{tier}.png")
            animate(out / f"{tier}.png", 2)
        elif tier == "military":
            stack([drill(tier, f) for f in range(2)]).save(out / f"{tier}.png")
            animate(out / f"{tier}.png", 12)
        else:
            drill(tier).save(out / f"{tier}.png")
        handheld_model(f"drill/{tier}", scale=1.0)


# ---------------------------------------------------------------------------------------------------------------
# Drill modules (16x16)
# ---------------------------------------------------------------------------------------------------------------

def module_base(seed, accent) -> Canvas:
    """Every module sits on the same chip housing so they read as one family."""
    c = Canvas(seed=seed)
    c.rect(2, 2, 13, 13, GUN_D)
    c.rect(3, 3, 12, 12, GUN)
    for i in range(3, 13):
        c.set(i, 3, GUN_L)
        c.set(3, i, GUN_L)
    for i in range(4, 13, 2):  # pins
        c.set(i, 1, STEEL); c.set(i, 14, STEEL); c.set(1, i, STEEL); c.set(14, i, STEEL)
    c.set(12, 12, accent); c.set(11, 12, shade(accent, 0.6))
    return c


def modules():
    out = SP / "textures" / "item" / "module"

    c = module_base(200, COPPER)  # magnet coil: U magnet wrapped in copper
    for y in range(5, 11):
        c.set(5, y, RED); c.set(10, y, STEEL_L)
        if y % 2:
            c.set(4, y, COPPER_L); c.set(6, y, COPPER); c.set(9, y, COPPER_L); c.set(11, y, COPPER)
    for x in range(5, 11):
        c.set(x, 11, GUN_D)
    c.set(5, 4, RED_L); c.set(10, 4, WHITE)
    c.save(out / "magnet_coil.png")

    c = module_base(201, (230, 60, 200))  # plasma cutter: nozzle and magenta jet
    c.rect(5, 8, 8, 10, STEEL); c.rect(5, 8, 8, 8, STEEL_L)
    for i, col in enumerate(((230, 60, 200), (255, 140, 240), (255, 220, 255))):
        c.set(9 + i, 9 - i, col); c.set(9 + i, 8 - i, col)
    c.set(12, 5, WHITE)
    c.save(out / "plasma_cutter.png")

    c = module_base(202, HAZ)  # scavenger rig: grabber claw
    c.line(5, 11, 8, 6, STEEL_L, wrap=False)
    c.line(8, 6, 11, 8, STEEL, wrap=False)
    c.line(11, 8, 11, 10, STEEL, wrap=False)
    c.line(8, 6, 6, 5, GUN_L, wrap=False)
    c.set(5, 11, HAZ); c.set(11, 10, HAZ_D)
    c.save(out / "scavenger_rig.png")

    c = module_base(203, CYAN)  # coolant loop: blue pipe loop with frost
    for a in range(0, 360, 20):
        c.set(round(8 + 3 * math.cos(math.radians(a))), round(8 + 3 * math.sin(math.radians(a))), CYAN)
    c.set(8, 8, FROST_L); c.set(5, 5, WHITE); c.set(11, 11, CYAN_D)
    c.save(out / "coolant_loop.png")

    c = module_base(204, RED)  # overcharger: lightning bolt
    for x, y in ((9, 4), (8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10), (5, 11)):
        c.set(x, y, HAZ)
    c.set(9, 4, MAGMA_L)
    c.save(out / "overcharger.png")

    c = module_base(205, GREEN)  # unknown / blueprint placeholder
    for y, row in enumerate(("..##..", ".#..#.", "....#.", "...#..", "......", "...#..")):
        for x, ch in enumerate(row):
            if ch == "#":
                c.set(5 + x, 5 + y, GREEN)
    c.save(out / "unknown.png")

    for name in ("magnet_coil", "plasma_cutter", "scavenger_rig", "coolant_loop", "overcharger", "unknown"):
        generated_model(f"module/{name}")


# ---------------------------------------------------------------------------------------------------------------
# UI icons (16x16)
# ---------------------------------------------------------------------------------------------------------------

def panel(seed, tint=None) -> Canvas:
    c = Canvas(seed=seed)
    c.noise([GUN_D, SOOT, GUN_D, (52, 50, 54)], [5, 2, 3, 2])
    if tint:
        for y in range(16):
            for x in range(16):
                p = c.get(x, y)
                c.set(x, y, mix(p[:3], tint, 0.45))
    for i in range(16):
        c.set(i, 0, GUN); c.set(0, i, GUN); c.set(i, 15, OIL); c.set(15, i, OIL)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(*p, GUN_L)
    c.speckle(RUST_D, 3)
    return c


def glyph(c: Canvas, rows, col, ox=0, oy=0, shadow=OIL):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                c.set(ox + x + 1, oy + y + 1, shadow)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "#":
                c.set(ox + x, oy + y, col)


ARROW_L = ["...#....", "..##....", ".#######", "########", ".#######", "..##....", "...#...."]
CROSS = ["##....##", ".##..##.", "..####..", "...##...", "..####..", ".##..##.", "##....##"]
CHECK = [".......##", "......##.", "##...##..", ".##.##...", "..###....", "...#....."]
PLUS = ["...##...", "...##...", "########", "########", "...##...", "...##..."]
UP = ["...##...", "..####..", ".######.", "########", "...##...", "...##...", "...##..."]
INFO = ["##", "..", "##", "##", "##", "##", "##"]
DOLLAR = ["..#..", ".####", "#.#..", ".###.", "..#.#", "####.", "..#.."]


def ui():
    out = SP / "textures" / "item" / "ui"
    names = []

    def save(c, name):
        c.save(out / f"{name}.png")
        names.append(name)

    save(panel(300), "panel")
    save(panel(301, RUST), "panel_rust")
    save(panel(302, HAZ_D), "panel_hazard")
    c = panel(303)
    for x in range(16):
        if (x // 2) % 2 == 0:
            for y in range(6, 10):
                c.set((x + y) % 16, y, HAZ)
    save(c, "divider")
    # Fully transparent filler for slots that should look empty.
    save(Canvas(seed=304), "blank")

    c = panel(310); glyph(c, ARROW_L, STEEL_L, 4, 4); save(c, "back")
    c = panel(311); glyph(c, ARROW_L, HAZ, 4, 4); save(c, "previous")
    c = panel(312); glyph(c, [r[::-1] for r in ARROW_L], HAZ, 4, 4); save(c, "next")
    c = panel(313, RED_D); glyph(c, CROSS, RED_L, 4, 4); save(c, "close")
    c = panel(314, RED_D); glyph(c, CROSS, RED, 4, 4); save(c, "cancel")
    c = panel(315, GREEN_D); glyph(c, CHECK, GREEN, 3, 5); save(c, "confirm")
    c = panel(316); glyph(c, UP, GREEN, 4, 4); save(c, "upgrade")
    c = panel(317); glyph(c, PLUS, HAZ, 4, 5); save(c, "add")
    c = panel(318); glyph(c, INFO, CYAN, 7, 4); save(c, "info")
    c = panel(319); glyph(c, DOLLAR, BRASS_L, 5, 4); save(c, "sell")

    c = panel(320)  # locked: padlock
    for x in range(6, 10):
        c.set(x, 4, STEEL)
    for y in range(5, 8):
        c.set(5, y, STEEL); c.set(10, y, STEEL)
    c.rect(4, 8, 11, 12, BRASS); c.rect(4, 8, 11, 8, BRASS_L)
    c.set(7, 10, OIL); c.set(8, 10, OIL); c.set(7, 11, OIL)
    save(c, "locked")

    c = panel(321)  # empty module socket
    c.rect(4, 4, 11, 11, OIL)
    for i in range(4, 12):
        c.set(i, 4, SOOT); c.set(4, i, SOOT); c.set(i, 11, GUN_L); c.set(11, i, GUN_L)
    for p in ((5, 5), (10, 5), (5, 10), (10, 10)):
        c.set(*p, HAZ_D)
    save(c, "socket_empty")

    c = panel(322)  # cargo hold: crate
    c.rect(3, 5, 12, 12, RUST); c.rect(3, 5, 12, 5, RUST_H)
    for y in range(5, 13):
        c.set(3, y, RUST_D); c.set(12, y, RUST_D); c.set(7, y, RUST_D)
    c.rect(3, 8, 12, 8, HAZ_D)
    save(c, "cargo")

    c = panel(323)  # rank / clearance badge
    for y in range(3, 13):
        w = 5 if y < 9 else 5 - (y - 8)
        for x in range(8 - w, 8 + w):
            c.set(x, y, BRASS if x < 8 else BRASS_D)
    glyph(c, UP, RED, 4, 4, shadow=BRASS_D)
    save(c, "rank")

    c = panel(324)  # prestige: reactor core
    c.blob(8, 8, 4, [MAGMA_L, MAGMA, MAGMA_D, SOOT], wrap=False)
    c.set(8, 8, WHITE)
    save(c, "prestige")

    c = panel(325)  # enchant: drill bit glyph
    for i in range(7):
        w = 3 - i // 2
        for j in range(-w, w + 1):
            c.set(8 + j, 3 + i + (j % 2), STEEL_L if j < 0 else STEEL)
    c.rect(6, 10, 9, 12, HAZ)
    save(c, "enchant")

    c = panel(326)  # heat vent
    for y in (5, 8, 11):
        c.rect(4, y, 11, y, OIL)
        c.set(4, y - 1, GUN_L)
    c.set(6, 3, (200, 200, 200), 160); c.set(9, 2, (200, 200, 200), 120)
    save(c, "vent")

    for name in names:
        generated_model(f"ui/{name}")

    # Heat gauge: 9 stages, picked by custom_model_data floats[0] in 0..1 (drill sets it from heat %).
    stages = 9
    for i in range(stages):
        t = i / (stages - 1)
        c = panel(330 + i)
        c.rect(3, 3, 12, 12, OIL)
        fill = round(t * 9)
        for y in range(12, 12 - fill, -1):
            col = GREEN if y > 9 else (HAZ if y > 6 else (MAGMA if y > 4 else RED_L))
            c.rect(4, y, 11, y, col)
        for y in range(3, 13, 2):
            c.set(12, y, STEEL)
        c.save(out / "heat_gauge" / f"{i}.png")
        generated_model(f"ui/heat_gauge/{i}")
    write_json(SP / "items" / "ui" / "heat_gauge.json", {
        "model": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "fallback": {"type": "minecraft:model", "model": "spacepunk:item/ui/heat_gauge/0"},
            "entries": [
                {"threshold": round((i - 0.5) / (stages - 1), 4),
                 "model": {"type": "minecraft:model", "model": f"spacepunk:item/ui/heat_gauge/{i}"}}
                for i in range(1, stages)
            ],
        }
    })


# ---------------------------------------------------------------------------------------------------------------
# Currency icons
# ---------------------------------------------------------------------------------------------------------------

def currency():
    out = SP / "textures" / "item" / "currency"

    def token(f):
        c = Canvas(seed=400)
        for y in range(16):
            for x in range(16):
                d = max(abs(x - 7.5), abs(y - 7.5)) * 0.6 + math.hypot(x - 7.5, y - 7.5) * 0.5
                if d < 6.4:
                    c.set(x, y, BRASS_L if x + y < 13 else (BRASS if x + y < 19 else BRASS_D))
        for y in range(5, 11):
            for x in range(5, 11):
                if (x in (5, 10) or y in (5, 10)) and not (x in (5, 10) and y in (5, 10)):
                    c.set(x, y, BRASS_D)
        c.rect(7, 7, 8, 8, RED if f == 0 else RED_L)
        c.outline(OIL)
        if f == 1:
            c.set(5, 4, WHITE)
        return c
    stack([token(0), token(0), token(1), token(0)]).save(out / "token.png")
    animate(out / "token.png", 10)
    generated_model("currency/token")

    def shard(f):
        c = Canvas(seed=401)
        for y in range(2, 15):
            w = (y - 1) // 2 if y < 7 else max(0, (15 - y) // 2)
            w = min(w, 4)
            for x in range(8 - w, 8 + w + 1):
                t = (x - (8 - w)) / max(1, 2 * w)
                c.set(x, y, mix(VIOLET_L, CYAN, t) if (x + y + f) % 5 else WHITE)
        c.outline(VOID)
        return c
    stack([shard(f) for f in range(5)]).save(out / "shard.png")
    animate(out / "shard.png", 4, interpolate=True)
    generated_model("currency/shard")

    c = Canvas(seed=402)  # credit chip (scrip)
    c.rect(2, 4, 13, 11, VERDI); c.rect(2, 4, 13, 4, (120, 200, 170))
    c.rect(4, 6, 6, 9, BRASS)
    for x in range(8, 13, 2):
        c.set(x, 7, OIL); c.set(x, 9, OIL)
    c.outline(OIL)
    c.save(out / "credit.png")
    generated_model("currency/credit")


# ---------------------------------------------------------------------------------------------------------------
# Models + item definitions
# ---------------------------------------------------------------------------------------------------------------

def item_definition(key: str):
    write_json(SP / "items" / f"{key}.json", {"model": {"type": "minecraft:model", "model": f"spacepunk:item/{key}"}})


def generated_model(key: str):
    write_json(SP / "models" / "item" / f"{key}.json", {
        "parent": "minecraft:item/generated", "textures": {"layer0": f"spacepunk:item/{key}"}})
    if not key.startswith("ui/heat_gauge/"):
        item_definition(key)


def handheld_model(key: str, scale=1.0):
    write_json(SP / "models" / "item" / f"{key}.json", {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": f"spacepunk:item/{key}"},
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.95, 0.95, 0.95]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.75, 0.75, 0.75]},
        },
    })
    item_definition(key)


def pack_meta():
    write_json(ROOT / "pack.mcmeta", {
        "pack": {
            "description": [
                {"text": "SPACEPUNK", "color": "#c8743a", "bold": True},
                {"text": " // station issue textures", "color": "gray"},
            ],
            # Item model definitions (assets/*/items) need format 46+ (1.21.4). Wide range so newer clients
            # (26.x) load it without an "incompatible" warning; old and new keys both present on purpose.
            "pack_format": 46,
            "supported_formats": {"min_inclusive": 46, "max_inclusive": 999},
            "min_format": 46,
            "max_format": 999,
        }
    })
    # pack.png: drill on a hull plate.
    bg = Image.new("RGBA", (128, 128))
    h = hull(500)
    bg.paste(h.img.resize((128, 128), Image.NEAREST))
    d = drill("industrial").img.resize((96, 96), Image.NEAREST)
    bg.alpha_composite(d, (16, 16))
    bg.save(ROOT / "pack.png", optimize=True)
    written.append(ROOT / "pack.png")


# ---------------------------------------------------------------------------------------------------------------
# Extra mine blocks + deck shell blocks (walls, rims, lights) used by the mines module (mines.yml)
# ---------------------------------------------------------------------------------------------------------------

def tinted(c: Canvas, tint, t) -> Canvas:
    for y in range(c.h):
        for x in range(c.w):
            p = c.px[x, y]
            if p[3]:
                c.set(x, y, mix(p[:3], tint, t))
    return c


def void_rock(seed) -> Canvas:
    c = Canvas(seed=seed)
    c.noise([VOID_D, (26, 22, 30), (38, 32, 44), SOOT], [4, 4, 2, 2])
    for _ in range(3):
        c.blob(c.rng.randrange(16), c.rng.randrange(16), 1, [(48, 40, 58), (38, 32, 44)])
    c.speckle(VOID, 5)
    return c


def plate(seed, hi, mid, lo, grid=8, rivets=True) -> Canvas:
    """Clean deck plating: panels with a lit top-left edge and a dark seam."""
    c = Canvas(seed=seed)
    c.noise([mid, mid, shade(mid, 0.92), shade(mid, 1.06)], [5, 3, 2, 2])
    for y in range(16):
        for x in range(16):
            if x % grid == grid - 1 or y % grid == grid - 1:
                c.set(x, y, lo)
            elif x % grid == 0 or y % grid == 0:
                c.set(x, y, hi)
    if rivets:
        for ox in range(0, 16, grid):
            for oy in range(0, 16, grid):
                c.set(ox + 1, oy + 1, hi)
                c.set(ox + grid - 3, oy + grid - 3, lo)
    return c


def grate(seed, bar, gap) -> Canvas:
    c = Canvas(seed=seed)
    c.fill(gap)
    for y in range(16):
        for x in range(16):
            if x % 4 == 0 or y % 4 == 0:
                c.set(x, y, bar if (x + y) % 2 else shade(bar, 1.15))
    c.speckle(RUST_D, 4)
    return c


def lamp(seed, glow_lo, glow, glow_hi, frame=0) -> Canvas:
    """Caged industrial light: steel frame, glowing core behind bars."""
    c = Canvas(seed=seed)
    rng = random.Random(seed * 31 + frame)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            t = 1 - d / 7.5
            c.set(x, y, mix(glow_lo, glow_hi, max(0.0, min(1.0, t * 1.4 + rng.uniform(-0.08, 0.08)))))
    for i in range(16):
        c.set(i, 0, GUN); c.set(0, i, GUN); c.set(i, 15, GUN_D); c.set(15, i, GUN_D)
        c.set(i, 1, GUN_D); c.set(1, i, GUN_D)
    for x in (5, 10):
        for y in range(2, 14):
            c.set(x, y, GUN_D)
    for y in (5, 10):
        for x in range(2, 14):
            c.set(x, y, GUN_D)
    for p in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(*p, STEEL)
    return c


def mine_extras():
    out = MC / "textures" / "block"

    tinted(slag(600), (120, 118, 112), 0.25).save(out / "andesite.png")
    tinted(slag(601), (70, 96, 70), 0.3).save(out / "tuff.png")
    c = frost_panel(602)
    tinted(c, WHITE, 0.35).save(out / "calcite.png")
    c = hull(603, (BRASS_D, BRASS, GRIME)); c.speckle(BRASS_L, 6); c.save(out / "raw_gold_block.png")

    plate(604, LAPIS_L, LAPIS, (20, 30, 90)).save(out / "lapis_block.png")
    c = plate(605, CYAN_L, CYAN_D, (10, 60, 70)); c.speckle(CYAN, 6); c.save(out / "diamond_block.png")
    c = plate(606, (160, 255, 170), GREEN_D, (20, 60, 20)); c.speckle(GREEN, 6); c.save(out / "emerald_block.png")

    void_rock(610).save(out / "blackstone.png")
    void_rock(611).save(out / "blackstone_top.png")
    ore(void_rock(612), crystal_clump, (WHITE, FROST_L, FROST), n=4, r=(1,), seed=613).save(out / "nether_quartz_ore.png")
    ore(void_rock(614), scrap_clump, (BRASS_L, BRASS, BRASS_D), n=5, seed=615).save(out / "gilded_blackstone.png")
    base = Canvas(seed=616)
    base.noise([(60, 22, 18), (44, 16, 14), RUST_D, SOOT], [4, 3, 2, 1])
    ore(base, blob_clump, (BRASS_L, BRASS, BRASS_D), n=6, r=(1,), seed=617).save(out / "nether_gold_ore.png")

    # Basalt: dark ribbed alloy columns.
    c = Canvas(seed=620)
    c.noise([SOOT, (38, 36, 40), GUN_D], [3, 3, 2])
    for x in range(0, 16, 4):
        for y in range(16):
            c.set(x, y, OIL); c.set(x + 1, y, GUN)
    c.save(out / "basalt_side.png")
    c = Canvas(seed=621)
    c.noise([SOOT, (38, 36, 40)], [1, 1])
    for r in (2, 5):
        for a in range(0, 360, 10):
            c.set(round(7.5 + r * math.cos(math.radians(a))), round(7.5 + r * math.sin(math.radians(a))), GUN)
    c.save(out / "basalt_top.png")
    hull(622, (SOOT, GUN_D, OIL)).save(out / "smooth_basalt.png")

    for name, seed in (("quartz_block_side", 630), ("quartz_block_top", 631), ("quartz_block_bottom", 632)):
        plate(seed, WHITE, (196, 198, 196), (130, 132, 134)).save(out / f"{name}.png")

    def crying(f):
        c = void_glass_frame(640, f)
        rng = random.Random(641)
        for _ in range(4):
            x, y0 = rng.randrange(16), rng.randrange(16)
            for k in range(3):
                c.set(x, (y0 + f + k) % 16, VIOLET_L if k == 2 else VIOLET)
        return c
    stack([crying(f) for f in range(8)]).save(out / "crying_obsidian.png")
    animate(out / "crying_obsidian.png", 4)

    # --- Deck shells (walls / rims) ---
    c = plate(650, COPPER_L, RUST, RUST_D)
    c.speckle(VERDI, 10); c.speckle((40, 110, 90), 5)
    c.save(out / "weathered_cut_copper.png")
    grate(651, STEEL, OIL).save(out / "polished_andesite.png")
    c = plate(652, GUN_L, GUN_D, OIL, grid=16)
    for y in range(3, 13, 3):
        for x in range(2, 14):
            c.set(x, y, SOOT)
    c.save(out / "polished_deepslate.png")
    c = plate(653, OLIVE_L, OLIVE, OLIVE_D)
    for i in range(16):
        if (i // 2) % 2 == 0:
            c.set(i, 15, HAZ); c.set(i, 14, HAZ)
    c.save(out / "polished_tuff.png")
    plate(654, WHITE, (178, 192, 200), FROST_D).save(out / "polished_diorite.png")
    plate(655, STEEL_L, STEEL, GUN, rivets=False).save(out / "smooth_stone.png")
    plate(656, STEEL_L, STEEL, GUN, rivets=False).save(out / "smooth_stone_slab_side.png")

    def bricks(seed, cracked):
        c = Canvas(seed=seed)
        c.noise([(30, 26, 36), (38, 34, 46), SOOT], [3, 3, 2])
        for y in range(16):
            for x in range(16):
                off = 4 if (y // 4) % 2 else 0
                if y % 4 == 3 or (x + off) % 8 == 7:
                    c.set(x, y, VOID_D)
                elif y % 4 == 0:
                    c.set(x, y, (54, 48, 64))
        if cracked:
            for _ in range(3):
                c.crack(c.rng.randrange(16), c.rng.randrange(16), 7, VOID_D, VIOLET)
        return c
    bricks(660, False).save(out / "polished_blackstone_bricks.png")
    bricks(661, True).save(out / "cracked_polished_blackstone_bricks.png")
    plate(662, (54, 48, 64), (34, 30, 40), VOID_D).save(out / "polished_blackstone.png")

    c = Canvas(seed=663)
    c.noise([SOOT, (40, 36, 36)], [2, 1])
    for x in range(16):
        for y in range(16):
            if x in (0, 15):
                c.set(x, y, OIL)
            elif x in (3, 12):
                c.set(x, y, MAGMA_D if y % 4 else MAGMA)
    c.save(out / "polished_basalt_side.png")
    c = plate(664, GUN, SOOT, OIL, grid=16)
    c.blob(8, 8, 2, [MAGMA, MAGMA_D, SOOT], wrap=False)
    c.save(out / "polished_basalt_top.png")

    # --- Deck lights ---
    for name, cols in (("ochre_froglight", ((120, 70, 10), HAZ, (255, 236, 150))),
                       ("verdant_froglight", ((20, 80, 30), GREEN, (210, 255, 190))),
                       ("pearlescent_froglight", ((70, 30, 90), VIOLET, (250, 220, 255)))):
        lamp(670 + len(name), *cols).save(out / f"{name}_side.png")
        lamp(680 + len(name), *cols).save(out / f"{name}_top.png")
    lamp(690, (90, 30, 6), MAGMA, MAGMA_L).save(out / "shroomlight.png")
    # Sea lantern is animated in vanilla, so ship our own strip + .mcmeta (a lone 16x16 would break it).
    frames = [lamp(691, CYAN_D, CYAN, CYAN_L, f) for f in range(5)]
    frames[3] = lamp(691, (10, 50, 60), CYAN_D, CYAN, 3)  # flicker dip
    stack(frames).save(out / "sea_lantern.png")
    animate(out / "sea_lantern.png", 5, [0, 1, 2, 0, 1, 2, 0, 4, 3, 0, 1, 2])


# ---------------------------------------------------------------------------------------------------------------
# Station terminals: every console in the station is a lodestone, so the lodestone becomes a CRT terminal.
# ---------------------------------------------------------------------------------------------------------------

TERM_G_D = (18, 90, 36)
TERM_G = (60, 200, 90)
TERM_G_L = (170, 255, 180)
TERM_BG = (6, 12, 8)
TERM_BG_L = (10, 20, 13)


def terminal_casing(seed) -> Canvas:
    c = Canvas(seed=seed)
    c.noise([GUN_D, GUN, GUN_D, SOOT], [4, 3, 3, 1])
    c.speckle(RUST_D, 5)
    c.speckle(RUST, 3)
    return c


def terminal_side(frame: int) -> Canvas:
    c = terminal_casing(700)
    rng = random.Random(701)
    # Bezel: lit top-left edge, dark bottom-right, around a 12x9 screen.
    for x in range(1, 15):
        c.set(x, 1, GUN_L); c.set(x, 11, OIL)
    for y in range(1, 12):
        c.set(1, y, GUN_L); c.set(14, y, OIL)
    for y in range(2, 11):
        for x in range(2, 14):
            c.set(x, y, TERM_BG_L if y % 2 else TERM_BG)  # scanlines
    # Text: lines of "words" that scroll up one row per frame, newest line typing in at the bottom.
    lines = []
    for _ in range(12):
        words, x = [], 0
        while x < 11:
            w = rng.randint(1, 4)
            words.append((x, min(w, 11 - x)))
            x += w + 1
        lines.append(words if rng.random() > 0.15 else [])
    rows = (3, 5, 7, 9)
    for i, y in enumerate(rows):
        line = lines[(i + frame) % len(lines)]
        for x0, w in line:
            for dx in range(w):
                c.set(3 + x0 + dx, y, TERM_G if (x0 + dx) % 5 else TERM_G_L)
    # Prompt + blinking cursor on the last row.
    c.set(3, 9, TERM_G_L)
    for x in range(4, 13):
        c.set(x, 9, TERM_BG)
    if frame % 2 == 0:
        c.set(5, 9, TERM_G_L); c.set(5, 8, TERM_G_D)
    # Screen glow bleeding onto the inner bezel.
    for x in range(2, 14):
        c.set(x, 2, mix(c.get(x, 2)[:3], TERM_G_D, 0.35))
    # Lower casing: status LEDs and a vent.
    c.set(2, 13, GREEN if frame % 4 else GREEN_D)
    c.set(4, 13, HAZ if frame % 3 == 0 else HAZ_D)
    c.set(6, 13, RED_D)
    for x in range(9, 14):
        c.set(x, 13, OIL); c.set(x, 14, GUN_L if x % 2 else OIL)
    for p in ((0, 0), (15, 0), (0, 15), (15, 15)):
        c.set(*p, STEEL)
    return c


def terminal():
    out = MC / "textures" / "block"
    stack([terminal_side(f) for f in range(8)]).save(out / "lodestone_side.png")
    animate(out / "lodestone_side.png", 8)
    c = terminal_casing(710)
    for i in range(16):
        c.set(i, 0, GUN_L); c.set(0, i, GUN_L); c.set(i, 15, OIL); c.set(15, i, OIL)
    for y in (4, 6, 8, 10):  # heat vent slots
        for x in range(4, 12):
            c.set(x, y, OIL)
        c.set(4, y - 1, GUN_L)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(*p, STEEL)
    c.set(13, 12, TERM_G)  # power LED
    c.save(out / "lodestone_top.png")


def main():
    if not ROOT.exists():
        sys.exit(f"missing {ROOT}")
    for sub in ("assets",):
        # Clean generated trees so removed assets do not linger.
        import shutil
        shutil.rmtree(ROOT / sub, ignore_errors=True)
    blocks()
    vanilla_items()
    drills()
    modules()
    ui()
    currency()
    mine_extras()
    terminal()
    # Rare drop items (raredrops module); art lives in raredrops_art.py.
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import raredrops_art
    raredrops_art.raredrops(sys.modules[__name__])
    # Fishing items, rods and icons (fishing module); art lives in fishing_art.py.
    import fishing_art
    fishing_art.fishing(sys.modules[__name__])
    # Undercroft + guns (undercroft and guns modules); art lives in undercroft_art.py.
    import undercroft_art
    undercroft_art.undercroft(sys.modules[__name__])
    # Workshop machines, conveyor, goods and menu icons (workshop module); art lives in workshop_art.py.
    import workshop_art
    workshop_art.workshop(sys.modules[__name__])
    # Module rig cells, new drill modules and scrap parts (drill + raredrops modules); art lives in scrap_art.py.
    import scrap_art
    scrap_art.scrap(sys.modules[__name__])
    pack_meta()
    print(f"wrote {len(written)} files under {ROOT}")


if __name__ == "__main__":
    main()
