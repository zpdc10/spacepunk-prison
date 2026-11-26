# Architecture

One Paper plugin, split into feature modules that each own one package. Modules never import each
other's packages; they talk only through `dev.spacepunk.prison.api` (services + events). That lets
several people (or agents) work in parallel with few merge conflicts.

Design doc (source of truth for gameplay): https://claude.ai/artifact/AJtY7m7ZK2CK9ygxJhHukC

## Platform

- Paper **26.3** (`io.papermc.paper:paper-api:26.3.build.262-stable`), Java **25**.
- Vanilla clients only. Custom visuals come from the required server resource pack, using the
  `minecraft:item_model` data component (and custom model data where useful). No client mods.
- Commands use Paper Brigadier `BasicCommand`, registered via `SpacepunkPrison#addCommand(...)` from a
  module's `enable()`. Do not add commands to `plugin.yml`.
- Text uses MiniMessage via `util.Text`. Item names/lore: `Text.item(...)` (non-italic).
- Persistence: SQLite via `data.ProfileStorage`. Per-player module state goes in
  `PlayerProfile.moduleData("<module id>")` as a JSON string you (de)serialize with Gson.
  Call `ProfileService.markDirty(uuid)` after changes. Read profiles after `ProfileLoadedEvent`, not on join.
- Global (non-player) module state: your own YAML/JSON file in the plugin data folder, or your own
  SQLite table created in your module with its own connection.
- Main thread for all Bukkit/profile access. Heavy work (mine refills, file IO) off-thread.

## Ownership

| Package / path | Owner workstream | Provides |
| --- | --- | --- |
| `api/`, `core/`, `data/`, `economy/`, `util/`, `SpacepunkPrison.java`, `build.gradle.kts` | core (main thread) | Services registry, profiles, 3-currency economy, `/balance`, `/eco` |
| `mines/` + `mines.yml` | mines | `MineService`, fires `MineBlocksBrokenEvent`, mine refills, `/mine` |
| `progression/` + `progression.yml` | progression | `ProgressionService`, grades 1-25, prestige, cargo hold, selling, `/rankup` `/prestige` `/sell` |
| `drill/` + `drill.yml` | drill | The drill item, frames, enchants, modules, heat, drill menu |
| `hud/` + `hud.yml` | hud | `HudService` (shared action bar), sidebar, tab list, cargo boss bar, per-break feedback, `/hud` |
| `gangs/` + `gangs.yml` | gangs | `GangService`, crews and ranks, gang bank (prestige lock), crew chat, upgrades, weekly contracts and leaderboard, turf ownership, `/gang` `/gc` |
| `resourcepack/` (Java) + `/resourcepack/` (pack sources) | resource pack | Pack build + hosting + required-pack prompt on join; all textures/models |
| `raredrops/` + `raredrops.yml` | rare drops | Tiered finds while mining, relic collections (sell bonus via `OreSellEvent`), salvage caches, blueprint fragments → drill modules, `/salvage` |
| `auction/` + `auction.yml` | auction house | The Black Market: buy-it-now + escrowed auctions, fees/tax sink, `auctions.json`, `/ah`; blocks prestige while the player leads a bid (`PrestigeAttemptEvent`) |
| `fishing/` + `fishing.yml` | fishing | `FishingService`, fires `FishCaughtEvent` / `SeaCreatureKilledEvent`; the `station_waters` world (hub + one pool per deck), catches, reel-in minigame, soulbound rod (tiers, enchants, lures, bait), sea creatures, collections, weekly leaderboard, `/fish` `/rod` `/waters` `/fishadmin`. See `docs/FISHING.md` |
| `station/` + `station.yml` | station | Hub world + layout, `WarpService` (warps, hub spawn), terminals, `/hub` `/warp` `/warps` `/stationadmin`. See `docs/STATION.md` |

Changing anything in `api/` (new method, new event) affects everyone: keep it additive, and mention it
in your PR description. If you need something from another module, add an interface method or event in
`api/` rather than reaching into their package.

Modules are discovered by class name in `SpacepunkPrison.MODULE_CLASSES`
(`dev.spacepunk.prison.<id>.<Name>Module`, implementing `core.FeatureModule` with a no-arg constructor).
Load order: resourcepack, mines, station, progression, drill, gangs, hud, fishing, raredrops, auction. Missing classes are skipped, so each module can
land independently.

