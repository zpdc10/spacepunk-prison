# Currencies

The station runs on three main currencies, plus Scales for fishing. Each is earned a different way and spent on a
different thing, so no single grind beats the others.

| Currency | In short | Survives prestige? |
| --- | --- | --- |
| <span class="cur credits">¢ Credits</span> | Station scrip. Earned by selling and working, spent on grades and gear | **No**, wiped on prestige |
| <span class="cur tokens">✦ Tokens</span> | Drill upgrade currency, found while doing things | Yes |
| <span class="cur shards">◆ Prestige Shards</span> | The prestige reward. Spent on the biggest upgrades | Yes |
| <span class="cur scales">Scales</span> | Fishing upgrade material | Yes |

Your balances show on the sidebar and in the tab list footer. `/balance` (`/bal`) prints them.

## ¢ Credits

**What they are:** the everyday money of the station. Almost everything that pays you pays Credits.

**How you earn them**

- Selling ore from your cargo hold (`/sell`, sell terminals, or the Auto-Seller).
- Selling fish at the Fish Market, and killing sea creatures.
- Selling processed goods from your workshop (Trade Terminal or Trade Chute).
- Quest, contract and Contract Track rewards.
- Opening salvage caches dug up while mining.
- Daily parkour reward rolls.
- Selling hot ore, stims and bounty tags at the Undercroft fence.
- Other players: Black Market sales, direct trades (`/trade`) and gang bank withdrawals.

**What you spend them on:** clearance grades (`/rankup`), cargo hold upgrades, the Auto-Seller, the Salvaged drill
frame, workshop machines, conveyors and bay expansions, bait, guns and ammo, Smuggler's Linings and bribes,
founding a gang and paying into its bank, and Black Market purchases.

**Prestige wipes Credits.** Spend what you have before you prestige.

!!! info "Server income vs player money"
    Credits the **server** pays you (selling, fishing, workshop, quests, salvage caches, the fence, parkour) are
    "server income". Credits from **other players** (auction sales, direct trades, gang bank) are not. Only server
    income counts toward gang points and quest goals like "earn X Credits", so moving money between alts never
    helps.

## ✦ Tokens

**What they are:** the drill's upgrade currency. Every drill enchant level costs Tokens.

**How you earn them.** Tokens are uncommon drops from activities. They aren't rare: a few hours in a mine buys a
couple of drill upgrades. When you find some, the action bar flashes "✦ Token find +N" with a chime, and the
sidebar counts the Tokens you've earned this session.

| Source | Chance | Amount (before scaling) | Roughly per hour at grade 1 |
| --- | --- | --- | --- |
| Mining (hand-broken blocks) | 0.6% per block | 4 to 7 | about 100 (one find every ~3 minutes) |
| Fishing (every landed catch) | 3% per catch | 4 to 7 | about 25 |
| Workshop (bay processing while you're online) | 6% roll each minute | 4 to 7 | about 20 |

**Scaling.** Every activity find is multiplied by **1.10^(grade − 1)** (for mining, the grade of the mine you're in)
and by **1.15^prestige**. So the same hour of mining pays far more deeper down and after each prestige:

| Mine grade | Mining Tokens per hour (Prestige 0) |
| --- | --- |
| 1 | about 100 |
| 10 | about 235 |
| 15 | about 375 |
| 20 | about 605 |
| 25 | about 975 |

A whole first run (about 71 hours) mines roughly **40,000 Tokens**: enough to max the Rusted frame early and most of
the Salvaged frame. Later runs earn much more: about 62K at Prestige 1, 185K at P5, 480K at P10, 1.1M at P15 and 2.4M
at P20.

**Other sources:**

- **Token Siphon** (drill enchant): +0.015% find chance per level, so level 10 is about +25% Tokens, capped at +250%.
- **Token Condenser** module: Tokens +30%. The **Magnet Coil** module cuts mining Tokens by 70%.
- **Salvage Sense** Token caches while mining, scaled the same way.
- Fishing Token Pouch treasure (20 to 50, scaled).
- Quests, contracts and Contract Track tiers.
- Daily parkour rolls, with rare Token caches.
- Bigger salvage caches and completed relic sets (1,500 to 100,000).
- Undercroft Smuggled Tech Crates (2,000 to 8,000).

Workshop Tokens only drop while you're online; offline catch-up never pays Tokens.

**What you spend them on:** drill enchants (Bore Speed, Yield, Token Siphon, Shaped Charge, Jackhammer, Cutting
Laser, Overclock, Salvage Sense, Coolant System). See [The drill](systems/drill.md).

**Prestige doesn't pay Tokens**, but each prestige makes every Token find 15% bigger (×2 at P5, ×4 at P10).

## ◆ Prestige Shards

**What they are:** the reward for prestiging, used for the biggest permanent upgrades.

**How you earn them**

- **Prestige:** 10 + 2 × your new prestige level. That is 12 Shards at Prestige 1, 20 at P5, 30 at P10 and 40 at P15.
- **Story quests:** 1 and 2 Shards from late chapter steps, and 10 for finishing "The Warden's Ledger".
- **Contract Track milestones:** 2 Shards at 400 points, 3 at 800 and 5 at 1,200 every month.
- **Rare parkour rolls:** 3 Shards on Hull Breach runs, 5 on Core Foundry runs.

**What you spend them on**

| Upgrade | Shards | Also needs |
| --- | --- | --- |
| Industrial drill frame | 5 | Prestige 1 |
| Military drill frame | 30 | Prestige 5 |
| Prototype drill frame | 120 | Prestige 15 |
| Workshop bay level 3 | 10 | 5M Credits, grade 12 |
| Workshop bay level 4 | 40 | 60M Credits, grade 18 |
| Workshop bay level 5 | 120 | 400M Credits, grade 22 |

## Scales

**What they are:** fishing's own upgrade material.

**How you earn them:** gutting fish instead of selling them (1 for a common up to 100 for a legendary), sea creature
kills, Scale Cache treasure, fishing collection tiers, and the weekly biggest-catch board (600 / 300 / 150).

**What you spend them on:** rod tiers (up to 140,000 Scales for the Void Angler) and rod enchants. See
[Fishing](systems/fishing.md).
