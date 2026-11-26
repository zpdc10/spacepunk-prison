# Cargo and selling

Ore you mine doesn't go into your inventory. It goes into your **cargo hold**, a backpack measured in ore units. You
turn it into <span class="cur credits">Credits</span> by selling.

## Commands

| Command | What it does |
| --- | --- |
| `/sell` (`/sellall`) | Sell everything in your cargo hold |
| `/cargo` (`/hold`, `/backpack`, `/bp`) | Open the cargo hold: see contents, upgrade capacity, Auto-Seller |
| `/autosell` | Install or toggle the Auto-Seller |
| `/balance` (`/bal`) | Show your Credits, Tokens and Shards |

The Exchange (east wing of the station) has **Sell** and **Cargo** terminals.

## The cargo hold

- It starts at **500 units** and grows ×1.5 per upgrade, up to level 12 (about 64,900 units).
- Upgrade cost from level L to L+1 = **1,500 × 2.4^L** Credits.
- When it's full, mining stops filling it and you get a "cargo hold full" warning every 4 seconds. The cargo boss
  bar turns yellow at 60% and red at 90%.
- Cargo upgrades are **kept through prestige**. The ore inside is not.

| Level | Capacity (units) | Cost of the next level |
| --- | --- | --- |
| 0 | 500 | 1.5K |
| 1 | 750 | 3.6K |
| 2 | 1.12K | 8.64K |
| 3 | 1.69K | 20.74K |
| 4 | 2.53K | 49.77K |
| 5 | 3.8K | 119.44K |
| 6 | 5.7K | 286.65K |
| 7 | 8.54K | 687.97K |
| 8 | 12.81K | 1.65M |
| 9 | 19.22K | 3.96M |
| 10 | 28.83K | 9.51M |
| 11 | 43.25K | 22.83M |
| 12 | 64.87K | max |

## The Auto-Seller

A one-time cargo upgrade that sells ore the moment you mine it, so you never fill up.

- Needs **grade 8** and costs **50,000 Credits**.
- Pays **90%** of the normal price (a small cut for the convenience).
- Toggle it on and off any time with `/autosell` or in `/cargo`.

The **Magnet Coil** drill module also auto-sells everything you mine, at full price, but stops Token drops.

## Sell price

What one unit pays = **base price × your sell multiplier**.

Sell multiplier = **1 + 0.02 × (grade − 1) + 0.10 × prestige**, then these stack on top:

| Bonus | Effect |
| --- | --- |
| Gang Fence Network upgrade | +2% per level, up to +10% |
| Relic collection sets | +2% to +10% per completed set, permanent |
| Rust stim (Undercroft) | ×1.3 for 4 minutes, then ×0.85 for 2 minutes |
| Scavenger Rig module | −15% |

At grade 25 and Prestige 0 the multiplier is 1.48; at Prestige 5 it is 1.98.

## Base prices

Credits per unit, before the multiplier. Names are the station names the resource pack shows.

| Item | Credits |
| --- | --- |
| Slag | 1 |
| Slag Rubble | 1 |
| Ash Rock | 2 |
| Carbon Slag | 4 |
| Copper Scrap | 6 |
| Steel Scrap | 9 |
| Copper Scrap Bale | 14 |
| Hull Plating | 3 |
| Vent Ash | 3 |
| Plated Copper Scrap | 7 |
| Plated Steel Scrap | 10 |
| Conduit Ore | 13 |
| Plated Conduit | 15 |
| Steel Scrap Bale | 22 |
| Plated Brass | 28 |
| Brass Bale | 45 |
| Cryo Panel | 8 |
| Rime Stone | 9 |
| Deep Cryo Panel | 11 |
| Cobalt Crystal | 22 |
| Plated Cobalt | 26 |
| Void Crystal | 34 |
| Cobalt Cell | 45 |
| Cryo Crystal | 60 |
| Plated Cryo Crystal | 70 |
| Breach Rock | 20 |
| Scorched Basalt | 20 |
| Frost Quartz | 45 |
| Gilded Breach Rock | 60 |
| Void Glass | 80 |
| Quartz Hull | 95 |
| Verdant Crystal | 120 |
| Plated Verdant | 135 |
| Weeping Void Glass | 150 |
| Verdant Core | 220 |
| Foundry Slag | 50 |
| Brass Vein | 110 |
| Neutronium | 260 |
| Cryo Core | 420 |
| Dense Neutronium | 700 |

Any other block is worth 1 Credit.

## How it ties into progression

- Selling is the main source of Credits for [rank-ups](mines.md).
- You can also send ore to your [workshop](workshop.md) instead of selling it: a Cargo Intake pulls it straight from
  your hold, and processed goods are worth more.
- Some [contracts](quests.md) ask you to hand in ore from your cargo hold.
- Selling counts as server income for gang points and "earn Credits" quest goals.
