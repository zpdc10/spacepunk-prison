# Fishing

The station's flooded decks: a second way to earn and progress next to mining. Module `fishing/`, tuning in
`src/main/resources/fishing.yml` (copied to the plugin folder on first start; `/fishadmin reload`).

## Where

`station_waters` is its own void world (normal difficulty so sea creatures can fight; nothing spawns naturally,
no hunger, no PvP, no block changes, keep-inventory). It is built automatically on first start: the **Bilge Hub**
(Fish Market, weekly biggest-catch board, top anglers board) and one pool per deck in a row to the east, joined
by bridges. Each pool has the deck's wall, floor and light blocks and its own biome so the water is tinted
(swampy runoff, toxic sludge, cold stasis fluid, bright breach water, foundry quench tanks).

| Pool | Deck | Clearance |
| --- | --- | --- |
| Scrapyard Runoff | Scrapyard | Grade 1 |
| Sludge Tanks | Reactor Ducts | Grade 6 |
| Frozen Pools | Cryo Vaults | Grade 11 |
| Breach Flood | Hull Breach | Grade 16 |
| Quench Tanks | Core Foundry | Grade 21 |

Every pool is registered as a `planetside` warp (`fishing`, `fishing-<area>`), so it shows on the hub's Shuttle
Board. `/waters [pool]` also works. `/fishadmin build` rebuilds the layout.

## Catching

1. Cast into a pool. Lure Speed, bait and lures shorten the wait.
2. On a bite the action bar flashes. Right-click to hook it.
3. The bite is rolled: maybe a **sea creature**, otherwise junk, treasure or a fish (rarity first, then a species
   of that rarity from this pool). Commons and junk land at once.
4. Uncommon and better fish fight: the **reel-in minigame** runs on the **XP bar**, which becomes line tension.
   Hold **sneak** to pull the bar up, let go and it drops, and the fish yanks it around. Keep it in the middle
   (30% to 70%) to reel; the XP level number is the catch percent (it lands at 100, escapes at 0). The boss bar
   repeats the progress and says REELING, TOO TIGHT or TOO SLACK. Rarer fish dart harder and the bar is more
   sensitive. The XP bar is a display-only packet: real XP and levels are never changed and come back when the
   fight ends (it is hidden in creative mode, so test in survival or adventure).
5. Fish go to your inventory (overflow goes to the **cooler**). Legendary catches are unique trophies with their
   weight, announced server-wide.

Each fish has a weight roll (big ones are rare). Weights feed the weekly biggest-catch board (top 3 win Scales on
Monday 00:00 UTC), personal bests and per-species station records.

## Money and progression

- **Sell** at the Fish Market for Credits, paid as `income:fishing`, so it counts toward the prestige "earned
  this run". Value = base x 1.2584^(grade-1, capped at the pool's top grade) x pool value-mult x (level, Fillet,
  lure and collection bonuses) x the progression sell multiplier.
- **Gut** fish instead for **Scales**, the fishing upgrade material (sea creatures and treasure also give Scales).
- **Fishing level** (1-50): XP per catch by rarity; +1% fish value per level; unlocks rod tiers.
- **Collections**: tiers per species (1/25/100/500/2000 catches; legendaries 1/3/10/25/60) add value to that species
  and pay Scales.

## The rod

One soulbound rod per player (given on first visit, `/rod` returns it). Shift + left-click or `/rod` opens the
rod bay.

- **Tiers** (Bent Wire, Scrap Spinner, Carbon Line, Mag-Rail, Void Angler): need a fishing level and Scales; raise
  the enchant cap and lure slots (1-3).
- **Enchants** (Scales): Lure Speed, Double Catch, Angler's Eye, Treasure Luck, Chum Sense, Steady Hands, Line
  Strength, Harpoon (damage to sea creatures with the rod), Fillet (value).
- **Lures**: items from treasure and sea creatures, slotted into the rod (tradeable when not slotted).
- **Bait**: bought in the bait shop with Credits (price follows grade), used one per catch.

## Sea creatures

Three per pool (common, uncommon, rare boss). Only the angler who hooked it can hurt it, and it only hunts them.
Kills give Scales, Credits (income), fishing XP and lure/bait drops; bosses are announced. They sink back after 90s
or if the angler leaves.

## Hooks for other modules

- `FishingService`: fishing level, total catches, catches per species, Scales, `giveScales` (quest rewards).
- `FishCaughtEvent` (kind fish/junk/treasure, species, rarity, weight, amount) and `SeaCreatureKilledEvent`.
- Gang contracts: objectives `fish_caught` and `sea_creatures_slain` are registered when gangs is present.
- Treasure entries of `type: command` run a console command, e.g. `drill admin module {player} magnet_coil`.

## Admin

`/fishadmin build | reload | resetweek | give <player> fish|lure|bait|scales|xp|rod ...`
