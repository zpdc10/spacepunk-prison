"""
Workshop art (machines, conveyor, goods, menu icons) for the workshop module.
Called from generate.py main() with the generate module, so it shares the palette and Canvas helpers.

Writes:
- spacepunk:workshop/machine/<id>   3D block models (cube, front face = north) for every machine
- spacepunk:workshop/conveyor       conveyor item; the repeater block is restyled as an animated belt
- spacepunk:workshop/goods/<id>     16x16 sprites for every good in src/main/resources/workshop.yml
- spacepunk:workshop/ui/<name>      menu icons
All art is procedural and original.
"""
from __future__ import annotations

import math
import random

# ---------------------------------------------------------------------------------------------------------------
# Goods: id -> (form, family). Keep in sync with workshop.yml (families, byproducts, recipes).
# ---------------------------------------------------------------------------------------------------------------

FAMILIES = {
    # family: (hi, mid, lo)
    "slag": ((120, 110, 98), (88, 80, 70), (52, 48, 42)),
    "carbon": ((92, 90, 96), (52, 50, 56), (22, 20, 24)),
    "copper": ((236, 156, 96), (190, 104, 56), (120, 58, 30)),
    "steel": ((200, 204, 206), (140, 144, 148), (82, 86, 92)),
    "conduit": ((255, 110, 84), (204, 40, 30), (110, 16, 14)),
    "brass": ((238, 206, 120), (184, 142, 60), (112, 82, 30)),
    "cobalt": ((110, 150, 240), (50, 82, 186), (24, 36, 100)),
    "cryo": ((190, 255, 250), (70, 214, 222), (22, 110, 130)),
    "quartz": ((240, 236, 226), (196, 188, 176), (130, 122, 112)),
    "verdant": ((170, 255, 170), (80, 200, 90), (34, 110, 44)),
    "void": ((206, 156, 255), (126, 74, 196), (40, 22, 64)),
    "neutronium": ((255, 214, 120), (120, 64, 40), (36, 24, 22)),
    "sludge": ((170, 220, 70), (100, 150, 30), (50, 70, 16)),
}

FAMILY_GOODS = {
    "carbon": ("crushed_carbon", "washed_carbon", "graphite"),
    "copper": ("crushed_copper", "copper_concentrate", "copper_ingot"),
    "steel": ("crushed_steel", "steel_concentrate", "steel_ingot"),
    "conduit": ("crushed_conduit", "conduit_paste", "conduit_filament"),
    "brass": ("crushed_brass", "brass_concentrate", "brass_ingot"),
    "cobalt": ("crushed_cobalt", "cobalt_concentrate", "cobalt_ingot"),
    "cryo": ("cryo_grit", "cryo_slurry", "cryo_lens"),
    "quartz": ("quartz_grit", "quartz_slurry", "silicon_wafer"),
    "verdant": ("verdant_grit", "verdant_slurry", "verdant_crystal"),
    "void": ("void_grit", "void_residue", "void_essence"),
    "neutronium": ("neutronium_grit", "neutronium_slurry", "neutronium_ingot"),
}

# refined form per family
REFINED_FORM = {
    "carbon": "rod", "copper": "ingot", "steel": "ingot", "conduit": "spool", "brass": "ingot", "cobalt": "ingot",
    "cryo": "lens", "quartz": "wafer", "verdant": "crystal", "void": "orb", "neutronium": "ingot",
}

ALLOYS = {
    # id: (family a, family b)
    "conductive_alloy": ("copper", "steel"),
    "hardened_steel": ("steel", "carbon"),
    "circuit_alloy": ("brass", "conduit"),
    "cobalt_steel": ("cobalt", "steel"),
    "cryo_alloy": ("cryo", "cobalt"),
    "verdant_matrix": ("verdant", "quartz"),
    "void_lattice": ("void", "quartz"),
    "neutronium_composite": ("neutronium", "steel"),
}

