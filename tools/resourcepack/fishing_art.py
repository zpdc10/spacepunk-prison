"""
Fishing art for the fishing module: 40 mutated station fish, junk, lures, bait, rods (normal + cast) and menu
icons. Called from generate.py main(). Everything is procedural and seeded (original art).

Fish are built from per-species shape parameters plus a per-deck palette, so a new fish in fishing.yml gets a
sprite automatically (fishing.yml is scanned for ids); add an entry to SHAPES to give it a distinct look.
"""
from __future__ import annotations

import math
import re
from pathlib import Path

# Per-deck palettes: (dark, mid, light, belly, fin, accent)
DECKS = {
    "scrapyard": ((74, 36, 20), (122, 58, 28), (168, 92, 44), (184, 150, 110), (90, 70, 50), (214, 166, 40)),
    "reactor": ((30, 70, 30), (70, 140, 60), (130, 210, 90), (200, 240, 160), (40, 90, 50), (190, 255, 120)),
    "cryo": ((70, 94, 120), (128, 162, 190), (196, 222, 236), (236, 244, 250), (90, 120, 150), (180, 255, 250)),
    "hull": ((34, 20, 56), (80, 50, 130), (150, 110, 220), (200, 170, 255), (50, 30, 90), (120, 230, 255)),
    "foundry": ((60, 20, 10), (150, 50, 20), (240, 110, 20), (255, 210, 90), (90, 30, 10), (255, 240, 160)),
}

# id: (half length, half height, features...)
SHAPES = {
    # Scrapyard Runoff
    "rust_minnow": (4.0, 1.8, "flakes"),
    "oilgill": (5.0, 2.4, "rainbow"),
    "bolt_carp": (5.5, 3.2, "bolts"),
    "scrap_pike": (6.5, 2.0, "teeth", "stripes"),
    "rivet_eel": (7.0, 1.4, "rivets", "eel"),
    "tinplate_bass": (5.5, 3.4, "plates", "shine"),
    "gearback_gar": (6.8, 2.4, "gear", "teeth"),
    "bilge_king": (6.8, 4.6, "crown", "plates", "whiskers"),
    # Sludge Tanks
    "glowfry": (3.6, 1.8, "glow"),
    "sludge_puffer": (4.6, 4.2, "spikes", "spots"),
    "duct_perch": (5.0, 2.8, "stripes"),
    "isotope_trout": (6.0, 2.4, "glow", "spots"),
    "coolant_catfish": (6.0, 2.8, "whiskers"),
    "fission_ray": (5.0, 4.6, "ray", "glow"),
    "meltdown_mackerel": (6.4, 2.4, "stripes", "drip"),
    "halflife_sturgeon": (6.9, 3.6, "plates", "glow", "whiskers"),
    # Frozen Pools
    "frostfin": (4.6, 2.2, "spikes_top"),
    "cryo_shrimp": (4.0, 2.0, "shrimp"),
    "icebreaker_cod": (5.5, 3.0, "plates"),
    "glacial_char": (6.0, 2.6, "spots"),
    "stasis_eel": (7.0, 1.4, "eel", "glow"),
    "hoarfrost_salmon": (6.2, 2.8, "frost"),
    "zero_pike": (6.8, 2.2, "teeth", "frost", "shine"),
    "the_sleeper": (6.8, 4.4, "frozen", "frost", "plates"),
    # Breach Flood
    "vacuum_sprat": (3.8, 1.6, "rainbow"),
    "hullsucker": (5.0, 2.6, "sucker"),
    "static_goby": (4.6, 2.6, "zap"),
    "starlight_tetra": (4.4, 2.8, "stars"),
    "void_carp": (5.8, 3.2, "ghost"),
    "horizon_angler": (5.2, 3.8, "angler", "teeth"),
    "nebula_koi": (6.0, 3.2, "nebula", "stars"),
    "thing_outside": (6.9, 4.6, "eyes", "teeth", "ghost"),
    # Quench Tanks
    "slagfish": (5.0, 2.6, "flakes", "spots"),
    "ember_tetra": (3.8, 2.4, "glow"),
    "quench_loach": (6.0, 1.8, "eel", "steam"),
    "forgeback_snapper": (5.6, 3.2, "plates", "teeth"),
    "molten_arowana": (6.6, 2.4, "drip", "shine"),
    "neutronium_barracuda": (6.9, 2.0, "teeth", "dense"),
    "sunforged_marlin": (6.6, 2.6, "bill", "shine"),
    "foundry_heart": (6.6, 4.6, "core", "plates", "crown"),
}

