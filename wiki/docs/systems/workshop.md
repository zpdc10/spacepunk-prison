# Workshop (hull bays)

Every player gets a private **hull bay**: a salvaged section of hull where you place machines that turn mined ore into
goods worth more than raw ore. The choice is always between selling ore now for quick Credits, or processing it for a
bigger payout. You can also decorate your bay freely.

## Commands

| Command | What it does |
| --- | --- |
| `/workshop` (`/ws`, `/bay`, `/hullbay`) | Go to your bay (claims and builds it the first time) |
| `/ws menu` | Open the workshop menu (or right-click the lodestone terminal in the north wall) |
| `/ws open` / `/ws close` | Let visitors in, or make it private again |
| `/ws crew add <player>` / `remove` | Let someone build and use machines in your bay |

The station's Shuttle Bay has a **Workshop** airlock (warp `workshop`).

## The processing loop

Ore flows from machine to machine. Each step multiplies the value.

1. **Cargo Intake** pulls ore from your cargo hold (or your Bay Locker) and pushes it out the front.
2. **Jaw Crusher** turns ore into grit (×1.15). 30% chance of Slag Dust, which goes to your locker.
3. **Sludge Washer** turns grit into concentrate (×1.17). Needs coolant. 15% chance of Toxic Sludge.
4. **Arc Furnace** smelts concentrate into refined goods: ingots, lenses, wafers (×1.33). Runs very hot.
5. **Alloy Forge** fuses two refined goods into an alloy (×1.25). Needs coolant.
6. **Fabricator** builds components (×1.3).
7. **Depot Hatch** stores whatever reaches it in your **Bay Locker**, or a **Trade Chute** sells it automatically at
   90% of today's market price.

