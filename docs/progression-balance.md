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
| Prestige payday | `prestige.rewards` (Tokens grow ×1.35 per prestige, plus Shards and optional console commands) |
| How often players must `/sell` | `cargo.base-capacity`, `cargo.capacity-growth` |

## Prestige

Prestige is the main bulk source of Tokens (rare drops while mining are a trickle). Grade, Credits and
cargo contents reset. The drill, Tokens, Shards and cargo upgrades are kept. Each prestige to level P pays:

- **Tokens** = `cache × earnings multiplier`
  - cache = `25,000 × 1.35^(P-1)` (P1 25K, P5 76K, P10 375K, P20 7.6M)
  - earnings multiplier = `min(4, earned ÷ run cost)^0.5`
  - earned = all Credits **the server paid you** since the last prestige: selling ore now, and later
    workshop goods, fishing, contracts and so on. Money from other players (pay, trades, auction sales,
    gang bank withdrawals) never counts, so parking money with an alt, in items or in a bank before
    prestiging can't raise the payout. The wallet is wiped either way.
  - run cost = Credits for all 24 rank-ups of the run just finished (66M on the first run).
  - A normal run earns about its own run cost, so the multiplier is about 1.0. Grinding at grade 25
    until you've earned 2× gives 1.41×, and 4× (the cap) gives 2×. The square root means each extra hour
    pays less than the last.
- **Shards** = `10 + 2P`, and **+10% sell bonus** forever.

### Rule for other modules

When a module pays a player Credits **from the server**, call `EconomyService.give(..., reason)` with a
reason starting `income:` (for example `income:fishing`, `income:workshop`). Selling already uses `sell:`.
Player-to-player transfers must use any other reason (`pay`, `trade`, `auction`, `gang-bank`). The list is
`prestige.rewards.income-reasons` in `progression.yml`. Later systems that store value (auction house,
gang bank) should also lock deposits and withdrawals briefly around a prestige.