COMPONENTS = ("wiring_harness", "armor_plate", "fuel_cell", "circuit_board", "induction_coil", "firing_mechanism",
              "weapon_receiver", "cryo_coil", "focusing_array", "void_capacitor", "reactor_core")

MACHINES = ("intake", "crusher", "generator", "depot", "radiator", "washer", "pump", "furnace", "chute", "forge", "fabricator")

UI = ("bay", "locker", "market", "shop", "assembly", "machines", "expand", "access", "input", "output", "rotate",
      "toggle_on", "toggle_off", "source", "filter", "pickup", "decor")


def workshop(g):
    goods(g)
    machines(g)
    conveyor(g)
    ui(g)


# ---------------------------------------------------------------------------------------------------------------
# Goods sprites
# ---------------------------------------------------------------------------------------------------------------

def finish(g, c, key):
    c.outline(g.OIL)
    c.save(g.SP / "textures" / "item" / f"{key}.png")
    g.generated_model(key)


def pile(g, seed, pal, grain=True):
    """Crushed grit: a heap of chunky grains."""
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    for y in range(7, 14):
        half = int((y - 6) * 0.95) + 1
        for x in range(8 - half, 8 + half):
            c.set(x, y, c.rng.choice((mid, mid, lo, hi if y < 10 else mid)))
    if grain:
        for _ in range(6):
            c.set(c.rng.randrange(5, 11), c.rng.randrange(8, 13), hi)
        for _ in range(4):
            c.set(c.rng.randrange(4, 12), c.rng.randrange(10, 14), lo)
    c.set(8, 7, hi)
    return c


def pan(g, seed, pal):
    """Washed concentrate: wet clumps in a steel tray."""
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    c.rect(2, 10, 13, 12, g.STEEL)
    c.rect(3, 12, 12, 13, g.GUN)
    for x in range(2, 14):
        c.set(x, 10, g.STEEL_L)
    c.blob(6, 8, 2, [hi, mid, lo], wrap=False)
    c.blob(10, 8, 2, [hi, mid, lo], wrap=False)
    c.blob(8, 7, 2, [hi, mid, mid], wrap=False)
    c.set(7, 6, g.WHITE)
    return c


def vial(g, seed, pal):
    """Slurry: a glass vial of glowing liquid."""
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    c.rect(6, 1, 9, 2, g.GUN_L)
    for y in range(3, 15):
        c.set(5, y, g.FROST_D)
        c.set(10, y, g.FROST_D)
    for x in range(5, 11):
        c.set(x, 14, g.FROST_D)
    for y in range(7, 14):
        for x in range(6, 10):
            c.set(x, y, hi if y == 7 else (mid if x < 8 else lo))
    for y in range(3, 7):
        c.set(6, y, (180, 200, 210))
    c.set(7, 9, g.WHITE)
    return c