RARITY_GLOW = {"common": None, "uncommon": None, "rare": (90, 170, 255), "epic": (196, 108, 255), "legendary": (255, 180, 58)}


def scan_fish(repo: Path):
    """[(id, area, rarity)] from src/main/resources/fishing.yml (simple line scan, no yaml dependency)."""
    out = []
    text = (repo / "src" / "main" / "resources" / "fishing.yml").read_text(encoding="utf-8")
    section = text.split("\nfish:", 1)[1].split("\njunk:", 1)[0]
    for m in re.finditer(r"^  ([a-z0-9_]+):\s*\{[^}]*?area:\s*([a-z0-9_]+),\s*rarity:\s*([a-z]+)", section, re.M):
        out.append((m.group(1), m.group(2), m.group(3)))
    return out


def fish_sprite(g, fid, area, rarity, seed):
    pal = DECKS.get(area, DECKS["scrapyard"])
    dark, mid, light, belly, fin, accent = pal
    shape = SHAPES.get(fid)
    rng = __import__("random").Random(seed)
    if shape is None:
        shape = (4.5 + rng.random() * 2, 2 + rng.random() * 2)
    a, b = min(shape[0], 6.0), shape[1]
    feats = set(shape[2:])
    c = g.Canvas(seed=seed)
    cx, cy = 9.3, 8.0
    eel = "eel" in feats
    ray = "ray" in feats
    if ghost := ("ghost" in feats):
        mid, light = g.mix(mid, (220, 220, 255), 0.3), g.mix(light, (255, 255, 255), 0.3)
    # Body
    for y in range(16):
        for x in range(16):
            dx = (x + 0.5 - cx) / a
            dy = (y + 0.5 - cy) / b
            if dx > 0:
                dy *= 1 + dx * 0.35  # blunt head, taper toward the snout
            if eel:
                dy *= 0.9
            if dx * dx + dy * dy <= 1.0:
                t = (y + 0.5 - (cy - b)) / (2 * b)
                col = light if t < 0.3 else (mid if t < 0.68 else belly)
                if (x + y) % 7 == 0:
                    col = g.shade(col, 0.9)
                c.set(x, y, col)
    # Tail: a forked fin behind the body
    tx = int(round(cx - a)) + 1
    span = 1 if eel else max(1, int(b * 0.6))
    for i in range(4):
        w = span + i
        for j in range(-w, w + 1):
            if i == 0 or abs(j) >= w - 1 or ("bill" in feats and abs(j) <= 1):
                c.set(tx - i, int(cy) + j, fin if abs(j) < w else g.shade(fin, 1.3))
    # Dorsal fin
    if not eel and not ray:
        top = int(cy - b)
        for i in range(3):
            c.set(int(cx) - 1 + i, top - (1 if i < 2 else 0), fin)
        c.set(int(cx) - 1, top - 2, g.shade(fin, 1.2))
    if ray:
        for i in range(-3, 4):
            c.set(int(cx) + i, int(cy - b) - 1 + abs(i) // 2, fin)
            c.set(int(cx) + i, int(cy + b) + 1 - abs(i) // 2, fin)
    # Features
    hx = int(cx + a * 0.55)
    ey = int(cy - b * 0.25)
    if "stripes" in feats:
        for x in range(int(cx - a) + 2, int(cx + a) - 1, 2):
            for y in range(int(cy - b) + 1, int(cy + b * 0.3)):
                if c.px[x, y][3]:
                    c.set(x, y, dark)
    if "spots" in feats:
        for _ in range(6):
            x, y = rng.randint(int(cx - a) + 1, int(cx + a) - 2), rng.randint(int(cy - b) + 1, int(cy + b) - 1)
            if c.px[x, y][3]:
                c.set(x, y, accent if "glow" in feats else dark)
    if "flakes" in feats:
        for _ in range(7):
            x, y = rng.randint(int(cx - a), int(cx + a)), rng.randint(int(cy - b), int(cy + b))
            if 0 <= x < 16 and 0 <= y < 16 and c.px[x, y][3]:
                c.set(x, y, g.RUST_H if area == "scrapyard" else g.SOOT)
    if "rainbow" in feats:
        cols = [(255, 90, 90), (255, 210, 80), (90, 220, 120), (90, 160, 255), (190, 110, 255)]
        for i, x in enumerate(range(int(cx - a) + 2, int(cx + a) - 1)):
            y = int(cy) - 1 + (i % 2)
            if c.px[x, y][3]:
                c.set(x, y, cols[i % len(cols)], 220)
    if "bolts" in feats or "rivets" in feats:
        for x in range(int(cx - a) + 2, int(cx + a) - 1, 2):
            y = int(cy)
            if c.px[x, y][3]:
                c.set(x, y, g.STEEL_L)
                c.set(x, y + 1, g.GUN_D)
    if "plates" in feats:
        for x in range(int(cx - a) + 1, int(cx + a), 3):
            for y in range(16):
                if c.px[x, y][3] and c.px[x, y][:3] != belly:
                    c.set(x, y, g.shade(mid, 0.75))
    if "gear" in feats:
        top = int(cy - b) - 1
        for i in range(-3, 3):
            if i % 2 == 0:
                c.set(int(cx) + i, top, g.BRASS_L)
                c.set(int(cx) + i, top - 1, g.BRASS)
            else:
                c.set(int(cx) + i, top, g.BRASS_D)
    if "spikes" in feats or "spikes_top" in feats:
        for x in range(int(cx - a) + 1, int(cx + a), 2):
            for y in range(16):
                if c.px[x, y][3]:
                    c.set(x, y - 1, light)
                    break
            if "spikes" in feats:
                for y in range(15, -1, -1):
                    if c.px[x, y][3]:
                        c.set(x, y + 1, light)
                        break
    if "frost" in feats or "frozen" in feats:
        for _ in range(8):
            x, y = rng.randint(0, 15), rng.randint(0, 15)
            if c.px[x, y][3]:
                c.set(x, y, g.WHITE)
    if "frozen" in feats:
        for y in range(16):
            for x in range(16):
                p = c.px[x, y]
                if p[3]:
                    c.set(x, y, g.mix(p[:3], g.FROST_L, 0.35))
        for i in range(3):
            c.set(int(cx) - 2 + i * 2, int(cy - b) - 1, g.FROST_L, 200)
    if "glow" in feats or "core" in feats:
        c.set(int(cx), int(cy), accent)
        c.set(int(cx) - 1, int(cy), g.shade(accent, 0.8))
        c.set(int(cx), int(cy) + 1, g.shade(accent, 0.8))
    if "core" in feats:
        c.blob(int(cx) - 1, int(cy), 2, [g.WHITE, accent, g.MAGMA], wrap=False)
    if "drip" in feats:
        for x in (int(cx) - 2, int(cx) + 1):
            for y in range(15, -1, -1):
                if c.px[x, y][3]:
                    if y + 1 < 16:
                        c.set(x, y + 1, accent)
                    if y + 2 < 16:
                        c.set(x, y + 2, g.shade(accent, 0.7))
                    break
    if "stars" in feats:
        for _ in range(5):
            x, y = rng.randint(int(cx - a) + 1, int(cx + a) - 1), rng.randint(int(cy - b) + 1, int(cy + b) - 1)
            if c.px[x, y][3]:
                c.set(x, y, g.WHITE)
    if "nebula" in feats:
        for y in range(16):
            for x in range(16):
                p = c.px[x, y]
                if p[3] and (x * 3 + y * 5) % 11 < 3:
                    c.set(x, y, g.mix(p[:3], (255, 120, 200), 0.5))
    if "zap" in feats:
        for x, y in ((int(cx) - 2, int(cy) - 1), (int(cx) - 1, int(cy)), (int(cx), int(cy) - 1), (int(cx) + 1, int(cy))):
            c.set(x, y, g.HAZ)
    if "dense" in feats:
        for y in range(16):
            for x in range(16):
                p = c.px[x, y]
                if p[3]:
                    c.set(x, y, g.mix(p[:3], (30, 30, 40), 0.35))
        c.set(int(cx), int(cy), (255, 255, 255))
    if "steam" in feats:
        for i in range(3):
            c.set(int(cx) - 1 + i * 2, int(cy - b) - 2 - (i % 2), (220, 220, 220), 140)
    if "shrimp" in feats:
        for x in range(int(cx - a) + 1, int(cx + a), 2):
            for y in range(16):
                if c.px[x, y][3]:
                    c.set(x, y, g.shade(mid, 0.8))
        for i in range(3):
            c.set(hx + 1 + i, ey - 1 - i, light)
    if "sucker" in feats:
        c.set(hx + 2, int(cy) + 1, g.OIL)
        c.set(hx + 2, int(cy), g.shade(belly, 0.7))
    if "angler" in feats:
        c.line(hx, int(cy - b) - 1, hx + 3, int(cy - b) - 3, g.GUN_L, wrap=False)
        c.set(hx + 4, int(cy - b) - 3, accent)
    if "bill" in feats:
        for i in range(1, 5):
            if hx + 2 + i < 16:
                c.set(hx + 2 + i, int(cy) - 1, g.STEEL_L)
    if "whiskers" in feats:
        c.set(hx + 3, int(cy) + 1, g.GUN_L); c.set(hx + 4, int(cy) + 2, g.GUN_L)
        c.set(hx + 2, int(cy) + 2, g.GUN_L)
    if "crown" in feats:
        top = int(cy - b) - 1
        for i, x in enumerate(range(int(cx) - 2, int(cx) + 3)):
            c.set(x, top, g.BRASS_L if area != "foundry" else g.MAGMA_L)
            if i % 2 == 0:
                c.set(x, top - 1, g.BRASS if area != "foundry" else g.MAGMA)
        c.set(int(cx), top - 1, g.RED_L)
    # Eye(s)
    if "eyes" in feats:
        for ex, eyy in ((hx, ey), (hx - 2, ey + 2), (hx - 3, ey - 1)):
            c.set(ex, eyy, g.HAZ); c.set(ex, eyy + 1, g.OIL)
    else:
        c.set(hx, ey, g.WHITE if not ghost else (255, 255, 200))
        c.set(hx + 1, ey, g.OIL)
    if "teeth" in feats:
        c.set(hx + 2, int(cy) + 1, g.WHITE)
        c.set(hx + 1, int(cy) + 1, g.OIL)
    if "shine" in feats:
        c.set(int(cx) + 1, int(cy - b) + 1, g.WHITE)
    c.outline(g.OIL)
    glow = RARITY_GLOW.get(rarity)
    if glow:
        # A faint coloured rim outside the outline marks rare and better catches.
        pts = []
        for y in range(16):
            for x in range(16):
                if c.px[x, y][3] == 0:
                    for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + ddx, y + ddy
                        if 0 <= nx < 16 and 0 <= ny < 16 and c.px[nx, ny][:3] == g.OIL and c.px[nx, ny][3] == 255:
                            pts.append((x, y))
                            break
        for x, y in pts:
            c.set(x, y, glow, 110 if rarity != "legendary" else 170)
    return c


def junk_art(g, out):
    c = g.Canvas(seed=900)  # corroded bolt
    c.rect(5, 3, 10, 6, g.GUN_L); c.rect(5, 3, 10, 3, g.STEEL)
    for y in range(7, 13):
        c.rect(7, y, 8, y, g.RUST if y % 2 else g.RUST_L)
    c.speckle(g.VERDI, 3)
    c.outline(g.OIL); c.save(out / "corroded_bolt.png")

    c = g.Canvas(seed=901)  # tangled cable
    for a in range(0, 720, 15):
        r = 2 + (a % 360) / 120
        x = round(8 + r * math.cos(math.radians(a)))
        y = round(8 + r * math.sin(math.radians(a)) * 0.8)
        c.set(x, y, g.RED if a < 360 else g.LAPIS_L)
    c.set(2, 13, g.COPPER_L); c.set(3, 12, g.RED)
    c.outline(g.OIL); c.save(out / "tangled_cable.png")

    c = g.Canvas(seed=902)  # soggy ration pack
    c.rect(3, 4, 12, 12, g.OLIVE); c.rect(3, 4, 12, 5, g.OLIVE_L)
    c.rect(5, 7, 10, 9, g.HAZ_D)
    for _ in range(5):
        c.set(c.rng.randint(3, 12), c.rng.randint(6, 12), (60, 80, 100))
    c.outline(g.OIL); c.save(out / "soggy_ration.png")

    c = g.Canvas(seed=903)  # cracked visor
    for y in range(4, 12):
        for x in range(2, 14):
            if ((x - 7.5) / 6) ** 2 + ((y - 7.5) / 4) ** 2 <= 1:
                c.set(x, y, g.mix(g.CYAN_D, g.OIL, 0.4), 220)
    c.line(4, 5, 9, 10, g.WHITE, wrap=False); c.line(9, 10, 12, 7, g.FROST_L, wrap=False)
    c.outline(g.GUN_D); c.save(out / "cracked_visor.png")

    c = g.Canvas(seed=904)  # spent power cell
    c.rect(5, 3, 10, 13, g.GUN); c.rect(6, 2, 9, 2, g.STEEL)
    c.rect(6, 10, 9, 12, g.RED_D); c.rect(6, 12, 9, 12, g.RED)
    c.outline(g.OIL); c.save(out / "spent_cell.png")
    for n in ("corroded_bolt", "tangled_cable", "soggy_ration", "cracked_visor", "spent_cell"):
        g.generated_model(f"fishing/junk/{n}")


def lure_art(g, out):
    def base(seed):
        return g.Canvas(seed=seed)

    def line_to(c, x, y):
        c.line(8, 0, x, y, (200, 200, 200), wrap=False)

    c = base(910)  # rattle spinner
    line_to(c, 8, 4)
    for y in range(5, 11):
        w = 3 - abs(y - 7)
        for x in range(8 - max(1, w), 8 + max(1, w) + 1):
            c.set(x, y, g.STEEL_L if x < 8 else g.STEEL)
    c.set(8, 12, g.RED); c.set(9, 13, g.GUN_L)
    c.outline(g.OIL); c.save(out / "spinner.png")

    c = base(911)  # deep hook: heavy weighted hook
    line_to(c, 8, 3)
    c.blob(8, 5, 1, [g.GUN_D, g.GUN], wrap=False)
    for y in range(6, 12):
        c.set(8, y, g.STEEL_L)
    c.line(8, 12, 11, 11, g.STEEL_L, wrap=False); c.set(11, 10, g.WHITE)
    c.outline(g.OIL); c.save(out / "deep_hook.png")

    c = base(912)  # siren lure: speaker cone
    line_to(c, 8, 3)
    c.rect(6, 4, 10, 10, g.GUN); c.blob(8, 7, 1, [g.RED_L, g.RED], wrap=False)
    for i in range(3):
        c.set(12 + i % 2, 5 + i * 2, g.VIOLET_L, 180)
    c.outline(g.OIL); c.save(out / "siren_lure.png")

    c = base(913)  # salvage hook: magnet
    line_to(c, 8, 3)
    for y in range(4, 11):
        c.set(6, y, g.RED); c.set(10, y, g.STEEL_L)
    for x in range(6, 11):
        c.set(x, 4, g.GUN_D)
    c.set(6, 11, g.WHITE); c.set(10, 11, g.WHITE)
    c.outline(g.OIL); c.save(out / "salvage_hook.png")

    c = base(914)  # twin hook
    line_to(c, 8, 3)
    for y in range(4, 10):
        c.set(8, y, g.STEEL_L)
    for s in (-1, 1):
        c.line(8, 10, 8 + 3 * s, 12, g.STEEL, wrap=False)
        c.set(8 + 3 * s, 11, g.WHITE)
    c.outline(g.OIL); c.save(out / "twin_hook.png")

    c = base(915)  # gyro bobber
    line_to(c, 8, 3)
    c.blob(8, 8, 3, [g.HAZ, g.HAZ_D, g.GUN_D], wrap=False)
    for x in range(4, 13):
        c.set(x, 8, g.STEEL)
    c.set(8, 8, g.CYAN)
    c.outline(g.OIL); c.save(out / "gyro_bobber.png")

    c = base(916)  # kevlar leader: braided line coil
    for y in range(2, 14):
        c.set(7 + (y % 2), y, g.HAZ if y % 3 else g.HAZ_D)
        c.set(8 - (y % 2), y, g.OLIVE_L)
    c.set(8, 14, g.STEEL_L)
    c.outline(g.OIL); c.save(out / "kevlar_leader.png")

    def rivet(f):
        c = base(917)
        line_to(c, 8, 3)
        c.blob(8, 8, 3, [g.BRASS_L, g.BRASS, g.BRASS_D], wrap=False)
        c.set(7, 7, g.WHITE if f == 0 else g.BRASS_L)
        c.set(9 + f % 2, 6, g.WHITE)
        c.outline(g.BRASS_D)
        return c
    g.stack([rivet(0), rivet(1), rivet(0), rivet(0)]).save(out / "golden_rivet.png")
    g.animate(out / "golden_rivet.png", 8)
    for n in ("spinner", "deep_hook", "siren_lure", "salvage_hook", "twin_hook", "gyro_bobber", "kevlar_leader", "golden_rivet"):
        g.generated_model(f"fishing/lure/{n}")


def bait_art(g, out):
    def jar(seed, fill, top, specks):
        c = g.Canvas(seed=seed)
        c.rect(4, 4, 11, 13, (200, 220, 220)); c.rect(5, 6, 10, 12, fill)
        c.rect(4, 2, 11, 3, top)
        for col in specks:
            c.speckle(col, 4)
        for y in range(4, 14):
            c.set(4, y, (230, 240, 240))
        c.rect(4, 2, 11, 3, top)
        c.outline(g.OIL)
        return c
    jar(920, (150, 110, 70), g.RUST, [(200, 160, 110)]).save(out / "grub_paste.png")
    jar(921, (40, 60, 40), g.GUN, [g.GREEN, (210, 255, 190)]).save(out / "glow_worms.png")
    jar(922, (120, 30, 30), g.HAZ_D, [g.RED_L, (240, 220, 200)]).save(out / "chum_canister.png")
    c = g.Canvas(seed=923)  # magnet bits: pile of tiny magnets
    for x, y in ((5, 10), (8, 8), (10, 11), (7, 12), (11, 8)):
        c.set(x, y, g.RED); c.set(x + 1, y, g.STEEL_L); c.set(x, y + 1, g.GUN_D)
    c.outline(g.OIL); c.save(out / "magnet_bits.png")
    jar(924, g.CYAN_D, g.STEEL, [g.CYAN, g.CYAN_L]).save(out / "coolant_gel.png")
    for n in ("grub_paste", "glow_worms", "chum_canister", "magnet_bits", "coolant_gel"):
        g.generated_model(f"fishing/bait/{n}")


RODS = {
    # tier: (pole colors (dark, mid, light), reel color, accent)
    "bent_wire": (((80, 70, 60), (130, 120, 104), (170, 160, 140)), (122, 58, 28), (200, 128, 64)),
    "scrap_spinner": (((74, 36, 20), (122, 58, 28), (168, 92, 44)), (66, 67, 72), (214, 166, 40)),
    "carbon_line": (((20, 22, 24), (44, 44, 48), (66, 67, 72)), (40, 110, 40), (90, 200, 80)),
    "mag_rail": (((70, 94, 120), (128, 162, 190), (196, 222, 236)), (20, 110, 130), (60, 210, 220)),
    "void_angler": (((12, 8, 22), (34, 20, 56), (120, 70, 190)), (34, 20, 56), (200, 150, 255)),
}


def rod_sprite(g, tier, cast):
    pole, reel, accent = RODS[tier]
    c = g.Canvas(seed=930 + len(tier))
    # Diagonal pole from the grip (bottom-left) to the tip (top-right), like the vanilla rod.
    for i in range(13):
        x, y = 2 + i, 13 - i
        c.set(x, y, pole[1])
        c.set(x, y + 1, pole[0])
        if i % 3 == 0:
            c.set(x - 1, y, pole[2])
    c.rect(1, 13, 3, 14, (40, 30, 26))  # grip
    c.set(2, 12, pole[2])
    c.blob(5, 11, 1, [reel, g.shade(reel, 0.7)], wrap=False)  # reel
    c.set(5, 11, accent)
    if tier in ("mag_rail", "void_angler"):
        for i in (4, 7, 10):
            c.set(2 + i, 13 - i - 1, accent)
    if not cast:
        for y in range(2, 9):
            c.set(14, y, (220, 220, 220), 200)
        c.set(14, 9, accent); c.set(14, 10, g.WHITE)
    c.outline(g.OIL)
    return c


def rods(g):
    out = g.SP / "textures" / "item" / "rod"
    for tier in RODS:
        for cast in (False, True):
            name = f"{tier}_cast" if cast else tier
            rod_sprite(g, tier, cast).save(out / f"{name}.png")
            g.write_json(g.SP / "models" / "item" / "rod" / f"{name}.json", {
                "parent": "minecraft:item/handheld_rod", "textures": {"layer0": f"spacepunk:item/rod/{name}"}})
        g.write_json(g.SP / "items" / "rod" / f"{tier}.json", {"model": {
            "type": "minecraft:condition", "property": "minecraft:fishing_rod/cast",
            "on_false": {"type": "minecraft:model", "model": f"spacepunk:item/rod/{tier}"},
            "on_true": {"type": "minecraft:model", "model": f"spacepunk:item/rod/{tier}_cast"},
        }})


ENCHANT_GLYPHS = {
    "lure_speed": (["..#..", ".###.", "#.#.#", "..#..", "..#.."], (95, 180, 200)),
    "double_catch": (["#...#", "##.##", "#####", "##.##", "#...#"], (111, 211, 90)),
    "angler_eye": ([".###.", "#...#", "#.#.#", "#...#", ".###."], (79, 168, 255)),
    "treasure_luck": (["#####", "#.#.#", "#####", "#...#", "#####"], (255, 210, 63)),
    "chum_sense": (["#.#.#", ".###.", "#####", ".###.", "#.#.#"], (255, 90, 60)),
    "steady_hands": (["#...#", "#...#", "#####", "#...#", "#...#"], (216, 208, 192)),
    "line_strength": (["#.#.#", ".#.#.", "#.#.#", ".#.#.", "#.#.#"], (156, 138, 110)),
    "harpoon": (["....#", "...##", "..#..", ".#...", "#...."], (196, 108, 255)),
    "fillet": (["###..", "#..#.", "####.", "#..#.", "###.."], (255, 180, 58)),
}


def icons(g):
    out = g.SP / "textures" / "item"
    for eid, (rows, col) in ENCHANT_GLYPHS.items():
        c = g.panel(940 + len(eid))
        big = [r.replace("#", "##").replace(".", "..") for r in rows for _ in (0, 1)]
        g.glyph(c, big, col, 3, 3)
        c.save(out / "ui" / "rod_enchant" / f"{eid}.png")
        g.generated_model(f"ui/rod_enchant/{eid}")

    def scale(f):
        c = g.Canvas(seed=960)
        for y in range(3, 14):
            w = min(y - 2, 14 - y, 5)
            for x in range(8 - w, 8 + w + 1):
                c.set(x, y, g.mix((156, 242, 122), g.CYAN, (x + y + f) % 6 / 6))
        for y in range(5, 13, 3):
            for x in range(5, 12):
                if c.px[x, y][3]:
                    c.set(x, y, (60, 140, 90))
        c.set(6 + f % 3, 5, g.WHITE)
        c.outline(g.OIL)
        return c
    g.stack([scale(f) for f in range(3)]).save(out / "fishing" / "scale.png")
    g.animate(out / "fishing" / "scale.png", 10, interpolate=True)
    g.generated_model("fishing/scale")

    c = g.Canvas(seed=961)  # cooler box
    c.rect(2, 5, 13, 13, (210, 220, 225)); c.rect(2, 5, 13, 6, (90, 160, 210))
    c.rect(6, 3, 9, 4, g.GUN); c.rect(4, 9, 11, 9, g.FROST)
    c.outline(g.OIL); c.save(out / "fishing" / "cooler.png")
    g.generated_model("fishing/cooler")

    c = g.Canvas(seed=962)  # collection: logbook with a fish
    c.rect(3, 2, 12, 13, (40, 70, 110)); c.rect(4, 3, 12, 12, (60, 100, 150))
    for y in range(2, 14):
        c.set(3, y, (20, 40, 70))
    c.rect(6, 7, 10, 8, g.FROST_L); c.set(11, 7, g.FROST_L); c.set(5, 6, g.FROST_L); c.set(5, 9, g.FROST_L)
    c.set(9, 7, g.OIL)
    c.outline(g.OIL); c.save(out / "fishing" / "collection.png")
    g.generated_model("fishing/collection")

    c = g.Canvas(seed=963)  # leaderboard: trophy cup
    c.rect(4, 3, 11, 8, g.BRASS); c.rect(4, 3, 11, 3, g.BRASS_L)
    c.set(3, 4, g.BRASS_D); c.set(12, 4, g.BRASS_D); c.set(3, 5, g.BRASS_D); c.set(12, 5, g.BRASS_D)
    c.rect(7, 9, 8, 10, g.BRASS_D); c.rect(5, 11, 10, 12, g.GUN)
    c.set(6, 4, g.WHITE)
    c.outline(g.OIL); c.save(out / "fishing" / "leaderboard.png")
    g.generated_model("fishing/leaderboard")

    def porthole(f):
        c = g.Canvas(seed=964)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d < 7:
                    c.set(x, y, g.STEEL if d > 5.2 else g.mix(g.CYAN_D, g.LAPIS, (y + f) % 4 / 4))
        for a in range(0, 360, 60):
            c.set(round(7.5 + 6.1 * math.cos(math.radians(a))), round(7.5 + 6.1 * math.sin(math.radians(a))), g.GUN_D)
        c.set(5, 5, g.WHITE)
        c.outline(g.OIL)
        return c
    g.stack([porthole(f) for f in range(4)]).save(out / "fishing" / "waters.png")
    g.animate(out / "fishing" / "waters.png", 6, interpolate=True)
    g.generated_model("fishing/waters")


def fishing(g):
    repo = Path(__file__).resolve().parents[2]
    out = g.SP / "textures" / "item"
    for i, (fid, area, rarity) in enumerate(scan_fish(repo)):
        fish_sprite(g, fid, area, rarity, 1000 + i * 7).save(out / "fish" / f"{fid}.png")
        g.generated_model(f"fish/{fid}")
    junk_art(g, out / "fishing" / "junk")
    lure_art(g, out / "fishing" / "lure")
    bait_art(g, out / "fishing" / "bait")
    rods(g)
    icons(g)
