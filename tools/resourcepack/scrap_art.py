"""
Module rig, new drill modules and scrap part art (drill module rig + raredrops scrap parts).
Called from generate.py main(). Draws:
  item/module/<id>.png                       new drill modules (on the shared chip housing)
  item/raredrop/part/<id>[_fried|_pristine]   scrap parts in three states
  item/ui/cell_*.png, item/ui/fuse.png        module rig menu cells and the fuser icon
  item/workshop/ui/refurbisher.png            workshop Refurbisher icon
Sprites are hand-drawn ASCII grids (original art). Letters: d dark, m mid, l light, h highlight, a accent,
A bright accent, w white glint; '.' is transparent. Each sprite gets a dark outline.
"""
from __future__ import annotations

import random

PART_SPRITES = {
    "wire": """
................
.......dd.......
.....ddmmdd.....
....dmlaalmd....
...dml.dd.lmd...
...dmldaadlmd...
...dmlaAAalmd...
...dmldaadlmd...
...dml.dd.lmd...
....dmlaalmd....
.....ddmmdd...a.
.......dd....aA.
.............a..
................
""",
    "board": """
................
................
..dddddddddddd..
..dmmmmmmmmmmd..
..dmaaAmmhhmmd..
..dmmmamddddmd..
..dmlmammdAAdmd.
..dmlmmmddddmd..
..dmlhhmmmammd..
..dmmmmmAaammd..
..dmlmlmlmlmmd..
..dddddddddddd..
...h.h.h.h.h....
................
""",
    "can": """
................
.......hh.......
......dhhd......
.....dmmmmd.....
.....dlaalmd....
.....dlAAlmd....
.....dlaalmd....
.....dlmmlmd....
.....dlmmlmd....
.....dlaalmd....
.....dlAAlmd....
.....dmmmmmd....
......dddd......
......h..h......
""",
    "motor": """
................
................
...ddddddd......
..dmmmmmmmd.....
..dmlllllmdddd..
..dmlaaalmdmmhh.
..dmlaAalmdmmhh.
..dmlaaalmdddd..
..dmlllllmd.....
..dmmmmmmmd.....
...ddddddd......
....d...d.......
...ddd.ddd......
................
""",
    "sensor": """
................
......aAa.......
.....a...a......
....a.aAa.a.....
.......h........
......dhd.......
.....dmmmd......
....dmllmmd.....
...dmlaalmmd....
...dmlaAlmmd....
...dmmmmmmmd....
...ddddddddd....
................
................
""",
    "relay": """
................
................
...dddddddddd...
...dmmmmmmmmd...
...dmhhhhhhmd...
...dmdaadaadmd..
...dmdAAdAAdmd..
...dmdaadaadmd..
...dmhhhhhhmd...
...dmmmmmmmmd...
...dddddddddd...
....h..hh..h....
....h..hh..h....
................
""",
    "core": """
................
.......dd.......
......dmmd......
....ddmllmdd....
...dmmaAAammd...
...dmaAwwAamd...
..dmlaAwAAalmd..
..dmlaAAAAalmd..
...dmaAAAAamd...
...dmmaAAammd...
....ddmllmdd....
......dmmd......
.......dd.......
................
""",
    "chip": """
................
....h.h.h.h.....
...dddddddddd...
..hdmmmmmmmmdh..
...dmaaaaaamd...
..hdmaAAAAamdh..
...dmaAwwAamd...
..hdmaAAAAamdh..
...dmaaaaaamd...
..hdmmmmmmmmdh..
...dddddddddd...
....h.h.h.h.....
................
................
""",
    "gyro": """
................
.......dd.......
.....ddaadd.....
....dal..lad....
...dal.dd.lad...
...da.dmmd.ad...
..dal.dAAd.lad..
..dal.dAAd.lad..
...da.dmmd.ad...
...dal.dd.lad...
....dal..lad....
.....ddaadd.....
.......dd.......
................
""",
    "shell": """
................
................
.....ddddd......
...ddmmmmmdd....
..dmmlllllmmd...
..dmlaaaaalmd...
.dmlaAAAAAalmd..
.dmlaAwwwAalmd..
.dmlaAAAAAalmd..
..dmlaaaaalmd...
..dmmlllllmmd...
...ddmmmmmdd..a.
.....ddddd...aA.
..............a.
""",
}