def ingot(g, seed, pal):
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    for y in range(6, 12):
        inset = 11 - y
        for x in range(2 + inset // 2, 14 - inset // 2):
            c.set(x, y, hi if y == 6 else (mid if y < 10 else lo))
    for x in range(3, 13):
        c.set(x, 12, lo)
    c.set(5, 7, g.WHITE)
    c.set(6, 7, hi)
    c.line(4, 9, 11, 9, mid, wrap=False)
    return c


def rod(g, seed, pal):
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    for i in range(10):
        x, y = 3 + i, 12 - i
        c.set(x, y, mid)
        c.set(x + 1, y, lo)
        c.set(x, y - 1, hi)
    c.set(3, 12, g.STEEL)
    c.set(13, 2, g.STEEL_L)
    return c


def spool(g, seed, pal):
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    c.rect(3, 3, 12, 4, g.GUN_L)
    c.rect(3, 11, 12, 12, g.GUN)
    for y in range(5, 11):
        for x in range(4, 12):
            c.set(x, y, mid if (x + y) % 2 else hi if y % 3 == 0 else lo)
    c.set(12, 8, hi)
    c.set(13, 9, mid)
    return c


def lens(g, seed, pal):
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d <= 6.5:
                c.set(x, y, g.STEEL if d > 5.3 else (hi if d < 2 else mid if (x + y) < 15 else lo))
    c.set(5, 5, g.WHITE)
    c.set(6, 5, g.WHITE)
    return c


def wafer(g, seed, pal):
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    for y in range(3, 13):
        for x in range(3, 13):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 30:
                c.set(x, y, mid)
    for i in range(4, 12, 2):
        c.line(i, 4, i, 11, lo, wrap=False)
    c.line(4, 7, 11, 7, hi, wrap=False)
    c.set(5, 5, g.WHITE)
    return c


def crystal(g, seed, pal):
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    for y in range(2, 15):
        w = min(y - 1, 15 - y, 4)
        for x in range(8 - w, 8 + w):
            c.set(x, y, hi if x < 7 else mid if x < 9 else lo)
    c.set(6, 5, g.WHITE)
    return c


def orb(g, seed, pal):
    hi, mid, lo = pal
    c = g.Canvas(seed=seed)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8)
            if d <= 5.5:
                c.set(x, y, hi if d < 1.8 else mid if d < 3.8 else lo)
    for a in range(0, 360, 45):
        c.set(round(7.5 + 6.6 * math.cos(math.radians(a))), round(8 + 6.6 * math.sin(math.radians(a))), mid)
    return c


def alloy_bar(g, seed, pa, pb):
    c = g.Canvas(seed=seed)
    for y in range(5, 12):
        for x in range(2, 14):
            band = (x + y) // 3 % 2
            pal = pa if band else pb
            c.set(x, y, pal[0] if y == 5 else pal[1] if y < 10 else pal[2])
    c.set(4, 6, g.WHITE)
    for x in range(2, 14):
        c.set(x, 12, g.OIL)
    return c


