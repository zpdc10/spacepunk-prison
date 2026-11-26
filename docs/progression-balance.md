# Progression balance

How the default numbers in `progression.yml` aim at **50 to 100 hours of active play from grade 1 to
the first prestige**. Every number below is a config value; tune after playtesting.

## The model

Time spent at grade g = cost(g) / income(g).

- **Cost** of going from grade g to g+1: `4,000 × 1.45^(g-1)` Credits (first prestige run). Later runs are
  multiplied by `1 + 0.5 × prestige`.
- **Income** per hour at grade g = average sell value of one block in that grade's mine × ore units
  mined per hour × sell multiplier.
  - Average block value in mine g: about `2 × 1.21^(g-1)` Credits. This comes from the prices in
    `progression.yml` combined with the block mix each mine uses in `mines.yml`. If the mines' weights
    drift from this, the pace drifts with it.
  - Ore units per hour: about `4,000 × 1.04^(g-1)`. That is a little over one block a second with
    hand mining at the start, rising as the drill gets Bore Speed, Yield and area enchants. This is the
    least certain number, because it depends on the drill's enchant costs and procs.
  - Sell multiplier: `1 + 0.02 × (grade-1) + 0.10 × prestige`.

Costs grow 45% per grade and income about 29%, so each grade takes about 12% longer than the one before:
about 30 minutes for the first rank-up and about 9 hours for the last.

| Grade to next | Cost | Assumed income/hour | Hours | Total hours |
| --- | --- | --- | --- | --- |
| 1 -> 2 | 4K | 8K | 0.5 | 0.5 |
| 2 -> 3 | 5.8K | 10.3K | 0.6 | 1.1 |
| 3 -> 4 | 8.41K | 13.2K | 0.6 | 1.7 |
| 4 -> 5 | 12.2K | 16.9K | 0.7 | 2.4 |
| 5 -> 6 | 17.7K | 21.7K | 0.8 | 3.2 |
| 6 -> 7 | 25.6K | 27.8K | 0.9 | 4.2 |
| 7 -> 8 | 37.2K | 35.6K | 1.0 | 5.2 |
| 8 -> 9 | 53.9K | 45.6K | 1.2 | 6.4 |
| 9 -> 10 | 78.2K | 58.4K | 1.3 | 7.7 |
| 10 -> 11 | 113K | 74.7K | 1.5 | 9.2 |
| 11 -> 12 | 164K | 95.6K | 1.7 | 11.0 |
| 12 -> 13 | 238K | 122K | 1.9 | 12.9 |
| 13 -> 14 | 346K | 156K | 2.2 | 15.1 |
| 14 -> 15 | 501K | 200K | 2.5 | 17.6 |
| 15 -> 16 | 726K | 256K | 2.8 | 20.5 |
| 16 -> 17 | 1.05M | 327K | 3.2 | 23.7 |
| 17 -> 18 | 1.53M | 418K | 3.7 | 27.4 |
| 18 -> 19 | 2.21M | 533K | 4.2 | 31.5 |
| 19 -> 20 | 3.21M | 681K | 4.7 | 36.2 |
| 20 -> 21 | 4.66M | 870K | 5.4 | 41.6 |
| 21 -> 22 | 6.75M | 1.11M | 6.1 | 47.6 |
| 22 -> 23 | 9.79M | 1.42M | 6.9 | 54.6 |
| 23 -> 24 | 14.2M | 1.81M | 7.8 | 62.4 |
| 24 -> 25 | 20.6M | 2.31M | 8.9 | 71.3 |

Total: **about 71 hours** to grade 25, inside the 50 to 100 hour target. Cargo upgrades and the
Auto-Seller also cost Credits, which adds a few hours.

## Knobs, by what they change

| To change | Edit |
| --- | --- |
| Total time to first prestige | `grades.base-cost` (scales every grade equally) |
| How much slower late grades feel than early ones | `grades.growth` |
| One specific grade | `grades.cost-overrides` |
| Later prestige runs | `prestige.cost-per-prestige`, `selling.bonus-per-prestige` |
| Prestige payday | `prestige.rewards` (Shards and optional console commands; no Tokens) |
| Token drop rates | `drill.yml` `tokens:` (chance, amounts, grade-growth, prestige-growth), `fishing.yml` / `workshop.yml` `tokens:` |
| How often players must `/sell` | `cargo.base-capacity`, `cargo.capacity-growth` |

