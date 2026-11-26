# The drill

Your drill is your pickaxe, and the system you'll spend the most time building. Every player gets one **soulbound**
drill on first join. It can't be dropped, stored, put in a frame or lost on death, and you keep it through prestige.

## Controls

| Action | How |
| --- | --- |
| Open the drill bay | **Shift + right-click** the drill, or `/drill` (`/pickaxe`, `/pick`) |
| Vent heat | Press **Q** (drop) with the drill in hand |
| Buy enchant levels | In the drill bay: left-click +1, right-click +10, shift-click as many as you can afford |

The Exchange (east wing) also has a **Drill** terminal.

## Four ways it grows

### 1. Frames: the ceiling

The frame is the drill's body. It sets the **level cap of every enchant**, the base mining speed, extra heat
capacity and how many **module sockets** you have. Once bought, a frame is kept forever.

| Frame | Unlocks at | Cost | Sockets | Base speed | Extra heat capacity |
| --- | --- | --- | --- | --- | --- |
| Rusted | Start | Free | 0 | 0.5× | +0 |
| Salvaged | Grade 10 | <span class="cur credits">175,000 Credits</span> | 1 | 0.65× | +25 |
| Industrial | Prestige 1 | <span class="cur shards">5 Shards</span> | 2 | 0.85× | +60 |
| Military | Prestige 5 | <span class="cur shards">30 Shards</span> | 3 | 1.1× | +110 |
| Prototype | Prestige 15 | <span class="cur shards">120 Shards</span> | 4 | 1.5× | +180 |

Base speed is a break-speed multiplier where 1.0 is a plain netherite pickaxe. Bore Speed adds on top.

### 2. Enchants: bought with Tokens

Cost of level N = **base × growth^(N−1)** <span class="cur tokens">Tokens</span>. The table shows the level cap on each
frame (Rusted / Salvaged / Industrial / Military / Prototype).

| Enchant | What it does | Level caps | Level 1 cost | Growth |
| --- | --- | --- | --- | --- |
| Bore Speed | +0.08 break speed per level | 15 / 30 / 50 / 75 / 100 | 15 | ×1.10 |
| Yield | +2.5% ore from every block per level (area enchants too) | 10 / 25 / 50 / 80 / 120 | 20 | ×1.10 |
| Token Siphon | +0.015% Token find chance per level (level 10 is about +25% Tokens), capped at +250% | 10 / 25 / 50 / 80 / 120 | 25 | ×1.10 |
| Shaped Charge | +0.08% chance per level (max 8%) to blow out a sphere, radius 2 growing +1 every 12 levels to 6 | 5 / 15 / 30 / 50 / 75 | 60 | ×1.11 |
| Jackhammer | +0.03% chance per level (max 2%) to clear a whole layer of the mine, up to 4,000 blocks | locked / locked / 10 / 25 / 40 | 4,000 | ×1.13 |
| Cutting Laser | +0.08% chance per level (max 6%) to fire a laser through the blocks ahead, 6 blocks +0.4 per level, up to 40 | locked / 10 / 25 / 40 / 60 | 1,200 | ×1.12 |
| Overclock | +0.04% chance per level (max 3%) of a +75% speed burst for 3 s (+0.05 s per level); heat ×1.5 while it lasts | 5 / 15 / 30 / 50 / 75 | 80 | ×1.11 |
| Salvage Sense | +4% [rare drop](rare-drops.md) and module odds per level, plus a small chance of Token caches (scaled like mining Tokens) | locked / 10 / 25 / 40 / 60 | 1,500 | ×1.12 |
| Coolant System | +4 heat capacity and +0.15 cooling per second per level | 5 / 15 / 30 / 50 / 75 | 30 | ×1.11 |

**Tokens to max each enchant on each frame** (from level 0):

