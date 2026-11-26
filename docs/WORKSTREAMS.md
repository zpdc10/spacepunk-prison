# Parallel workstreams (core loop, step 3)

Shared rules for every workstream:

- Read `docs/ARCHITECTURE.md` first (package ownership, `api/` contracts, module discovery, commands via
  `SpacepunkPrison#addCommand`, asset keys) and the design doc:
  https://claude.ai/artifact/AJtY7m7ZK2CK9ygxJhHukC
- Stay inside your own package. Keep `api/` changes additive and call them out in the PR.
- Own branch, one PR into `main`, CI green. Cloud containers cannot reach repo.papermc.io, so GitHub CI is
  the compiler: push and read the Build check.
- Vanilla clients only (no client mods). Every tuning number lives in YAML.

## 1. Mines (`mines/`, `MinesModule`)

- 25 shared mines, one per grade, across 5 decks (Scrapyard, Reactor Ducts, Cryo Vaults, Hull Breach,
  Core Foundry). `mines.yml` holds region, required grade and weighted block mix per mine.
- Refill when a configurable % is mined or on a timer, using FAWE (compileOnly) off the main thread; warn
  and move players out first.
- Block breaks inside a mine: cancel vanilla drops, remove the block, fire `MineBlocksBrokenEvent`.
  Implement `MineService` including `breakBlocks` for enchants. PvP off inside mines.
- Admin commands to create/define/reset mines; `/mine [id]` teleports, gated by grade when
  `ProgressionService` is registered.

## 2. Progression (`progression/`, `ProgressionModule`)

- Grades 1-25, cost growth ~45% per grade, tuned so first prestige takes 50-100 active hours.
- Prestige at 25: grade back to 1, Credits cleared, drill kept, Shards granted, permanent sell bonus.
- Cargo hold (upgradeable capacity, in `moduleData("progression")`), per-material sell prices, sell
  multiplier from grade/prestige.
- `/rankup`, `/rankup max`, `/prestige` (with confirm), `/sell`, `/cargo` (menu). Implement
  `ProgressionService`, fire `GradeChangeEvent`, consume `MineBlocksBrokenEvent` at HIGHEST.

## 3. Drill (`drill/`, `DrillModule`)

- One soulbound drill per player, kept through prestige, cannot be dropped/lost; given on first join.
- 5 frame tiers (Rusted, Salvaged @ grade 10, Industrial @ P1, Military @ P5, Prototype @ P15) setting
  level caps and module sockets (0-4).
- Token-bought enchants: Bore Speed (block-break-speed attribute), Yield, Token Siphon, Shaped Charge,
  Jackhammer, Cutting Laser, Overclock, Salvage Sense. Exponential costs.
- Modules with trade-offs (Magnet Coil, Plasma Cutter, Scavenger Rig, ...), admin give command for now.
- Heat: heavy enchants add heat, overheat stalls a few seconds, early vent gives a small burst, coolant
  upgrades raise the cap; gentle in early decks. Show heat on the action bar.
- Drill menu on shift + right-click. Listen to `MineBlocksBrokenEvent` at NORMAL; only HAND procs enchants.

## 4. Resource pack (`/resourcepack` sources + `resourcepack/`, `ResourcePackModule`)

- Grungy spacepunk pack for Minecraft 26.3 covering every key in the asset-key table, plus retextures of
  the vanilla blocks used in mines. Original art only.
- CI zips the pack and computes its SHA-1.
- The plugin serves the pack itself from a small built-in HTTP server on a configurable port and sends it
  as required on join (decline = kick with a friendly message). Config: port, public URL override.

## Server setup (main thread)

Paper 26.3 container on the Unraid server, deploy flow for the built jar and pack.