def component(g, key):
    c = g.Canvas(seed=sum(map(ord, key)))
    F = FAMILIES
    if key == "wiring_harness":
        for i, col in enumerate((F["copper"][1], F["conduit"][1], F["cobalt"][1])):
            for x in range(2, 14):
                y = 6 + i * 2 + round(math.sin(x / 2 + i))
                c.set(x, y, col)
        c.rect(1, 5, 2, 12, g.GUN_L)
        c.rect(13, 5, 14, 12, g.GUN_L)
    elif key == "armor_plate":
        c.rect(2, 2, 13, 13, g.GUN)
        c.rect(3, 3, 12, 12, g.GUN_L)
        for p in ((3, 3), (12, 3), (3, 12), (12, 12)):
            c.set(*p, g.STEEL_L)
        c.line(3, 8, 12, 8, g.GUN_D, wrap=False)
        c.set(9, 5, g.RUST)
        c.set(5, 10, g.RUST_D)
    elif key == "fuel_cell":
        c.rect(5, 2, 10, 13, g.GUN)
        c.rect(6, 4, 9, 12, F["sludge"][1])
        c.rect(6, 4, 7, 12, F["sludge"][0])
        c.rect(6, 1, 9, 2, g.STEEL_L)
        c.set(8, 7, g.HAZ)
    elif key == "circuit_board":
        c.rect(2, 3, 13, 12, g.GREEN_D)
        for x in range(3, 13, 3):
            c.line(x, 4, x, 11, g.BRASS_L, wrap=False)
        c.line(3, 7, 12, 7, g.BRASS, wrap=False)
        c.rect(6, 5, 8, 9, g.OIL)
        c.set(7, 6, g.GUN_L)
    elif key == "induction_coil":
        for y in range(3, 13):
            c.set(7, y, g.STEEL)
            c.set(8, y, g.GUN_L)
        for y in range(4, 12, 2):
            c.line(4, y, 11, y + 1, F["copper"][0], wrap=False)
        c.set(7, 2, F["cobalt"][0])
        c.set(8, 13, F["cobalt"][1])
    elif key == "firing_mechanism":
        c.rect(3, 6, 12, 10, g.GUN)
        c.rect(4, 7, 11, 9, g.GUN_L)
        c.line(10, 10, 11, 13, g.GUN_D, wrap=False)
        c.set(6, 8, g.RED)
        c.set(8, 5, g.STEEL_L)
        c.set(9, 5, g.STEEL)
    elif key == "weapon_receiver":
        c.rect(1, 6, 14, 9, g.GUN_D)
        c.rect(2, 6, 13, 7, g.GUN)
        c.rect(9, 9, 11, 13, g.GUN_D)
        c.set(4, 8, g.HAZ)
        c.set(13, 7, g.STEEL_L)
    elif key == "cryo_coil":
        for a in range(0, 720, 15):
            r = 2 + a / 240
            c.set(round(8 + r * math.cos(math.radians(a))), round(8 + r * math.sin(math.radians(a))), F["cryo"][1])
        c.set(8, 8, g.WHITE)
        c.speckle(g.FROST_L, 3)
    elif key == "focusing_array":
        c.rect(3, 9, 12, 12, g.GUN)
        for x in (5, 8, 11):
            c.set(x - 1, 8, F["verdant"][1])
            c.set(x - 1, 7, F["verdant"][0])
        c.line(7, 2, 7, 6, F["verdant"][0], wrap=False)
        c.set(7, 1, g.WHITE)
    elif key == "void_capacitor":
        c.rect(4, 3, 11, 13, g.GUN_D)
        c.rect(5, 4, 10, 12, F["void"][2])
        for y in range(5, 12, 2):
            c.line(5, y, 10, y, F["void"][1], wrap=False)
        c.rect(6, 1, 9, 2, g.STEEL_L)
        c.set(7, 7, F["void"][0])
    elif key == "reactor_core":
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d <= 6.5:
                    c.set(x, y, g.GUN if d > 4.8 else g.MAGMA if d > 2.2 else g.MAGMA_L)
        for a in range(0, 360, 60):
            c.set(round(7.5 + 5.6 * math.cos(math.radians(a))), round(7.5 + 5.6 * math.sin(math.radians(a))), g.HAZ)
        c.set(7, 7, g.WHITE)
    return c


def goods(g):
    seed = 9000
    key = lambda i: f"workshop/goods/{i}"
    finish(g, pile(g, seed, FAMILIES["slag"]), key("slag_dust"))
    finish(g, vial(g, seed + 1, FAMILIES["sludge"]), key("toxic_sludge"))
    forms = {"ingot": ingot, "rod": rod, "spool": spool, "lens": lens, "wafer": wafer, "crystal": crystal, "orb": orb}
    for i, (fam, (crushed, washed, refined)) in enumerate(FAMILY_GOODS.items()):
        pal = FAMILIES[fam]
        finish(g, pile(g, seed + 10 + i, pal), key(crushed))
        if fam in ("cryo", "quartz", "verdant", "neutronium"):
            finish(g, vial(g, seed + 30 + i, pal), key(washed))
        else:
            finish(g, pan(g, seed + 30 + i, pal), key(washed))
        finish(g, forms[REFINED_FORM[fam]](g, seed + 50 + i, pal), key(refined))
    for i, (alloy, (a, b)) in enumerate(ALLOYS.items()):
        finish(g, alloy_bar(g, seed + 70 + i, FAMILIES[a], FAMILIES[b]), key(alloy))
    for comp in COMPONENTS:
        finish(g, component(g, comp), key(comp))


# ---------------------------------------------------------------------------------------------------------------
# Machines (16x16 block faces, cube models)
# ---------------------------------------------------------------------------------------------------------------

