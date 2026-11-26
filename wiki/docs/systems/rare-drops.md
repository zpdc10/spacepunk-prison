# Rare drops (salvage)

Every block you break in a mine has a small chance to dig something up: a **salvage cache** full of loot, a
collectible **relic**, or a **module blueprint** fragment. Every activity on the station can also turn up
**scrap parts**, which build and upgrade drill modules.

## Commands

| Command | What it does |
| --- | --- |
| `/salvage` | Open the salvage menu: relic collections and decoding blueprint fragments into schematics |

The Exchange has a **Salvage** terminal.

## How finds work

- Each **hand-broken** block has a **0.2%** base chance. Blocks broken by area enchants (Shaped Charge, Jackhammer,
  Cutting Laser) roll at 15% of that, and one batch gives at most 3 finds.
- **Salvage Sense** adds +4% odds per level, and the **Scavenger Rig** module doubles them.
- A find picks a tier, then an item of that tier that can drop on your deck.
- Finds go straight to your inventory. If it's full, they drop at your feet.

| Tier | Share of finds | Roughly how often (base odds, ~3,000 hand-broken blocks an hour) | Announced |
| --- | --- | --- | --- |
| Common | 55% | Several an hour | No |
| Uncommon | 28% | A couple an hour | No |
| Rare | 12% | About every 1.5 hours | Title for you |
| Epic | 4% | About every 4 hours | Server-wide |
| Legendary | 0.9% | About every 18 hours | Server-wide, everyone hears it |
| Mythic | 0.1% | About every 170 hours | Server-wide, everyone hears it |

## Salvage caches

Right-click a cache to open it. Credits scale with **the grade and prestige of whoever opens it** (×1.26 per grade,
+50% per prestige), so a cache is worth about the same few minutes of mining at any point, and saving them for later
gains nothing. Cache Credits count as server income.

| Cache | Tier | Credits at grade 1 | Tokens |
| --- | --- | --- | --- |
| Scrap Cache | Common | 250 to 500 | none |
| Sealed Supply Crate | Uncommon | 1,000 to 1,800 | 5 to 15 |
| Armored Strongbox | Rare | 3,500 to 5,500 | 25 to 60 |
| Warden's Vault Core | Epic | 15,000 to 22,000 | 150 to 300 |

## Module schematics

Blueprint fragments drop on every deck. Collect enough of one rarity and **decode** them in `/salvage` to learn a
random [module](drill.md#3-modules-and-the-module-rig) **schematic** (drill, rod, weapon or exo) of that rarity you don't know yet. A
schematic is a permanent recipe: build the module as often as you like at your workshop's
[Assembly Bench](workshop.md#the-assembly-bench).

Once you know every schematic of a rarity, its fragments assemble a random module of that rarity instead.

| Fragment | Drops as | Needed |
| --- | --- | --- |
| Common Module Blueprint | Uncommon | 5 |
| Rare Module Blueprint | Rare | 5 |
| Epic Module Blueprint | Epic | 5 |
| Legendary Module Blueprint | Legendary | 4 |

Everyone starts knowing the Servo Actuator, Ore Sifter, Reel Servo, Extended Mag and Servo Greaves schematics.
Fragments can decode into drill, [rod](fishing.md#the-rod-rig), [weapon](guns.md#weapon-rigs) or [exo](exo-rig.md)
module schematics.

## Scrap parts

Salvaged electronics and machine parts. They come from **every activity**, not just mining, and they're what the
Assembly Bench needs to build modules and what fusing needs for Mk III to Mk V.

**Condition.** Every part is found in one of three states: **Fried** (80%), **Working** (14%) or **Pristine** (6%).
Fried parts must be repaired at the workshop [Refurbisher](workshop.md#the-refurbisher) before use. Pristine parts
count as working and are needed to fuse modules to Mk IV and Mk V.

| Part | Tier | Found in |
| --- | --- | --- |
| Copper Wiring | Common | Every activity |
| Circuit Board | Common | Every activity |
| Capacitor | Uncommon | Every activity |
| Servo Motor | Uncommon | Every activity |
| Power Cell | Uncommon | Every activity |
| Quantum Core | Legendary | Every activity, very rarely |
| Seismic Sensor | Rare | Mining only |
| Sonar Array | Rare | Fishing and sea creatures only |
| Gyro Stabilizer | Rare | Daily parkour only |
| Logic Chip | Rare | Workshop only |
| Military Relay | Epic | Undercroft kills and smuggling only |
| Encrypted Data Core | Epic | Contracts and story quests only |

**How often**

| Activity | Chance |
| --- | --- |
| Mining | 0.1% per hand-broken block (about 3 to 4 an hour) |
| Fishing | 5% per catch |
| Sea creature kill | 30% |
| Daily parkour reward | Always: 2 parts per rewarded run |
| Workshop | 5% each minute your bay is processing while you're online |
| Undercroft kill | 25% per player downed |
| Smuggling | 60% per run that gets contraband out |
| Daily or weekly contract | 60% |
| Story quest step | Always |

The Scavenger Rig, the Quantum Sorter and the Salvage family bonus raise every source. Parts go to your inventory, or
drop at your feet if it's full. They're kept through prestige and can be traded.

## Relic collections

Relics are collectibles. Each deck has four, and they only drop on that deck. **Catalog** one of every relic in a set
(in `/salvage`) for a **permanent sell bonus** and a **Token** payout.

| Set | Relics (uncommon, rare, epic, legendary) | Sell bonus | Tokens |
| --- | --- | --- | --- |
| Scrapyard Relics | Inmate Tag #0451, Rusted Warden Badge, Cracked Holo-Locket, Founder's Rivet | +2% | 1,500 |
| Reactor Duct Relics | Melted Valve Wheel, Coolant Engineer's Wrench, Reactor Log Chip, Glowing Fuel Pellet | +3% | 3,000 |
| Cryo Vault Relics | Frozen Dog Tag, Cryo-Pod Nameplate, Sleeper's Music Box, Perfect Ice Core | +4% | 6,000 |
| Hull Breach Relics | Void-Bleached Bolt, Spacesuit Visor Shard, Black Box Recorder, Star Chart Fragment | +5% | 12,000 |
| Core Foundry Relics | Slagged Gold Tooth, Foundry Crown Gear, Warden's Ledger Page, Neutronium Heart | +6% | 25,000 |
| Anomalies | The Warden's Master Key (mythic, any deck) | +10% | 100,000 |

All six sets together add +30% to your sell price, forever. Spare relics are worth trading on the
[Black Market](black-market.md).

## How it ties into progression

Caches top up Credits and Tokens, blueprints and scrap parts are the path to drill modules, and relic sets are one of the
biggest permanent sell bonuses in the game. Finding a rare drop also counts for [tutorials and contracts](quests.md).
