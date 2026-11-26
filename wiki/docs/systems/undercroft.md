# The Undercroft

The Undercroft is the lawless lower hull and the **only place on the server where PvP is on**. It's where contraband
comes from, and getting it out alive is the whole game. It's optional: nothing in the core loop needs it.

## Commands

| Command | What it does |
| --- | --- |
| `/undercroft` (`/uc`) | Go down to the Guard Checkpoint |
| `/undercroft leave` | Leave (only from the checkpoint) |
| `/fence` | Sell contraband (only outside the Undercroft) |
| `/gunsmith` (`/guns`) | Buy guns and ammo |

The station's **Guard Checkpoint** (off the Shuttle Bay) has the Undercroft gate terminal (warp `undercroft`).

## The rings

You arrive at the **Guard Checkpoint**, which is safe. Walk east through the gate into the rings. Each ring is richer,
deadlier and gated behind prestige.

| Ring | Needs | Hot ore | Drug lab |
| --- | --- | --- | --- |
| 1. Outer Bilge | Grade 1 | Hot Scrap (30 at grade 1) | Bilge Still: Rust, Overdrive |
| 2. Smuggler's Run | Prestige 1 | Hot Crystal (70) | Rustworks: Rust, Overdrive, Coldsleep |
| 3. The Sump | Prestige 3 | Hot Core Slag (160) | Sump Kitchen: Overdrive, Coldsleep, Static |

## The rules of the zone

- **Grace:** leaving the checkpoint gives you **10 seconds** where you can't hurt or be hurt. New players (Prestige 0,
  up to grade 5) get **30 seconds**.
- **Combat tag:** hitting or being hit tags you for **15 seconds**. While tagged, most commands are blocked (only
  messaging, `/gunsmith` and `/undercroft` work), the checkpoint won't let you in, and **logging out kills you**.
- **Leaving:** the only way out is the checkpoint: its gate terminal, `/undercroft leave`, or any warp from the
  checkpoint. Teleports from inside the rings are blocked.
- **Death:** everything except your drill drops. You wake up back at the station.
- No mobs spawn here, and natural regeneration is on.

## Contraband

| Item | Where from | What for |
| --- | --- | --- |
| Hot ore | Glowing veins in each ring; they regrow a minute after you mine them | Sell at the fence for far more than normal ore |
| Stim ingredients: Toxic Sludge, Rust Flakes, Cryo Gel, Volt Salt | A chance from hot ore veins (rarer kinds deeper), supply drops | Cooking stims |
| Stims | Cooked at drug labs | A strong timed boost, or sell at the fence (150 to 260 at grade 1) |
| Bounty tags | Dropped by players you kill (not again for the same player within 30 minutes) | Credits at the fence, plus gang points |
| Smuggled Tech Crate | Supply drops | Open outside the Undercroft for gun parts, rare guns, Tokens and drill modules |

Fence prices and bribes grow with your grade (×1.21 per grade). Fence payouts are **server income**.

## Stims and drug labs

Right-click a lab (brewing stand), pick a recipe you have ingredients for, and **stay within 6 blocks** until it's
cooked (a boss bar shows progress). Walk away or die and the batch is ruined. Everyone in the zone is told someone is
cooking. Labs in deeper rings make one extra dose per ring.

| Stim | Recipe | Doses | Cook time | High | Comedown |
| --- | --- | --- | --- | --- | --- |
| Rust | 2 Rust Flakes + 1 Sludge | 3 | 20 s | Sell price ×1.3 for 4 min | Sell ×0.85 and Hunger for 2 min |
| Overdrive | 2 Volt Salt + 1 Sludge | 3 | 25 s | +60% mining speed and Speed for 4 min | Mining Fatigue and Slowness for 2 min |
| Coldsleep | 2 Cryo Gel + 1 Rust Flakes | 2 | 30 s | Resistance and Regeneration for 90 s | Weakness and Slowness for 60 s |
| Static | 1 Volt Salt + 1 Cryo Gel + 1 Sludge | 2 | 35 s | Gun damage ×1.2 and Strength for 90 s | Weakness and Glowing for 60 s |

One stim at a time. Taking another during a comedown stacks the comedowns.

If a [gang](gangs.md) controls the lab, 25% of every batch you cook there goes to that gang (unless it's yours).

## Smuggling it out

The fence only deals **outside**, so contraband has to pass the checkpoint scanner on the way out.

- Each unhidden contraband stack has a **40% chance** of being confiscated.
- A **Smuggler's Lining** (25,000 Credits at grade 1 from the fence, or from supply drops) hides **3 stacks**.
- Or **bribe the guard**: 3,000 Credits at grade 1, plus 30% of what you're carrying is worth.

## Supply drops

Every **12 minutes** (when anyone is in the zone) a drop is announced to the whole server with coordinates. It lands
30 seconds later as a chest and despawns after 4 minutes. Drops in deeper rings are rarer and have more loot: gun
parts, ammo, stim ingredients, a Smuggler's Lining, Smuggled Tech Crates, and sometimes a Scrap SMG or Breach
Shotgun.

**Smuggled Tech Crates** open outside the Undercroft for two rolls of: 4 to 10 gun parts, a Bolt Rifle, a Plasma
Lance, 2,000 to 8,000 Tokens, or a Plasma Cutter, Coolant Loop or Overcharger module.

## Scrap parts

Downing a player has a 25% chance of a [scrap part](rare-drops.md#scrap-parts), and every smuggling run that gets
contraband out has 60%. The Military Relay part, needed for the Overcharger and Singularity Core, only comes from here.

## Bounties

Each kill adds 250 Credits (at the killer's grade) to the killer's bounty. A bounty tag is worth 400 Credits at the
victim's grade, plus the victim's bounty, and 1 gang leaderboard point at the fence.

## How it ties into progression

High risk, high reward: hot ore and stims sell for far more than ore, tech crates are a source of modules and
Tokens, and gangs fight over the labs for hourly bank income. Stims are timed boosts with downsides, so players who
skip PvP never fall far behind.
