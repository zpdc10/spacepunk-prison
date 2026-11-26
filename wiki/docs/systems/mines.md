# Mines and grades

Mining is the heart of the station. There are **25 clearance grades** across **five decks**, and every grade has its
own mine. Each grade costs more than the last and opens a richer mine.

## Commands

| Command | What it does |
| --- | --- |
| `/mine` | Go to the best mine your grade unlocks |
| `/mine <id or grade>` | Go to a specific mine you have unlocked, e.g. `/mine reactor-6` |
| `/mines` | List every mine; click one you can use to travel there |
| `/rankup` (`/ru`) | Buy your next grade |
| `/grades` (`/ranks`) | List every grade and its cost |

The **Mine Lifts** in the station's north wing have a lift per deck and a "best mine" lift.

## The decks

| Deck | Grades | Look |
| --- | --- | --- |
| 1. Scrapyard | 1 to 5 | Rusted copper hull plates, amber lamps |
| 2. Reactor Ducts | 6 to 10 | Dark plating, vents, toxic green lamps |
| 3. Cryo Vaults | 11 to 15 | Pale panels, frost, cold blue lamps |
| 4. Hull Breach | 16 to 20 | Cracked black bricks, pearl lamps |
| 5. Core Foundry | 21 to 25 | Scorched basalt, furnace lamps |

## How a mine works

- Every mine is an **enclosed room**: walls, a lit ceiling nine blocks above the rim walkway, and a landing pad where
  you arrive.
- Mines are **shared** with everyone who has that grade. **PvP is off** and there is **no fall damage** in the mines
  world. You can only break blocks inside the pit.
- A mine **resets** once 80% of its blocks are gone (20% left). Players in the room get a **1-minute countdown**,
  then are moved out of the way while it refills.
- **Ladders** run up every pit wall, three per wall, so you can always climb back to the walkway.
- A boss bar shows the mine's name and how much ore is left while you're near it.
- The north wall of each room has a doorway to that mine's daily [maintenance run](parkour.md).

## Rank-up costs

Cost to go from grade g to g+1 = **4,000 × 1.45^(g-1)** Credits, times **(1 + 0.5 × prestige)**. So every
rank-up costs 50% more per prestige level: ×1.5 at Prestige 1, ×2 at P2, ×3.5 at P5.

The whole first run (24 rank-ups) costs about **66 million Credits**.

## Every mine

Rank-up costs are for prestige 0. "Avg value" is the average sell price of one block in Credits, before your sell
multiplier.