def palettes(g):
    """part id -> (sprite, palette d m l h a A)."""
    return {
        "copper_wiring": ("wire", (g.RUST_D, g.COPPER, g.COPPER_L, g.RUST_H, g.GUN, g.STEEL_L)),
        "circuit_board": ("board", (g.GREEN_D, (40, 120, 60), (70, 160, 80), g.BRASS_L, g.BRASS, g.GUN_D)),
        "capacitor": ("can", (g.GUN_D, g.LAPIS, g.LAPIS_L, g.STEEL_L, g.STEEL, g.WHITE)),
        "servo_motor": ("motor", (g.GUN_D, g.GUN, g.GUN_L, g.STEEL_L, g.HAZ_D, g.HAZ)),
        "power_cell": ("can", (g.GUN_D, g.OLIVE, g.OLIVE_L, g.STEEL_L, g.GREEN, (180, 255, 140))),
        "quantum_core": ("core", (g.VOID_D, g.VOID, g.VIOLET, g.WHITE, g.CYAN, g.CYAN_L)),
        "seismic_sensor": ("sensor", (g.GUN_D, g.SLAG, g.SLAG_L, g.STEEL_L, g.CYAN_D, g.CYAN)),
        "sonar_array": ("shell", (g.FROST_D, g.FROST, g.FROST_L, g.WHITE, g.CYAN_D, g.CYAN)),
        "gyro_stabilizer": ("gyro", (g.GUN_D, g.BRASS, g.BRASS_L, g.WHITE, g.BRASS_D, g.MAGMA_L)),
        "logic_chip": ("chip", (g.OIL, g.GUN_D, g.GUN, g.STEEL_L, g.RED_D, g.RED_L)),
        "military_relay": ("relay", (g.OLIVE_D, g.OLIVE, g.OLIVE_L, g.STEEL_L, g.RED, g.RED_L)),
        "data_core": ("chip", (g.VOID_D, g.VOID, g.VIOLET, g.STEEL_L, g.VIOLET, g.VIOLET_L)),
    }


def draw(g, sprite, pal, state="working", seed=0):
    d, m, l, h, a, A = pal
    if state == "fried":
        # Burnt: everything darker and browner, accents dead, soot and scorch marks.
        d, m, l, h = (g.shade(g.mix(c, g.SOOT, 0.45), 0.8) for c in (d, m, l, h))
        a, A = g.shade(g.mix(a, g.RUST_D, 0.6), 0.7), g.shade(g.mix(A, g.RUST, 0.6), 0.7)
    elif state == "pristine":
        d, m, l, h = (g.mix(c, g.WHITE, 0.12) for c in (d, m, l, h))
    colors = {"d": d, "m": m, "l": l, "h": h, "a": a, "A": A, "w": g.WHITE}
    c = g.Canvas(seed=seed)
    rows = [r for r in PART_SPRITES[sprite].strip("\n").split("\n")]
    oy = max(0, (16 - len(rows)) // 2)
    filled = []
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:16]):
            if ch in colors:
                c.set(x, y + oy, colors[ch])
                filled.append((x, y + oy))
    rng = random.Random(seed)
    if state == "fried" and filled:
        for _ in range(7):
            x, y = rng.choice(filled)
            c.set(x, y, g.SOOT)
        for _ in range(3):
            x, y = rng.choice(filled)
            c.set(x, y, g.MAGMA_D)
    c.outline(g.OIL)
    if state == "pristine" and filled:
        # A few white glints and a sparkle in the corner.
        for _ in range(3):
            x, y = rng.choice(filled)
            c.set(x, y, g.WHITE)
        for x, y in ((13, 1), (12, 2), (14, 2), (13, 3)):
            c.set(x, y, (255, 240, 170))
        c.set(13, 2, g.WHITE)
    return c


def parts(g):
    out = g.SP / "textures" / "item" / "raredrop" / "part"
    for i, (pid, (sprite, pal)) in enumerate(palettes(g).items()):
        for state, suffix in (("working", ""), ("fried", "_fried"), ("pristine", "_pristine")):
            draw(g, sprite, pal, state, seed=900 + i).save(out / f"{pid}{suffix}.png")
            g.generated_model(f"raredrop/part/{pid}{suffix}")


