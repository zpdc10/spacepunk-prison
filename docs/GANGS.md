# Gangs (crews)

Package `gangs/`, config `gangs.yml`, state `plugins/SpacepunkPrison/gangs-data.json`.

## Players

- Unlock at Prestige 1 (`create-prestige`, `join-prestige`). Founding costs 25K Credits.
- `/gang` opens the crew terminal: header (level, weekly rank, upkeep, turf), bank, 4 upgrades, weekly
  contracts with progress bars, roster with heads, crew chat toggle, bank log.
- Ranks: Leader > Officer > Member > Recruit. Officers invite, kick lower ranks, withdraw, buy upgrades,
  promote recruits to members. The leader also makes officers, transfers, recolors, disbands.
- `/gc <msg>` or `/gang chat` (toggle) for crew chat. Gangmates can't damage each other anywhere.
- Gang points: mining (0.05/block), server income (1 per 20K Credits), rank-ups, prestiges, contracts.
  Lifetime points give gang levels (gate upgrades); weekly points drive the leaderboard. At the weekly reset
  (Monday 00:00) the top 3 crews get bank Credits and every crew gets new contracts.

## Bank and the prestige lock

Prestige wipes Credits, so the bank must not be a hiding place:

1. Credits you deposit are **parked** under your name for the rest of your run.
2. From Grade 21 (`bank.deposit-lock-grade`) you can't deposit, and nobody else can withdraw what you parked.
   You can always take your own parked Credits back.
3. When you prestige, whatever you still have parked is wiped from the bank.
4. Spending (upgrades, upkeep) uses unparked money first, then everyone's parked Credits pro rata, so the
   wipe never takes other members' money. Contract, weekly and lab payouts are never parked.
5. Leaving the gang keeps your parked Credits locked for 14 days (`ex-member-release-days`), then they become
   gang money; prestiging in that time still wipes them.

Bank moves use EconomyService reasons `gang:deposit` / `gang:withdraw`, which never count toward a run's
server income ("earned this run").

## Upgrades (bank Credits)

| Id | Effect | Levels |
| --- | --- | --- |
| `bunks` | +2 member slots (base 6) | 5 |
| `vault` | +100% bank capacity (base 5M) | 5 |
| `fence` | +2% sell price for every member (OreSellEvent) | 5 |
| `dispatch` | +1 weekly contract (base 3) | 2 |

Upkeep: 2.5K per member + 5K per upgrade level, every 24h. If the bank can't pay, perks (sell bonus) switch
off until it can.

## For other modules

```java
Services.find(GangService.class).ifPresent(g -> g.registerContractObjective("fish_caught", "Fish caught"));
Services.find(GangService.class).ifPresent(g -> g.addContractProgress(player.getUniqueId(), "fish_caught", 1));
```

## Undercroft turf

- Seize a drug lab (`bilge_still`, `rustworks`, `sump_kitchen`) by holding the area within 6 blocks of it with no
  other gang there for 60s. Another gang nearby makes it contested (progress holds); leaving lets it decay.
  The current holder is warned when someone starts taking it. Capturing gives 500 gang points.
- The holder's bank gets lab income every hour (40K / 90K / 180K by ring) and 12K Credits per dose of the
  25% cut from every batch someone else cooks there. The holder's own members keep their full batch.
- Undercroft kills that drop a bounty tag give 15 gang points x ring; bounty turn-ins add their points.
- Gangmates can't shoot each other (GunHitEvent) or hit each other.
- Ownership is mirrored into `UndercroftService#setLabController`, so `/undercroft admin lab` changes are adopted too.
  Numbers live under `turf:` in gangs.yml.

Admin: `/gang admin reload | disband <gang> | setbank <gang> <amount> | addpoints <gang> <n> | weeklyreset |
territory <id> <gang|none>`.