| Grade | Mine | Deck | Rank-up to next (P0) | What's in it | Avg value/block |
|---|---|---|---|---|---|
| 1 | Slag Heap | Scrapyard | 4K | Slag 52%, Carbon Slag 33%, Slag Rubble 15% | 2.0 |
| 2 | Rust Pit | Scrapyard | 5.8K | Slag 48%, Carbon Slag 25%, Copper Scrap 14%, Slag Rubble 13% | 2.4 |
| 3 | Wreck Yard | Scrapyard | 8.41K | Slag 41%, Copper Scrap 23%, Carbon Slag 22%, Ash Rock 15% | 2.9 |
| 4 | Hull Graveyard | Scrapyard | 12.19K | Ash Rock 44%, Copper Scrap 21%, Slag 19%, Steel Scrap 12%, Carbon Slag 5% | 3.5 |
| 5 | Smelter Floor | Scrapyard | 17.68K | Ash Rock 62%, Copper Scrap 18%, Steel Scrap 16%, Copper Scrap Bale 4% | 4.3 |
| 6 | Intake Vent | Reactor Ducts | 25.64K | Hull Plating 50%, Plated Steel Scrap 25%, Vent Ash 15%, Plated Copper Scrap 10% | 5.2 |
| 7 | Coolant Line | Reactor Ducts | 37.18K | Hull Plating 44%, Plated Steel Scrap 22%, Conduit Ore 17%, Vent Ash 16% | 6.3 |
| 8 | Turbine Shaft | Reactor Ducts | 53.91K | Hull Plating 54%, Plated Conduit 19%, Vent Ash 15%, Steel Scrap Bale 12% | 7.6 |
| 9 | Core Bypass | Reactor Ducts | 78.16K | Vent Ash 64%, Plated Conduit 19%, Plated Brass 10%, Steel Scrap Bale 7% | 9.2 |
| 10 | Meltdown Chamber | Reactor Ducts | 113.34K | Vent Ash 61%, Plated Conduit 18%, Plated Brass 16%, Brass Bale 5% | 11.1 |
| 11 | Frost Locker | Cryo Vaults | 164.34K | Cryo Panel 49%, Rime Stone 21%, Plated Brass 17%, Cobalt Crystal 13% | 13.5 |
| 12 | Stasis Bay | Cryo Vaults | 238.29K | Cryo Panel 45%, Cobalt Crystal 27%, Rime Stone 16%, Brass Bale 12% | 16.3 |
| 13 | Rime Archive | Cryo Vaults | 345.52K | Deep Cryo Panel 34%, Plated Cobalt 27%, Void Crystal 22%, Rime Stone 18% | 19.7 |
| 14 | Cold Storage | Cryo Vaults | 501.01K | Deep Cryo Panel 59%, Void Crystal 21%, Cobalt Cell 11%, Cryo Crystal 9% | 23.8 |
| 15 | Cryo Core | Cryo Vaults | 726.46K | Deep Cryo Panel 54%, Void Crystal 21%, Cryo Crystal 16%, Plated Cryo Crystal 9% | 28.8 |
| 16 | Breach Edge | Hull Breach | 1.05M | Breach Rock 39%, Frost Quartz 29%, Cryo Crystal 19%, Scorched Basalt 13% | 34.9 |
| 17 | Vacuum Gap | Hull Breach | 1.53M | Gilded Breach Rock 37%, Frost Quartz 30%, Breach Rock 23%, Scorched Basalt 10% | 42.2 |
| 18 | Debris Field | Hull Breach | 2.21M | Scorched Basalt 49%, Gilded Breach Rock 26%, Verdant Crystal 14%, Void Glass 11% | 51.1 |
| 19 | Torn Plating | Hull Breach | 3.21M | Scorched Basalt 51%, Void Glass 18%, Verdant Crystal 14%, Plated Verdant 10%, Quartz Hull 8% | 61.9 |
| 20 | Void Lip | Hull Breach | 4.66M | Breach Rock 50%, Plated Verdant 17%, Void Glass 16%, Weeping Void Glass 11%, Verdant Core 6% | 74.8 |
| 21 | Slag Furnace | Core Foundry | 6.75M | Foundry Slag 53%, Brass Vein 26%, Weeping Void Glass 15%, Verdant Core 6% | 90.6 |
| 22 | Crucible | Core Foundry | 9.79M | Foundry Slag 43%, Brass Vein 24%, Weeping Void Glass 22%, Neutronium 11% | 109.6 |
| 23 | Pour Line | Core Foundry | 14.2M | Foundry Slag 56%, Weeping Void Glass 21%, Neutronium 14%, Cryo Core 8% | 132.6 |
| 24 | Forge Heart | Core Foundry | 20.58M | Foundry Slag 54%, Neutronium 23%, Cryo Core 12%, Verdant Core 11% | 160.2 |
| 25 | Core Foundry | Core Foundry | Prestige | Foundry Slag 55%, Neutronium 26%, Cryo Core 13%, Dense Neutronium 7% | 194.2 |

Each mine is worth about 21% more per block than the one before it, while rank-ups cost 45% more, so every grade
takes a little longer than the last.

## How it ties into progression

- Your grade sets which mine you can use, your [sell bonus](selling.md) (+2% per grade), which
  [fishing pool](fishing.md) you can fish, which [workshop machines](workshop.md) you can buy, and which
  [story chapter](quests.md) you're on.
- Grade 25 unlocks [prestige](prestige.md).
- From grade 21, you can't deposit into a [gang bank](gangs.md).