def casing(g, seed, tint=None):
    """Shared riveted machine casing so every machine reads as one family."""
    c = g.Canvas(seed=seed)
    c.noise([g.GUN_D, g.GUN, g.GUN, g.SOOT], [3, 5, 4, 1])
    if tint:
        for y in range(16):
            for x in range(16):
                p = c.get(x, y)
                c.set(x, y, g.mix(p[:3], tint, 0.3))
    for i in range(16):
        c.set(i, 0, g.GUN_L)
        c.set(0, i, g.GUN_L)
        c.set(i, 15, g.OIL)
        c.set(15, i, g.OIL)
        c.set(i, 1, g.GUN)
    for p in ((2, 2), (13, 2), (2, 13), (13, 13)):
        c.set(*p, g.STEEL_L)
        c.set(p[0] + 1, p[1] + 1, g.OIL)
    c.speckle(g.RUST, 4)
    c.speckle(g.RUST_D, 3)
    return c


def hazard_strip(g, c, y0, y1):
    for y in range(y0, y1 + 1):
        for x in range(1, 15):
            c.set(x, y, g.HAZ if (x + y) // 2 % 2 else g.OIL)


def vents(g, c, x0=3, x1=12, y0=4, y1=11):
    for y in range(y0, y1 + 1, 2):
        c.line(x0, y, x1, y, g.OIL, wrap=False)
        c.line(x0, y + 1, x1, y + 1, g.GUN_L, wrap=False)


def front_for(g, mid, seed):
    c = casing(g, seed)
    F = FAMILIES
    if mid == "intake":
        c.rect(3, 3, 12, 10, g.OIL)
        for x in range(4, 12, 2):
            c.line(x, 3, x, 10, g.GUN_L, wrap=False)
        hazard_strip(g, c, 12, 13)
    elif mid == "crusher":
        c.rect(2, 3, 13, 12, g.OIL)
        for x in range(2, 14):
            t = 1 + (x % 3 == 1)
            c.line(x, 3, x, 3 + t, g.STEEL_L, wrap=False)
            c.line(x, 12 - t, x, 12, g.STEEL, wrap=False)
        c.rect(6, 7, 9, 8, g.SLAG_L)
        hazard_strip(g, c, 13, 14)
    elif mid == "generator":
        c.rect(3, 4, 12, 12, g.OIL)
        for y in range(6, 12):
            for x in range(4, 12):
                c.set(x, y, g.MAGMA if (x * 7 + y * 3) % 5 else g.MAGMA_L)
        for x in range(4, 12, 2):
            c.line(x, 4, x, 12, g.GUN, wrap=False)
    elif mid == "depot":
        c.rect(3, 3, 12, 12, g.GUN_D)
        c.rect(4, 4, 11, 11, g.GUN)
        for i in range(4):
            c.line(7 - i, 5 + i, 8 + i, 5 + i, g.HAZ, wrap=False)
        c.rect(7, 9, 8, 10, g.HAZ)
        for i in range(3, 13):
            c.set(i, 3, g.HAZ_D)
            c.set(i, 12, g.HAZ_D)
    elif mid == "radiator":
        for x in range(2, 14):
            c.line(x, 2, x, 13, g.STEEL_L if x % 2 else g.GUN_D, wrap=False)
    elif mid == "washer":
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d <= 5.5:
                    c.set(x, y, g.STEEL if d > 4.4 else (F["cryo"][1] if y > 7 else F["cryo"][2]))
        c.set(6, 6, g.WHITE)
        c.set(9, 10, F["sludge"][1])
    elif mid == "pump":
        c.rect(2, 6, 13, 9, g.CYAN_D)
        c.line(2, 6, 13, 6, g.CYAN, wrap=False)
        for y in range(2, 6):
            c.set(7, y, g.STEEL)
        c.rect(5, 2, 9, 4, g.GUN_L)
        c.set(7, 3, g.RED)
        c.rect(4, 11, 11, 13, g.GUN_D)
    elif mid == "furnace":
        c.rect(3, 3, 12, 12, g.OIL)
        for y in range(4, 12):
            for x in range(4, 12):
                d = math.hypot(x - 7.5, y - 7.5)
                c.set(x, y, g.WHITE if d < 1.5 else g.MAGMA_L if d < 3 else g.MAGMA if d < 4.5 else g.MAGMA_D)
        c.line(3, 3, 12, 3, g.HAZ, wrap=False)
    elif mid == "chute":
        c.rect(3, 6, 12, 9, g.OIL)
        c.line(3, 5, 12, 5, g.BRASS, wrap=False)
        c.line(3, 10, 12, 10, g.BRASS_D, wrap=False)
        c.rect(6, 11, 9, 13, g.BRASS_L)
        c.set(7, 12, g.BRASS_D)
    elif mid == "forge":
        c.rect(2, 9, 13, 13, g.GUN_D)
        for cx in (5, 10):
            c.rect(cx - 2, 5, cx + 1, 9, g.STEEL)
            c.rect(cx - 1, 5, cx, 6, g.MAGMA_L)
        c.line(2, 9, 13, 9, g.BRASS, wrap=False)
    elif mid == "fabricator":
        c.rect(3, 3, 12, 9, g.OIL)
        for x in range(4, 12):
            c.set(x, 4 + (x * 3) % 4, g.GREEN)
        c.set(5, 7, g.GREEN_D)
        c.set(10, 5, (160, 255, 170))
        c.rect(5, 11, 10, 13, g.GUN_L)
        c.line(7, 9, 7, 11, g.STEEL, wrap=False)
    return c


def side_for(g, mid, seed):
    c = casing(g, seed)
    if mid == "radiator":
        return front_for(g, mid, seed)
    vents(g, c, 4, 11, 5, 10)
    if mid in ("washer", "pump"):
        c.line(1, 13, 14, 13, g.CYAN_D, wrap=False)
    elif mid in ("generator", "furnace", "forge"):
        c.line(1, 13, 14, 13, g.MAGMA_D, wrap=False)
    return c


def top_for(g, mid, seed):
    c = casing(g, seed, g.SOOT)
    if mid == "intake":
        for y in range(16):
            for x in range(16):
                d = max(abs(x - 7.5), abs(y - 7.5))
                if d < 5.5:
                    c.set(x, y, g.OIL if d < 3 else g.GUN_D)
        return c
    if mid == "radiator":
        return front_for(g, mid, seed)
    c.rect(5, 5, 10, 10, g.OIL)
    for i in range(5, 11):
        c.set(i, 7, g.GUN_L)
    # Output arrow (points north, the front).
    for i in range(3):
        c.line(7 - i, 2 + i, 8 + i, 2 + i, g.HAZ, wrap=False)
    return c


def machines(g):
    tex = g.SP / "textures" / "block" / "workshop"
    for i, mid in enumerate(MACHINES):
        seed = 9500 + i * 7
        front_for(g, mid, seed).save(tex / f"{mid}_front.png")
        side_for(g, mid, seed + 1).save(tex / f"{mid}_side.png")
        top_for(g, mid, seed + 2).save(tex / f"{mid}_top.png")
        casing(g, seed + 3, g.OIL).save(tex / f"{mid}_bottom.png")
        t = lambda s: f"spacepunk:block/workshop/{mid}_{s}"
        g.write_json(g.SP / "models" / "item" / "workshop" / "machine" / f"{mid}.json", {
            "parent": "minecraft:block/cube",
            "textures": {
                "particle": t("side"), "north": t("front"), "south": t("side"), "east": t("side"), "west": t("side"),
                "up": t("top"), "down": t("bottom"),
            },
        })
        g.item_definition(f"workshop/machine/{mid}")


# ---------------------------------------------------------------------------------------------------------------
# Conveyor: the repeater block becomes a low animated belt; the item is its own sprite.
# ---------------------------------------------------------------------------------------------------------------

def belt_frame(g, f):
    c = g.Canvas(seed=9700)
    for y in range(16):
        for x in range(16):
            if x < 2 or x > 13:
                c.set(x, y, g.HAZ if (y + f) // 2 % 2 == 0 and x in (0, 15) else g.GUN)
            else:
                c.set(x, y, g.SOOT if (y + f * 2) % 4 else g.GUN_D)
    # chevrons pointing up (north = flow direction in the unrotated model), scrolling
    for k in range(2):
        cy = (k * 8 - f * 2) % 16
        for i in range(4):
            y = (cy + i) % 16
            c.set(7 - i, y, g.HAZ_D)
            c.set(8 + i, y, g.HAZ_D)
    return c


def conveyor(g):
    tex = g.SP / "textures" / "block" / "workshop"
    g.stack([belt_frame(g, f) for f in range(4)]).save(tex / "conveyor_top.png")
    g.animate(tex / "conveyor_top.png", 3)
    casing(g, 9710).save(tex / "conveyor_side.png")
    b = "spacepunk:block/workshop/conveyor"
    g.write_json(g.SP / "models" / "block" / "workshop" / "conveyor.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": f"{b}_side", "top": f"{b}_top", "side": f"{b}_side"},
        "elements": [{
            "from": [0, 0, 0], "to": [16, 3, 16],
            "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": "down"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "north": {"uv": [0, 13, 16, 16], "texture": "#side"},
                "south": {"uv": [0, 13, 16, 16], "texture": "#side"},
                "west": {"uv": [0, 13, 16, 16], "texture": "#side"},
                "east": {"uv": [0, 13, 16, 16], "texture": "#side"},
            },
        }],
    })
    # Vanilla repeater facing=south uses y=0 and sends its signal north: our belt arrows point north too.
    rot = {"south": 0, "west": 90, "north": 180, "east": 270}
    g.write_json(g.MC / "blockstates" / "repeater.json", {
        "variants": {f"facing={f}": {"model": b, "y": y} if y else {"model": b} for f, y in rot.items()}
    })
    # Item sprite: a short belt section seen from above at an angle.
    c = g.Canvas(seed=9720)
    for y in range(5, 12):
        for x in range(1, 15):
            c.set(x, y, g.GUN if y in (5, 11) else g.SOOT)
    for x in range(2, 14, 4):
        for i in range(3):
            c.set(x + i, 8 - i, g.HAZ)
            c.set(x + i, 8 + i, g.HAZ)
    for x in (1, 14):
        c.set(x, 12, g.GUN_D)
        c.set(x, 13, g.GUN_D)
    finish(g, c, "workshop/conveyor")


