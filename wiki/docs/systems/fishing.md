# Fishing

The station's flooded decks are a second way to earn and progress next to mining. Fish for
<span class="cur credits">Credits</span>, gut fish for <span class="cur scales">Scales</span>, level up a soulbound
rod, fight sea creatures, and chase legendary trophies.

## Commands

| Command | What it does |
| --- | --- |
| `/waters [pool]` (`/pools`) | Travel to the fishing world or a specific pool |
| `/fish` (`/fishing`) | Fish Market, bait shop, collections and leaderboards |
| `/rod` | Get your rod back, or open the rod bay |

The Shuttle Bay's **Fishing** airlock and the Shuttle Board both go there too. Each pool is a warp
(`fishing`, `fishing-<pool>`).

## Where to fish

Fishing lives in its own world, the **station waters**. You arrive at the **Bilge Hub**, which has the Fish Market,
the weekly biggest-catch board and the top anglers board. Five pools run east from it, joined by bridges, one per
deck. Each has its deck's look and tinted water.

| Pool | Deck | Opens at | Value multiplier |
| --- | --- | --- | --- |
| Scrapyard Runoff | Scrapyard | Grade 1 | ×1.0 |
| Sludge Tanks | Reactor Ducts | Grade 6 | ×1.1 |
| Frozen Pools | Cryo Vaults | Grade 11 | ×1.2 |
| Breach Flood | Hull Breach | Grade 16 | ×1.3 |
| Quench Tanks | Core Foundry | Grade 21 | ×1.4 |

The waters are safe: no PvP, no hunger, no natural mobs, and you keep your inventory.

## Catching a fish

1. **Cast** into a pool. A bite takes 5 to 15 seconds; Lure Speed, bait and lures shorten it (by up to 75%).
2. When the **action bar flashes**, right-click to hook it.
3. The bite is rolled: 3.5% base chance of a **sea creature**, otherwise **junk** (12%), **treasure** (3%) or a
   **fish** (85%). A fish rolls its rarity first, then a species of that rarity from this pool.

| Rarity | Chance (of fish) | Fishing XP | Scales when gutted |
| --- | --- | --- | --- |
| Common | 64% | 3 | 1 |
| Uncommon | 24% | 8 | 3 |
| Rare | 9% | 20 | 8 |
| Epic | 2.6% | 60 | 25 |
| Legendary | 0.4% | 250 | 100 |

Commons and junk land straight away. Anything better **fights**.

Every landed catch also has a **3% chance of a Token find** (4 to 7, scaled by grade and prestige, about 25 an hour
at grade 1).

## The reel-in minigame

While you fight a fish, your **XP bar becomes the line tension** and the **XP level number shows your catch
percent**.

- **Hold sneak** to pull the tension up. Let go and it drops. The fish yanks it around on its own.
- Keep the tension **in the middle band (30% to 70%)** to reel in. Too tight or too slack and the catch slips.
- The catch lands at **100** and escapes at **0**. The boss bar repeats your progress and says REELING, TOO TIGHT or
  TOO SLACK.
- Rarer fish dart harder and more erratically, with a narrower zone: 30% wide for uncommon down to 18% for
  legendary.
- **Steady Hands** widens the zone; **Line Strength** slows the drain when the fish slips.

Your real XP and levels are never touched: they come back when the fight ends. (The bar is hidden in creative mode.)

## What you catch

Fish go to your inventory, and overflow goes to your **cooler**. Each fish has a **weight** roll, and big ones are
rare. **Legendary** catches are unique trophies with their weight, announced to the whole server.

**Treasure** can be a Scale Cache (8 to 30 Scales), Waterlogged Scrip (60 to 200 Credits), a Token Pouch (20 to 50
Tokens at grade 1, scaled by grade and prestige), bait crates, lost lures, or very rarely a **Sealed Module Case** with a Magnet Coil or Scavenger Rig.

**Junk** (bolts, cable, rations, visors, spent cells) sells for 1 to 3 Credits and guts for nothing.

## Selling or gutting

At the Fish Market you choose:

- **Sell** for Credits. Value = base value × 1.2584^(grade − 1, capped at the pool's top grade) × the pool's
  multiplier × your bonuses (fishing level, Fillet, lures, collections) × your normal sell multiplier. Fish money is
  server income, like selling ore.
- **Gut** for Scales, the fishing upgrade material.

## Sea creatures

Each pool has three: a common one, an uncommon one and a rare **boss**. Only the angler who hooked it can hurt it, and
it only hunts them. Kills give Scales, Credits, fishing XP and lure or bait drops. Bosses are announced. A creature
sinks back after 90 seconds or if you leave. **Harpoon** adds rod damage against them; **Chum Sense** and the Siren
Lure make them appear more often (up to 30%).

## Fish and creatures by pool

### Scrapyard Runoff (grade 1+, value ×1.0)

*Oily runoff from the scrap presses. Things live in it anyway.*

| Fish | Rarity | Base value | Weight |
| --- | --- | --- | --- |
| Rust Minnow | Common | 6 | 0.1 to 0.6 kg |
| Oilgill | Common | 7 | 0.3 to 1.4 kg |
| Bolt Carp | Common | 8 | 0.8 to 3 kg |
| Scrap Pike | Uncommon | 20 | 1.5 to 6 kg |
| Rivet Eel | Uncommon | 22 | 1 to 4.5 kg |
| Tinplate Bass | Rare | 60 | 2.5 to 9 kg |
| Gearback Gar | Epic | 200 | 6 to 22 kg |
| The Bilge King | Legendary | 1200 | 30 to 95 kg |

| Sea creature | Level | Health | Scales | Credits |
| --- | --- | --- | --- | --- |
| Bilge Rat | 2 | 18 | 3 to 8 | 10 to 30 |
| Drowned Scrapper | 5 | 40 | 6 to 15 | 25 to 60 |
| Rust Leviathan (boss, announced) | 10 | 120 | 30 to 60 | 120 to 250 |

### Sludge Tanks (grade 6+, value ×1.1)

*Reactor coolant gone thick and green. Don't drink it.*

| Fish | Rarity | Base value | Weight |
| --- | --- | --- | --- |
| Glowfry | Common | 6 | 0.1 to 0.5 kg |
| Sludge Puffer | Common | 7 | 0.4 to 1.8 kg |
| Duct Perch | Common | 8 | 0.7 to 2.6 kg |
| Isotope Trout | Uncommon | 20 | 1.2 to 5 kg |
| Coolant Catfish | Uncommon | 22 | 2 to 8 kg |
| Fission Ray | Rare | 60 | 3 to 12 kg |
| Meltdown Mackerel | Epic | 200 | 5 to 20 kg |
| The Half-Life Sturgeon | Legendary | 1200 | 40 to 130 kg |

| Sea creature | Level | Health | Scales | Credits |
| --- | --- | --- | --- | --- |
| Sludge Blob | 12 | 70 | 8 to 18 | 30 to 70 |
| Duct Eel | 15 | 90 | 10 to 24 | 40 to 90 |
| Reactor Wyrm (boss, announced) | 20 | 260 | 50 to 100 | 200 to 400 |

### Frozen Pools (grade 11+, value ×1.2)

*Thawed stasis fluid. Some of the fish were frozen on purpose.*

| Fish | Rarity | Base value | Weight |
| --- | --- | --- | --- |
| Frostfin | Common | 6 | 0.2 to 0.8 kg |
| Cryo Shrimp | Common | 7 | 0.1 to 0.4 kg |
| Icebreaker Cod | Common | 8 | 1 to 3.5 kg |
| Glacial Char | Uncommon | 20 | 1.5 to 6 kg |
| Stasis Eel | Uncommon | 22 | 1 to 5 kg |
| Hoarfrost Salmon | Rare | 60 | 3 to 11 kg |
| Absolute Zero Pike | Epic | 200 | 6 to 24 kg |
| The Sleeper | Legendary | 1200 | 50 to 160 kg |

| Sea creature | Level | Health | Scales | Credits |
| --- | --- | --- | --- | --- |
| Frostbitten Diver | 22 | 110 | 14 to 30 | 50 to 110 |
| Cryo Warden | 26 | 160 | 18 to 40 | 70 to 150 |
| Stasis Horror (boss, announced) | 32 | 420 | 80 to 160 | 300 to 600 |

### Breach Flood (grade 16+, value ×1.3)

*Seawater from somewhere that isn't here, leaking through the hull.*

| Fish | Rarity | Base value | Weight |
| --- | --- | --- | --- |
| Vacuum Sprat | Common | 6 | 0.1 to 0.5 kg |
| Hullsucker | Common | 7 | 0.5 to 2 kg |
| Static Goby | Common | 8 | 0.6 to 2.4 kg |
| Starlight Tetra | Uncommon | 20 | 0.4 to 2 kg |
| Void Carp | Uncommon | 22 | 2 to 8 kg |
| Event Horizon Angler | Rare | 60 | 4 to 14 kg |
| Nebula Koi | Epic | 200 | 5 to 18 kg |
| The Thing Outside | Legendary | 1200 | 60 to 200 kg |

| Sea creature | Level | Health | Scales | Credits |
| --- | --- | --- | --- | --- |
| Void Lurker | 34 | 140 | 20 to 44 | 80 to 160 |
| Hull Breacher | 38 | 220 | 26 to 56 | 100 to 220 |
| The Thing Below (boss, announced) | 44 | 640 | 120 to 240 | 450 to 900 |

### Quench Tanks (grade 21+, value ×1.4)

*Where the foundry cools its castings. The water never stops boiling.*

| Fish | Rarity | Base value | Weight |
| --- | --- | --- | --- |
| Slagfish | Common | 6 | 0.3 to 1.2 kg |
| Ember Tetra | Common | 7 | 0.1 to 0.5 kg |
| Quench Loach | Common | 8 | 0.8 to 3 kg |
| Forgeback Snapper | Uncommon | 20 | 2 to 7 kg |
| Molten Arowana | Uncommon | 22 | 2.5 to 9 kg |
| Neutronium Barracuda | Rare | 60 | 8 to 30 kg |
| Sunforged Marlin | Epic | 200 | 10 to 40 kg |
| The Foundry Heart | Legendary | 1200 | 80 to 260 kg |

| Sea creature | Level | Health | Scales | Credits |
| --- | --- | --- | --- | --- |
| Slag Ooze | 46 | 260 | 30 to 64 | 120 to 260 |
| Cinder Knight | 50 | 340 | 40 to 80 | 160 to 320 |
| Foundry Titan (boss, announced) | 60 | 900 | 180 to 360 | 700 to 1400 |

## Your rod

Every player gets one **soulbound rod** on their first visit. `/rod` gives it back if you lose track of it.
**Shift + left-click** or `/rod` opens the **rod bay**.

### Rod tiers

| Tier | Fishing level | Cost | Enchant cap | Lure slots |
| --- | --- | --- | --- | --- |
| Bent Wire Rod | 1 | Free | 10 | 1 |
| Scrap Spinner | 8 | 800 Scales | 20 | 1 |
| Carbon Line | 18 | 6,000 Scales | 35 | 2 |
| Mag-Rail Rod | 30 | 30,000 Scales | 50 | 2 |
| Void Angler | 42 | 140,000 Scales | 75 | 3 |

### Rod enchants (Scales)

Cost of level N = base × 1.09^(N−1) Scales.

| Enchant | Per level | Level 1 cost |
| --- | --- | --- |
| Lure Speed | Bites 1% sooner | 8 |
| Double Catch | +0.4% chance to land two fish | 15 |
| Angler's Eye | Rarer fish bite more often (+1.2%) | 14 |
| Treasure Luck | More treasure, less junk (+3%) | 12 |
| Chum Sense | Sea creatures surface more often (+2%) | 10 |
| Steady Hands | Wider reel-in zone | 9 |
| Line Strength | Progress drains slower (+0.8%) | 9 |
| Harpoon | +0.5 damage to sea creatures | 10 |
| Fillet | Fish sell for +1% more | 12 |

### Lures

Lures are items from treasure and sea creatures. Slot them into the rod (they're tradeable while not slotted).

| Lure | Rarity | Effect |
| --- | --- | --- |
| Rattle Spinner | Uncommon | Bites 12% faster |
| Salvage Hook | Uncommon | Much more treasure |
| Kevlar Leader | Uncommon | Progress drains 25% slower |
| Deep Hook | Rare | Rarer fish more often (+18%) |
| Siren Lure | Rare | Many more sea creatures |
| Gyro Bobber | Rare | Wider catch zone |
| Twin Hook | Epic | +8% double catch |
| Golden Rivet | Legendary | +15% value, rarer fish, more treasure |

### Bait

Bought in the bait shop for Credits (the price grows with your grade). One is used per catch; a pack is 16.

| Bait | Price at grade 1 | Effect |
| --- | --- | --- |
| Grub Paste | 30 | Bites 20% faster |
| Coolant Gel | 45 | Wider zone, slower drain |
| Chum Canister | 70 | Many more sea creatures |
| Magnet Bits | 70 | Much more treasure |
| Glow Worms | 90 | Rarer fish more often (+25%) |

## Fishing level and collections

- **Fishing level 1 to 50.** XP per catch by rarity (see above). Level N needs 60 × 1.13^(N−1) XP; level 50 takes
  about 208,000 XP in total. Each level adds **+1% fish value** and unlocks rod tiers.
- **Collections:** every species has tiers at 1, 25, 100, 500 and 2,000 catches (legendaries: 1, 3, 10, 25 and 60).
  Each tier adds +5% value to that species and pays 5, 25, 100, 400 and 1,500 Scales.
- **Records:** personal bests and a station record per species.
- **Weekly biggest catch:** the top 3 by weight win 600, 300 and 150 Scales every Monday 00:00 UTC.

## How it ties into progression

Fishing is a full alternative income: fish sell for real Credits that count as server income, and the Fish Market
pays more as your grade rises. It also feeds Tokens (catch finds and Token Pouches), modules (Sealed Module Cases), daily and weekly
[contracts](quests.md) and [gang contracts](gangs.md) (fish caught, sea creatures slain).