def modules(g):
    """New drill modules on the shared chip housing (generate.py module_base)."""
    out = g.SP / "textures" / "item" / "module"

    c = g.module_base(210, g.HAZ)  # servo actuator: motor drum and shaft
    c.rect(5, 6, 9, 10, g.GUN_L); c.rect(6, 7, 8, 9, g.HAZ_D); c.set(7, 8, g.HAZ)
    c.line(10, 8, 12, 8, g.STEEL_L, wrap=False)
    c.save(out / "servo_actuator.png")

    c = g.module_base(211, g.GREEN)  # ore sifter: mesh screen with ore bits
    for y in range(5, 12):
        for x in range(5, 12):
            if (x + y) % 2 == 0:
                c.set(x, y, g.STEEL)
    for x, y in ((6, 6), (9, 8), (7, 10)):
        c.set(x, y, g.COPPER_L)
    c.save(out / "ore_sifter.png")

    c = g.module_base(212, g.FROST_L)  # heat sink fins: vertical copper fins
    for x in range(5, 12, 2):
        c.line(x, 5, x, 11, g.COPPER_L, wrap=False)
        c.set(x, 5, g.RUST_H)
    c.save(out / "heat_sink_fins.png")

    c = g.module_base(213, (255, 112, 208))  # arc capacitor: can with an arc
    c.rect(6, 6, 9, 11, g.LAPIS); c.rect(6, 6, 9, 6, g.LAPIS_L)
    for x, y in ((10, 5), (11, 6), (10, 7), (11, 8)):
        c.set(x, y, (255, 160, 230))
    c.save(out / "arc_capacitor.png")

    c = g.module_base(214, g.MAGMA)  # thermal recycler: circular arrows in orange
    for x, y in ((6, 5), (7, 5), (8, 5), (9, 6), (10, 7), (10, 8), (9, 10), (8, 11), (7, 11), (6, 10), (5, 9), (5, 8), (5, 7)):
        c.set(x, y, g.MAGMA)
    c.set(10, 6, g.MAGMA_L); c.set(4, 9, g.MAGMA_L); c.set(7, 8, g.RED)
    c.save(out / "thermal_recycler.png")

    c = g.module_base(215, g.RED)  # graviton lens: lens ring with a red beam
    for x, y in ((6, 6), (7, 5), (8, 5), (9, 6), (10, 7), (10, 8), (9, 9), (8, 10), (7, 10), (6, 9), (5, 8), (5, 7)):
        c.set(x, y, g.STEEL_L)
    c.rect(7, 7, 8, 8, g.CYAN)
    c.line(9, 8, 12, 8, g.RED_L, wrap=False)
    c.save(out / "graviton_lens.png")

    c = g.module_base(216, g.CYAN)  # token condenser: drip into a cup
    c.rect(6, 9, 10, 11, g.GUN_L); c.rect(7, 10, 9, 10, g.CYAN_D)
    for y in (5, 7):
        c.set(8, y, g.CYAN)
    c.set(8, 8, g.CYAN_L)
    c.save(out / "token_condenser.png")

    c = g.module_base(217, g.STEEL_L)  # seismic dampener: hammer over waves
    c.rect(6, 5, 10, 6, g.STEEL); c.line(8, 7, 8, 8, g.GUN_L, wrap=False)
    for x in range(5, 12):
        c.set(x, 10 + (x % 2), g.CYAN)
    c.save(out / "seismic_dampener.png")

    c = g.module_base(218, g.VIOLET_L)  # quantum sorter: four glowing cells
    for (x, y), col in zip(((6, 6), (9, 6), (6, 9), (9, 9)), (g.VIOLET_L, g.CYAN, g.VIOLET, g.CYAN_L)):
        c.rect(x, y, x + 1, y + 1, col)
    c.save(out / "quantum_sorter.png")

    c = g.module_base(219, g.VIOLET)  # singularity core: black hole with an accretion ring
    for x, y in ((6, 6), (7, 5), (8, 5), (9, 6), (10, 7), (10, 8), (9, 9), (8, 10), (7, 10), (6, 9), (5, 8), (5, 7)):
        c.set(x, y, g.VIOLET_L)
    c.rect(7, 7, 8, 8, g.VOID_D); c.set(4, 7, g.MAGMA_L); c.set(11, 8, g.MAGMA_L)
    c.save(out / "singularity_core.png")

    for name in ("servo_actuator", "ore_sifter", "heat_sink_fins", "arc_capacitor", "thermal_recycler", "graviton_lens",
                 "token_condenser", "seismic_dampener", "quantum_sorter", "singularity_core"):
        g.generated_model(f"module/{name}")


