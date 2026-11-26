# Maintenance runs (daily parkour)

Every mine has a **maintenance run**: a parkour course of catwalks, pipes and gantries in the void just north of the
mine room, through a doorway in its north wall. Finish it for a **reward roll once a day per course**.

## Commands

| Command | What it does |
| --- | --- |
| `/parkour` (`/pk`, `/runs`) | Menu: today's status, best times, click a course to go there |
| `/parkour go <mine>` | Travel to a mine's run |
| `/parkour cp` | Back to your last checkpoint |
| `/parkour leave` | Quit the run |

Each run is also a warp, `parkour-<mine>` (for example `/warp parkour-cryo-11`). The Mine Lifts wing has a parkour
board.

## How a run works

1. Step on the **iron plate** to start. The clock runs on your action bar.
2. **Gold plates** are checkpoints. Falling puts you back on the last one.
3. The last gold plate is the finish. Two seconds later you're sent back to the mine.

- Runs grow from **14 jumps** in the first mine to **38** in the last (+1 per grade).
- Checkpoints come every 5 to 7 jumps.
- Every jump is possible on a vanilla client. A 3-block gap is a full sprint jump.
- You can run a course as often as you like for a better time, but it only pays **one roll a day** (resets at
  midnight server time). Personal bests and course records are kept.

## Difficulty by deck

| Deck | Course style | Gaps | Platforms |
| --- | --- | --- | --- |
| Scrapyard Gantries | Wide and forgiving, no diagonals | 1 to 2 | Mostly 2 to 3 block lines, some wide |
| Reactor Pipework | Some diagonals | 1 to 3 | Lines, singles, slabs |
| Cryo Catwalks | 15% diagonals | 2 to 3 | Singles, slabs, trapdoors |
| Hull Scaffolds | 25% diagonals | 2 to 3 | Mostly single blocks and trapdoors |
| Foundry Rigging | 30% diagonals, highest climbs | 2 to 3 | Half single blocks, trapdoors |

## Daily reward roll

One line is picked by weight. "Credits" are a share of **your next rank-up cost**, so the roll stays useful at any
grade, and they count as server income.

| Roll | Scrapyard | Reactor Ducts | Cryo Vaults | Hull Breach | Core Foundry |
| --- | --- | --- | --- | --- | --- |
| Common (~55%) | 4% rank-up + 4-8 Tokens | 4% + 6-12 | 4% + 8-16 | 4% + 10-20 | 4% + 12-24 |
| Tokens (30%) | 12-24 Tokens | 18-32 | 25-45 | 32-55 | 40-70 |
| Good (12%) | 12% rank-up + 8-12 Tokens | 12% + 10-16 | 12% + 14-22 | 12% + 18-28 | 12% + 22-34 |
| Rare: Token cache | 120-200 (2%) | 180-300 (2%) | 250-400 (3%) | 350-550 (3%) | 500-800 (3%) |
| Rare: Shards | | | | 3 Shards (1%) | 5 Shards (2%) |
| Rare: module (1%) | Coolant Loop | Magnet Coil | Scavenger Rig | Plasma Cutter | Token Condenser |

Every rewarded run also gives **2 [scrap parts](rare-drops.md#scrap-parts)**. The Gyro Stabilizer part only comes from
parkour.

Rare rolls are announced to the server.

## How it ties into progression

Five minutes a day per mine for Credits, Tokens and a shot at modules and Shards. Finishing runs also counts toward
[contracts](quests.md) and [gang contracts](gangs.md). Every reward roll also has a 12% chance at an
[Exo-Rig](exo-rig.md) module.