## Prestige

Grade, Credits and cargo contents reset. The drill, Tokens, Shards and cargo upgrades are kept. Prestige
**pays no Tokens** (changed 2026-11-26; it used to pay a Token cache scaled by the run's earnings). Each
prestige to level P pays:

- **Shards** = `10 + 2P`, and **+10% sell bonus** forever.
- **+15% on every Token drop** forever (`drill.yml` `tokens.prestige-growth`, compounding: P5 x2.0, P10 x4.0).

## Tokens

Tokens are found while doing things: uncommon, but not rare. The target is that **a few hours in the mine
buys a couple of drill enchant upgrades**, at every stage of the game. Enchant costs grow about 10% per level
and the drill gets more expensive the deeper you are, so drops grow with the mine's grade.

**Mining** (`drill.yml` `tokens:`): each hand-broken block has a 0.6% chance to drop 4-7 Tokens (a find
about every 3 minutes at one block a second). Every mining drop (base find, Token Siphon, Salvage Sense
caches) is multiplied by `1.10^(mine grade - 1) x 1.15^prestige`. Blocks broken by area enchants roll at 5%
of the hand chance. Token Siphon adds 0.015% find chance per level (level 10 = +25% Tokens, cap +250%).

Assumes about 3,000 hand-broken blocks an hour (prestige 0):

| Mine grade | Tokens/hour | Per 3 hours | What 3 hours buys (typical next upgrades) |
| --- | --- | --- | --- |
| 1 | ~100 | ~300 | Several early levels (15-120 each on Rusted) |
| 5 | ~145 | ~435 | Shaped Charge 5 (91) + Overclock 5 (121) + Bore Speed levels |
| 10 | ~235 | ~700 | Bore Speed 30 (238) + Yield 25 (197), or toward Cutting Laser 1 (1.2K) |
| 15 | ~375 | ~1.1K | Cutting Laser 1, or a few mid levels |
| 20 | ~605 | ~1.8K | Cutting Laser 5 (1.9K) |
| 25 | ~975 | ~2.9K | Cutting Laser 10 (3.3K) / Salvage Sense levels |

Over the ~71 hour first run that is about **40K Tokens from mining** (1.6K by grade 10, 16K by grade 20):
enough to max the Rusted frame (~2K) early and get most of the way through Salvaged (~60K). Later runs are
longer and every drop is 15% bigger per prestige:

| Prestige run | Run length | Tokens from mining |
| --- | --- | --- |
| 0 -> 1 | ~71h | ~40K |
| 1 -> 2 | ~97h | ~62K |
| 5 -> 6 | ~166h | ~185K |
| 10 -> 11 | ~214h | ~480K |
| 15 -> 16 | ~242h | ~1.1M |
| 20 -> 21 | ~261h | ~2.4M |

That lines up with the frame gates: Industrial (P1, ~540K to max) by about P5, Military (P5, ~4M) by about
P15, and Prototype (P15, ~72M) as the long endgame.

**Other activities** add to mining (all scale with your grade and prestige unless noted):

- Fishing (`fishing.yml` `tokens:`): 3% per landed catch for 4-7 Tokens (~25/h at grade 1), plus Token Pouch
  treasure (20-50).
- Workshop (`workshop.yml` `tokens:`): while you're online and your bay is processing, a 6% roll each minute for
  4-7 Tokens (~20/h at grade 1).
- Parkour: one reward roll per course per day, 4-800 Tokens by deck (`parkour.yml`, not scaled).
- Quests and contracts: fixed or per-grade Token rewards (`quests.yml`).
- Rare drops: salvage caches and relic collections (`raredrops.yml`).

### Rule for other modules

When a module pays a player Credits **from the server**, call `EconomyService.give(..., reason)` with a
reason starting `income:` (for example `income:fishing`, `income:workshop`). Selling already uses `sell:`.
Player-to-player transfers must use any other reason (`pay`, `trade`, `auction`, `gang-bank`). The list is
`prestige.rewards.income-reasons` in `progression.yml`. Later systems that store value (auction house,
gang bank) should also lock deposits and withdrawals briefly around a prestige.
