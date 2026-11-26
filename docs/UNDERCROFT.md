# The Undercroft and guns

The Undercroft is the lawless lower hull and the only place PvP is on. It's optional: the core loop never needs
it. Everything is tunable in `undercroft.yml` and `guns.yml`.

## Getting in and out

- Go down with the hub's **Undercroft Gate** terminal (warp `undercroft`) or `/undercroft`. You arrive at the
  **Guard Checkpoint**, which is safe.
- Walk east through the gate into **Ring 1, the Outer Bilge** (PvP on). Deeper rings need prestige:
  **Smuggler's Run** (Prestige 1), **The Sump** (Prestige 3).
- Leaving the checkpoint gives you a few seconds of **grace** (longer for new players): you can't hurt or be hurt.
- Hitting or being hit **combat tags** you for 15s: commands are locked, the checkpoint won't let you in, and
  logging out kills you.
- The only way out is the checkpoint: the **gate terminal** (lodestone), `/undercroft leave`, or any warp/teleport
  you try from the checkpoint. Teleports from inside the rings are blocked.
- When you die there, everything except your drill drops, and you wake up back at the station hub.

## Contraband

| Item | Where from | What for |
| --- | --- | --- |
| Hot ore (scrap, crystal, core slag) | Glowing veins in each ring, regrow after a minute | Sell at the `/fence` for far more than normal ore |
| Stim ingredients (sludge, rust flakes, cryo gel, volt salt) | Chance from hot ore veins, supply drops | Cook stims at a lab |
| Stims (Rust, Overdrive, Coldsleep, Static) | Cooked at drug labs | Strong timed boost, then a comedown; sell at the fence |
| Bounty tags | Dropped by players you kill (not twice in 30 min) | Credits (more if they had a bounty) + gang points at the fence |
| Smuggled Tech Crate | Supply drops | Open outside: gun parts, rare guns, Tokens, drill modules |

At the checkpoint the scanner checks each contraband stack: 40% chance it's confiscated. A **Smuggler's Lining**
(fence, supply drops) hides 3 stacks. Or **bribe the guard** (Credits, scales with grade and what you carry).
The fence only deals **outside** the Undercroft, so goods must be smuggled out. Fence payouts are server income
(`income:undercroft`), so they count toward the prestige Token payout.

## Labs and stims

Each ring has a drug lab (brewing stand). Right-click it, pick a recipe you have ingredients for, and stay within
6 blocks until it's cooked (20-35s, boss bar). Walk away or die and the batch is ruined. Everyone in the zone is
told someone is cooking. Deeper labs make more doses.

| Stim | High | Comedown |
| --- | --- | --- |
| Rust | Sell price x1.3 (4 min) | Sell x0.85, hunger (2 min) |
| Overdrive | +60% mining speed, Speed (4 min) | Mining Fatigue, Slowness (2 min) |
| Coldsleep | Resistance, Regeneration (90s) | Weakness, Slowness (60s) |
| Static | Gun damage x1.2, Strength (90s) | Weakness, Glowing (60s) |

One stim at a time. Taking another during a comedown stacks the comedowns.

**Gangs:** `UndercroftService#setLabController(lab, gangId, gangName, announce)` makes a gang the lab's controller;
it gets 25% of every batch through `StimCookedEvent#controllerCut()`. Kills fire `UndercroftKillEvent`, fence
turn-ins fire `BountyTurnInEvent` (gang points), smuggling fires `ContrabandSmuggledEvent`. Gang contracts
`pvp_kills` and `contraband_smuggled` are registered and progressed here.

## Supply drops

Every 12 minutes (if anyone is in the zone) a drop is announced server-wide with coordinates, lands 30s later as
a chest, and despawns after 4 minutes. Deeper rings get rarer drops with more loot.

## Guns

Buy at the `/gunsmith` (Credits, plus gun parts for the better ones; prices grow with grade), or find them in
drops. Guns only fire in PvP zones and drop on death like any other gear.

- **Right-click** fires (hold for the SMG's bursts). **Q** or **F** reloads using ammo from your inventory.
- **Sneak** tightens spread; the Bolt Rifle and Plasma Lance zoom while sneaking. Jumping widens spread.
- Recoil kicks your view up a little each shot. Headshots do extra damage; damage drops off with range.
- The action bar shows loaded/spare ammo and a reload bar. The item's durability bar is the magazine.

| Gun | Ammo | Role |
| --- | --- | --- |
| Rivet Pistol | Rivet Rounds | Cheap sidearm |
| Scrap SMG | Rivet Rounds | 3-round bursts, close range |
| Breach Shotgun | Breach Shells | 8 pellets, brutal up close |
| Bolt Rifle | Bolt Slugs | Scoped, slow, pierces one target |
| Plasma Lance | Plasma Cells | Prestige 1, accurate, pierces two |

## Admin

`/undercroft admin generate | regrow | reload | tp | drop [ring] | give <player> <item> [n] | lab <lab> <id|none> [name]`,
`/gun admin give|ammo|parts <player> ... | reload`. Permissions: `spacepunk.undercroft.build` (edit the zone),
`spacepunk.undercroft.bypass` (skip rings, tags and the checkpoint).
The layout is built automatically the first time the module starts.