# ---------------------------------------------------------------------------------------------------------------
# Menu icons
# ---------------------------------------------------------------------------------------------------------------

def ui(g):
    for i, name in enumerate(UI):
        c = g.panel(9800 + i)
        draw_icon(g, c, name)
        c.save(g.SP / "textures" / "item" / "workshop" / "ui" / f"{name}.png")
        g.generated_model(f"workshop/ui/{name}")


def draw_icon(g, c, name):
    F = FAMILIES
    if name == "bay":
        c.rect(3, 4, 12, 12, g.GUN_L)
        c.rect(4, 5, 11, 11, g.OIL)
        c.rect(5, 9, 6, 11, g.HAZ)
        c.rect(9, 8, 10, 11, g.COPPER)
        c.line(3, 4, 12, 4, g.STEEL_L, wrap=False)
    elif name == "locker":
        c.rect(3, 3, 12, 12, g.GUN_L)
        c.line(3, 7, 12, 7, g.OIL, wrap=False)
        c.rect(7, 5, 8, 9, g.HAZ)
    elif name == "market":
        c.rect(3, 9, 12, 12, g.BRASS_D)
        c.line(4, 9, 6, 5, g.GREEN, wrap=False)
        c.line(6, 5, 9, 8, g.GREEN, wrap=False)
        c.line(9, 8, 12, 3, g.GREEN, wrap=False)
    elif name == "shop":
        c.rect(4, 8, 11, 12, g.GUN_L)
        c.rect(6, 4, 9, 8, g.STEEL)
        c.set(7, 10, g.HAZ)
    elif name == "assembly":
        c.line(3, 12, 8, 7, g.STEEL_L, wrap=False)
        c.rect(8, 3, 12, 7, g.GUN_L)
        c.set(10, 5, g.GREEN)
    elif name == "machines":
        for x0, y0 in ((3, 3), (9, 3), (3, 9), (9, 9)):
            c.rect(x0, y0, x0 + 3, y0 + 3, g.GUN_L)
            c.set(x0 + 1, y0 + 1, g.GREEN if x0 + y0 != 18 else g.RED)
    elif name == "expand":
        for i in range(3):
            c.set(3 + i, 3, g.CYAN); c.set(3, 3 + i, g.CYAN)
            c.set(12 - i, 12, g.CYAN); c.set(12, 12 - i, g.CYAN)
            c.set(12 - i, 3, g.CYAN); c.set(12, 3 + i, g.CYAN)
            c.set(3 + i, 12, g.CYAN); c.set(3, 12 - i, g.CYAN)
        c.rect(6, 6, 9, 9, g.GUN_L)
    elif name == "access":
        c.rect(4, 3, 11, 12, g.GUN_L)
        c.rect(5, 4, 10, 11, g.OIL)
        c.set(9, 8, g.HAZ)
    elif name in ("input", "output"):
        c.rect(3, 9, 12, 12, g.GUN_L)
        for i in range(4):
            y = 3 + i if name == "input" else 7 - i
            c.line(7 - i if name == "input" else 7 - (3 - i), y, 8 + i if name == "input" else 8 + (3 - i), y, g.HAZ, wrap=False)
        c.line(7, 3, 7, 8, g.HAZ, wrap=False)
        c.line(8, 3, 8, 8, g.HAZ, wrap=False)
    elif name == "rotate":
        for a in range(30, 330, 10):
            c.set(round(7.5 + 4.5 * math.cos(math.radians(a))), round(7.5 + 4.5 * math.sin(math.radians(a))), g.HAZ)
        c.set(12, 4, g.HAZ); c.set(11, 3, g.HAZ); c.set(13, 5, g.HAZ_D)
    elif name in ("toggle_on", "toggle_off"):
        on = name == "toggle_on"
        c.rect(3, 6, 12, 10, g.GUN_D)
        c.rect(4, 7, 11, 9, g.GREEN_D if on else g.RED_D)
        x = 9 if on else 4
        c.rect(x, 6, x + 2, 10, g.STEEL_L)
    elif name == "source":
        c.rect(3, 8, 12, 12, g.RUST)
        c.rect(4, 6, 11, 8, F["copper"][1])
        c.set(6, 6, F["copper"][0])
        c.set(9, 7, F["steel"][0])
    elif name == "filter":
        for y in range(3, 9):
            c.line(3 + (y - 3), y, 12 - (y - 3), y, g.STEEL_L, wrap=False)
        c.rect(7, 9, 8, 12, g.STEEL)
        c.set(7, 13, g.CYAN)
    elif name == "pickup":
        c.line(3, 12, 8, 7, g.STEEL_L, wrap=False)
        c.line(8, 7, 12, 3, g.STEEL, wrap=False)
        c.line(8, 7, 12, 9, g.STEEL, wrap=False)
        c.set(3, 12, g.HAZ)
    elif name == "decor":
        c.rect(3, 3, 7, 7, (200, 128, 64))
        c.rect(8, 3, 12, 7, g.GUN_L)
        c.rect(3, 8, 7, 12, g.FROST)
        c.rect(8, 8, 12, 12, g.MAGMA_L)