## Event flow when a player mines

1. Player breaks a block in a mine. Mines module cancels vanilla drops, removes the block, and fires
   `MineBlocksBrokenEvent(cause=HAND, ores={material: 1})`.
2. Drill (NORMAL priority): applies Yield (multiplies `ores`), adds heat, rolls Tokens, and may proc
   area enchants, which call `MineService.breakBlocks(..., ENCHANT)`. That fires another
   `MineBlocksBrokenEvent` with cause ENCHANT (drill must not proc enchants again on non-HAND causes).
3. Progression (HIGHEST): adds `ores` to the cargo hold, or auto-sells (price × `sellMultiplier`).
4. HUD (HIGH snapshot, MONITOR): measures what actually went into the hold, then shows it on the action bar
   (`⛏ +14 Iron Ore → hold  +3 ✦`), plays a pickup click and sparks for the miner, and raises the cargo-full alarm.

## Screen ownership (HUD)

The hud module owns the action bar. Never call `sendActionBar` directly when `HudService` is registered:
use `HudService#flash` for a short notice, or `HudService#segment(player, key, text)` for a persistent part
(the drill's heat gauge is segment `heat`). Fall back to `sendActionBar` only if the service is missing.
The sidebar uses a per-player scoreboard, so any future nametag/team feature must add its teams to each
player's board (ask the hud module for a hook) instead of the main scoreboard. Boss bars: mines shows the
current mine's fill, hud shows the cargo hold while mining.

## Money that never counts toward prestige

Prestige wipes Credits and pays Tokens for server income earned this run (EconomyService reasons starting
`sell:` or `income:`). Player-to-player money must use any other reason: the auction house uses `auction:buy`,
`auction:bid`, `auction:sale`, `auction:refund` and `auction:fee`. Anything that holds a player's Credits outside
their balance (escrow, banks) must stop that money surviving a prestige: listen to `PrestigeAttemptEvent` and
`deny(...)` it while the player has money held (the auction house does this for leading bids).

## Gangs hooks

`GangService` (see docs/GANGS.md): `gangOf`/`sameGang` for PvP and bounties (gangmates already can't hurt each
other), `registerContractObjective` + `addContractProgress` to feed weekly gang contracts from any module
(`fish_caught`, `goods_processed`, `pvp_kills`, `contraband_smuggled`, `parkour_runs`, `quests_done`),
`addPoints` for the weekly leaderboard, `setTerritoryOwner` + `depositFromServer` for Undercroft labs. Gang bank
moves use reasons `gang:deposit` / `gang:withdraw`, so they never count as server income for prestige.
`GangMembershipEvent` fires on join/leave/rank/disband/recolor so tags can refresh.

## Places and warps

The station hub is the main hub (world `station`); everything else lives in its own area and is reached by a
warp. Register destinations with `WarpService` (category `planetside`/`danger` show on the hub Shuttle Board,
`private` for per-player places) and listen to `PlayerWarpEvent` to gate trips. Hub terminals and what they run
are listed in `docs/STATION.md`.

## Currencies

`CREDITS` (sell ore → grade rank-ups), `TOKENS` (mining drops → enchants), `SHARDS` (prestige → frames,
permanent bonuses). Use `EconomyService`; it fires `CurrencyChangeEvent`.

## Balance targets

- 25 grades over 5 decks (5 grades each). Grade cost grows ~45% per grade (tune in config).
- 50 to 100 hours of active play from grade 1 to first prestige.
- All numbers in YAML config, never hard-coded.

## Asset keys (contract between drill/mines/progression and the resource pack)

Items reference models as `spacepunk:<path>` through the `item_model` component. The resource pack
workstream must provide every key below; other workstreams should use only these keys (add new ones
here in the same PR that needs them, and the pack falls back to a vanilla look until it exists).

| Key | Used for |
| --- | --- |
| `spacepunk:drill/rusted` | Drill, Rusted frame |
| `spacepunk:drill/salvaged` | Drill, Salvaged frame |
| `spacepunk:drill/industrial` | Drill, Industrial frame |
| `spacepunk:drill/military` | Drill, Military frame |
| `spacepunk:drill/prototype` | Drill, Prototype frame |
| `spacepunk:module/<module_id>` | Drill modules: `magnet_coil`, `plasma_cutter`, `scavenger_rig`, `coolant_loop`, `overcharger`, `unknown` (fallback for modules without art yet) |
| `spacepunk:ui/<name>` | Menu icons and filler panes, see the list below |
| `spacepunk:ui/heat_gauge` | Heat gauge, 9 stages: set `custom_model_data` floats `[heat 0.0-1.0]` |
| `spacepunk:ui/enchant/<enchant_id>` | Drill bay enchant icons (`bore_speed`, `yield`, `token_siphon`, `shaped_charge`, `jackhammer`, `cutting_laser`, `overclock`, `salvage_sense`, `coolant`, plus `locked`) |
| `spacepunk:ui/socket_empty`, `ui/socket_locked`, `ui/modules`, `ui/frame_upgrade`, `ui/close` | Drill bay sockets and buttons |
| `spacepunk:currency/token` | Token item/icon |
| `spacepunk:currency/shard` | Prestige Shard icon |
| `spacepunk:currency/credit` | Credits (scrip) icon |
| `spacepunk:fish/<fish_id>` | Fish from `fishing.yml` (art in `tools/resourcepack/fishing_art.py`, generated for every fish id found there), e.g. `spacepunk:fish/rust_minnow` |
| `spacepunk:rod/<tier>` | Fishing rod tiers (`condition` on `fishing_rod/cast`): `spacepunk:rod/bent_wire`, `scrap_spinner`, `carbon_line`, `mag_rail`, `void_angler` |
| `spacepunk:fishing/junk/<id>`, `fishing/lure/<id>`, `fishing/bait/<id>` | Fishing junk, lures and bait icons, e.g. `spacepunk:fishing/lure/spinner`, `spacepunk:fishing/bait/grub_paste` |
| `spacepunk:fishing/scale`, `spacepunk:fishing/cooler`, `spacepunk:fishing/collection`, `spacepunk:fishing/leaderboard`, `spacepunk:fishing/waters` | Fishing menu icons |
| `spacepunk:ui/rod_enchant/<id>` | Rod bay enchant icons, e.g. `spacepunk:ui/rod_enchant/lure_speed` |
| `spacepunk:raredrop/<drop_id>` | Rare drop items from `raredrops.yml` (art in `tools/resourcepack/raredrops_art.py`): `scrap_cache`, `sealed_crate`, `armored_strongbox`, `vault_core`, `fragment_common`, `fragment_rare`, `fragment_epic`, `fragment_legendary`, relics `inmate_tag` ... `warden_key` |

UI icons available as `spacepunk:ui/<name>`: `panel` (default filler), `panel_rust`, `panel_hazard`, `divider`,
`blank` (invisible), `back`, `previous`, `next`, `close`, `cancel`, `confirm`, `upgrade`, `add`, `info`, `sell`,
`locked`, `socket_empty`, `cargo`, `rank`, `prestige`, `enchant`, `vent`. Put them on any item (paper or a
glass pane is a good base) with `item_model`; set `hide_tooltip` for fillers. A key with no art shows the
missing-model cube, so add new keys here and ask the resource pack workstream (or run
`tools/resourcepack/generate.py` after adding the art there).

Mine blocks are vanilla blocks; the pack retextures them so the mines look grungy for everyone:
stone → slag, cobblestone → slag rubble, deepslate → hull plating, every ore (stone and deepslate variants)
→ scrap/conduit/crystal versions, packed and blue ice → cryo panels, amethyst block → void crystal,
obsidian and crying obsidian → void glass, ancient debris and netherite block → neutronium, andesite/tuff/
calcite/blackstone/basalt/quartz/gem blocks, raw ore blocks, plus every deck shell block in `mines.yml`
(wall, rim and light per deck: cut copper, polished stones, blackstone bricks, froglights, sea lantern,
shroomlight). The matching drop items (coal, raw copper, diamond, ...) are retextured too. Prefer these blocks
when building mines.

The pack is built from `/resourcepack` by Gradle (`resourcePackZip`), embedded in the jar, and served by the
plugin's own HTTP server (`resourcepack.yml`, default port 25580). See `resourcepack/README.md`.

## Branches and PRs

Each workstream works on its own branch and opens a PR into `main`. CI (`.github/workflows/build.yml`)
must be green. Keep PRs inside your package; rebase on `main` before merging.