| Enchant | Rusted | Salvaged | Industrial | Military | Prototype |
| --- | --- | --- | --- | --- | --- |
| Bore Speed | 476 | 2.5K | 17.5K | 191K | 2.07M |
| Yield | 318 | 2K | 23.3K | 409K | 18.5M |
| Token Siphon | 399 | 2.5K | 29.1K | 512K | 23.2M |
| Shaped Charge | 374 | 2.1K | 11.9K | 100K | 1.37M |
| Jackhammer | locked | locked | 73.7K | 622K | 4.05M |
| Cutting Laser | locked | 21.1K | 160K | 921K | 8.97M |
| Overclock | 498 | 2.8K | 15.9K | 134K | 1.82M |
| Salvage Sense | locked | 26.3K | 200K | 1.15M | 11.2M |
| Coolant System | 187 | 1K | 6K | 50K | 684K |

For scale: mining pays about 100 Tokens an hour at grade 1 and about 975 at grade 25, and every prestige makes Token
finds 15% bigger. A first run mines about 40,000 Tokens, enough to max the Rusted frame early and most of Salvaged.
See [Currencies](../currencies.md#tokens).

### 3. Modules: found, not bought

Modules are rare items you slot into frame sockets in the drill bay. Each one changes how the drill plays, with an
upside and a downside. Unslotted modules can be traded on the [Black Market](black-market.md).

| Module | Rarity | Upside | Downside |
| --- | --- | --- | --- |
| Cryo Coolant Loop | Common | Heat capacity +60% | Overclock can't fire |
| Magnet Coil | Rare | Auto-sells everything you mine, at full price | No Token drops |
| Scavenger Rig | Rare | Rare drop and module odds ×2 | Sell price −15% |
| Plasma Cutter | Epic | Shaped Charge radius ×2 | Shaped Charge heat ×2 |
| Token Condenser | Epic | Tokens ×1.5 | Heat from hand mining ×2.5 |
| Seismic Dampener | Epic | Jackhammer fires twice as often | Cutting Laser can't fire |
| Overcharger | Legendary | Overclock and vent bursts last ×2 | Overheat stalls last ×2 |

**Where modules come from**

- **Mining salvage:** every hand-broken block has a tiny chance (0.002%, raised by Salvage Sense and the
  Scavenger Rig) to salvage a random module. Finds are announced to the server.
- **Blueprint fragments** from [rare drops](rare-drops.md): 5 Common, Rare or Epic fragments, or 4 Legendary ones,
  assemble a random module of that rarity in `/salvage`.
- **The workshop Assembly Bench:** build a chosen module from components and Credits (see [Workshop](workshop.md)).
- **Daily parkour** rare rolls, **fishing** treasure (Sealed Module Cases), **Undercroft** Smuggled Tech Crates,
  and the **Contract Track** (Coolant Loop at 600 points, Magnet Coil at 1,200).

### 4. Heat: rhythm and skill

- Breaking blocks by hand adds 0.35 heat each, and Shaped Charge, Jackhammer, Cutting Laser and Overclock add heat
  when they fire.
- Heat gain depends on the deck: ×0.35 on the Scrapyard, ×0.6 Reactor Ducts, ×0.85 Cryo Vaults, ×1.0 Hull Breach,
  ×1.2 Core Foundry.
- Capacity is **100**, plus the frame's bonus, +4 per Coolant System level and +60% with a Coolant Loop.
- **Overheat:** at full heat the drill **stalls for 3 seconds**, then drops to 40% heat.
- **Cooling:** 12 heat per second once you stop mining for 1.5 seconds (0.6 per second while you keep mining), plus
  Coolant System bonuses.
- **Venting:** press Q above 30% heat to dump all heat at once and get a speed burst. The burst is worth up to
  **+40% break speed** (scaled by how hot you were) and lasts 2 seconds plus up to 4 more the hotter you were.
  Vent between **75% and 95%** for a **perfect vent**: the bonus is doubled (up to about +76%). Vents have a
  6-second cooldown, and you can't vent while stalled.

## How it ties into progression

- A faster drill with Yield and area enchants is what makes later grades reachable in reasonable time.
- Frames tie the drill to [prestige](prestige.md): high enchant caps need Shards.
- Salvage Sense feeds [rare drops](rare-drops.md); modules feed the [Black Market](black-market.md).
- [Stims](undercroft.md) like Overdrive add mining speed on top.