Machines push **out of their front**, the way you faced when you placed them (rotate them in the machine's menu).
The block in front can be another machine, or a **conveyor** line (restyled repeaters, up to 48 blocks) that ends at a
machine.

## Machines

The first of each kind costs the price below; every extra one of the same kind costs ×1.35 more.

| Machine | Job | Cost | Grade | Power/s | Heat/s |
| --- | --- | --- | --- | --- | --- |
| Cargo Intake | Pulls 2 ore/s from cargo or locker | 1,500 | 1 | 1 | 0 |
| Jaw Crusher | Ore to grit, 1 craft/s | 3,000 | 1 | 3 | 2 |
| Slag Burner | Makes 12 power/s from fuel | 2,500 | 1 | | 3 |
| Depot Hatch | Into the Bay Locker | 1,000 | 1 | | |
| Heat Sink | Sheds 4 heat/s from every touching machine | 2,000 | 2 | | |
| Sludge Washer | Grit to concentrate, uses 4 coolant/s | 12,000 | 4 | 3 | 1 |
| Coolant Pump | Makes 10 coolant/s for the whole bay | 8,000 | 4 | 2 | 0.5 |
| Arc Furnace | Concentrate to refined goods | 40,000 | 6 | 8 | 6 |
| Trade Chute | Sells what it receives at 90% | 25,000 | 6 | 1 | |
| Alloy Forge | Two refined goods to an alloy, uses 3 coolant/s | 400,000 | 11 | 10 | 5 |
| Fabricator | Alloys and parts to components | 3,000,000 | 16 | 12 | 4 |

Conveyors come in bundles of 16 for 800 Credits at the Machine Shop. The Decor Locker (second page) sells building
blocks for decorating.

## Power, coolant and heat

- **Power:** Slag Burners burn fuel from their input or straight from your locker, only as much as the bay needs.
  Fuel burn times: Slag Dust 4 s, stone or cobblestone 2 s, Carbon Slag 20 s, Crushed Carbon 30 s, Fuel Cell 240 s.
  **Short on power, every machine runs slower** (brownout).
- **Coolant:** pumps share coolant with every washer and forge in the bay. Short on coolant, they slow down.
- **Heat:** running machines heat up, **+35% for every running machine touching them**. Every machine sheds 3 heat
  per second, and a touching Heat Sink sheds 4 more. At **100 heat a machine jams** until it cools to 25. An Arc
  Furnace (6/s) overheats on its own without a sink.

Your bay's power, coolant and jams show on the action bar while you're inside.

## Marks and quality

Upgrade any machine in its menu:

| Mark | Speed | Power | Heat | Refined chance | Pristine chance | Cost |
| --- | --- | --- | --- | --- | --- | --- |
| Mk I | ×1.0 | ×1.0 | ×1.0 | | | (what you bought) |
| Mk II | ×1.6 | ×1.2 | ×0.9 | 15% | | 6× base cost |
| Mk III | ×2.5 | ×1.4 | ×0.8 | 30% | 6% | 30× base cost + 4 Wiring Harness + 2 Armor Plate |

Smelt, alloy and fabricate steps roll **quality**: **Refined** goods sell for ×1.35 and **Pristine** for ×1.9. Better
inputs raise the roll.

## What you can make

**Ore families** (crush, wash, smelt): Slag (slag dust only), Carbon (Graphite Rod), Copper (Copper Ingot), Steel
(Steel Ingot), Conduit (Conduit Filament), Brass (Brass Ingot), Cobalt (Cobalt Ingot), Cryo (Cryo Lens), Quartz
(Silicon Wafer), Verdant (Verdant Crystal), Void (Void Essence) and Neutronium (Neutronium Ingot). Each ore crushes
into enough units that crushing never loses value.

**Alloy Forge recipes**

| Alloy | Inputs |
| --- | --- |
| Conductive Alloy | 2 Copper Ingot + 1 Steel Ingot |
| Hardened Steel | 2 Steel Ingot + 1 Graphite Rod |
| Circuit Alloy | 1 Brass Ingot + 2 Conduit Filament |
| Cobalt Steel | 1 Cobalt Ingot + 2 Steel Ingot |
| Cryo Alloy | 1 Cryo Lens + 1 Cobalt Ingot |
| Verdant Matrix | 1 Verdant Crystal + 1 Silicon Wafer |
| Void Lattice | 2 Void Essence + 1 Silicon Wafer |
| Neutronium Composite | 1 Neutronium Ingot + 1 Hardened Steel |

**Fabricator recipes**

| Component | Inputs |
| --- | --- |
| Wiring Harness (×2) | 1 Conductive Alloy |
| Armor Plate | 2 Hardened Steel |
| Fuel Cell | 2 Graphite Rod + 1 Toxic Sludge |
| Circuit Board | 1 Circuit Alloy + 1 Silicon Wafer |
| Induction Coil | 1 Cobalt Steel + 1 Wiring Harness |
| Firing Mechanism | 1 Circuit Board + 2 Wiring Harness |
| Weapon Receiver | 1 Armor Plate + 1 Induction Coil |
| Cryo Coil | 1 Cryo Alloy + 1 Induction Coil |
| Focusing Array | 1 Verdant Matrix + 1 Circuit Board |
| Void Capacitor | 1 Void Lattice + 1 Circuit Board |
| Reactor Core | 1 Neutronium Composite + 1 Void Capacitor + 1 Cryo Coil |

**Example:** Copper Scrap sells for 6 as ore. As a Copper Ingot it's worth about 10.7. A full copper and steel to
Wiring Harness chain is worth about **2.9× the raw ore**.

## The market

The **Trade Terminal** market is shared by the whole server and changes every day (midnight UTC):

- **Daily demand:** each good's price is multiplied by ×0.7 to ×1.4, the same for everyone.
- **Priority orders:** three goods a day pay **×1.5** extra.
- **Saturation:** the more the whole server sells of a good today, the less it pays:
  1 / (1 + Credits sold today / 3,000,000), never below 40%.
- Your normal grade and prestige sell multiplier applies on top.

Workshop payouts are server income, like selling ore. A running full chain adds roughly 30% to 60% to a run's income.

## The Assembly Bench

Turn components and Credits into a [drill module](drill.md) of your choice:

| Module | Grade | Credits | Components |
| --- | --- | --- | --- |
| Magnet Coil | 10 | 250K | 16 Wiring Harness, 4 Induction Coil |
| Cryo Coolant Loop | 13 | 1.5M | 4 Cryo Coil, 4 Armor Plate |
| Scavenger Rig | 15 | 3M | 4 Circuit Board, 8 Armor Plate |
| Plasma Cutter | 18 | 8M | 4 Focusing Array, 6 Circuit Board |
| Seismic Dampener | 20 | 20M | 2 Weapon Receiver, 8 Induction Coil |
| Token Condenser | 22 | 50M | 2 Reactor Core, 4 Void Capacitor |

## Bay levels

Expand your bay in the workshop menu. It grows around its centre, and your machines and builds stay put.

| Level | Size | Height | Machines | Locker | Cost | Grade |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 11×11 | 6 | 6 | 2,000 | Free | 1 |
| 1 | 15×15 | 7 | 10 | 5,000 | 30K Credits | 3 |
| 2 | 21×21 | 8 | 16 | 12,000 | 400K Credits | 8 |
| 3 | 27×27 | 9 | 24 | 30,000 | 5M Credits + <span class="cur shards">10 Shards</span> | 12 |
| 4 | 35×35 | 10 | 34 | 75,000 | 60M Credits + <span class="cur shards">40 Shards</span> | 18 |
| 5 | 45×45 | 12 | 48 | 200,000 | 400M Credits + <span class="cur shards">120 Shards</span> | 22 |

Conveyors don't count toward the machine limit.

## Online and offline

- Your bay runs every second **while you're online**.
- When you log back in, it **catches up on up to 4 hours** you were away, at **50% speed**.
- While you're online and the bay is processing, it has a 6% chance each minute of a **Token find** (4 to 7, scaled
  by grade and prestige, about 20 an hour at grade 1). Offline catch-up never pays Tokens.

## How it ties into progression

- Bays, machines and everything in your locker **survive prestige**, so a bay you build is yours for good.
- The workshop is the main source of components for building drill modules.
- Processed goods are worth trading on the [Black Market](black-market.md).
- Processing counts for [contracts](quests.md) and [gang contracts](gangs.md) (goods processed).