def ui(g):
    out = g.SP / "textures" / "item" / "ui"

    def cell(name, fill, rim, seed, core=False, cross=False):
        c = g.Canvas(seed=seed)
        c.rect(0, 0, 15, 15, rim)
        c.rect(1, 1, 14, 14, fill)
        for i in range(1, 15):
            c.set(i, 1, g.mix(fill, g.WHITE, 0.15))
            c.set(1, i, g.mix(fill, g.WHITE, 0.15))
        c.set(2, 2, rim); c.set(13, 2, rim); c.set(2, 13, rim); c.set(13, 13, rim)  # rivets
        if core:
            for x, y in ((7, 5), (8, 5), (6, 6), (9, 6), (5, 7), (10, 7), (5, 8), (10, 8), (6, 9), (9, 9), (7, 10), (8, 10)):
                c.set(x, y, g.MAGMA_L)
            c.rect(7, 7, 8, 8, g.MAGMA)
        if cross:
            c.line(4, 4, 11, 11, g.RED_L, wrap=False)
            c.line(11, 4, 4, 11, g.RED_L, wrap=False)
        c.save(out / f"{name}.png")
        g.generated_model(f"ui/{name}")

    cell("cell_empty", (58, 58, 62), g.GUN_D, 501)
    cell("cell_core", (92, 56, 26), g.MAGMA_D, 502, core=True)
    cell("cell_none", (18, 17, 19), g.OIL, 503)
    cell("cell_common", (150, 146, 138), g.GUN, 504)
    cell("cell_rare", (52, 98, 170), (24, 50, 100), 505)
    cell("cell_epic", (120, 66, 170), (60, 30, 96), 506)
    cell("cell_legendary", (200, 140, 40), (110, 70, 16), 507)
    cell("cell_offline", (110, 26, 22), g.RED_D, 508, cross=True)

    c = g.Canvas(seed=510)  # fuser: two chips merging into a bright one
    c.rect(2, 3, 6, 7, g.GUN_L); c.rect(9, 3, 13, 7, g.GUN_L)
    c.set(4, 5, g.CYAN); c.set(11, 5, g.CYAN)
    c.line(5, 8, 7, 10, g.STEEL_L, wrap=False); c.line(10, 8, 8, 10, g.STEEL_L, wrap=False)
    c.rect(5, 10, 10, 14, g.VIOLET); c.rect(6, 11, 9, 13, g.VIOLET_L); c.set(7, 12, g.WHITE)
    c.outline(g.OIL)
    c.save(out / "fuse.png")
    g.generated_model("ui/fuse")

    wout = g.SP / "textures" / "item" / "workshop" / "ui"
    c = g.Canvas(seed=511)  # refurbisher: wrench over a scorched board
    c.rect(2, 8, 13, 13, (40, 90, 50)); c.rect(3, 9, 12, 12, (60, 130, 70))
    for x, y in ((4, 10), (7, 11), (10, 10), (11, 12)):
        c.set(x, y, g.SOOT)
    c.line(3, 2, 11, 10, g.STEEL_L, wrap=False); c.line(4, 2, 12, 10, g.STEEL, wrap=False)
    c.rect(2, 1, 4, 3, g.STEEL_L); c.set(3, 2, g.GUN_D)
    c.set(12, 2, g.HAZ); c.set(13, 3, g.HAZ_D)
    c.outline(g.OIL)
    c.save(wout / "refurbisher.png")
    g.generated_model("workshop/ui/refurbisher")


def scrap(g):
    modules(g)
    parts(g)
    ui(g)
