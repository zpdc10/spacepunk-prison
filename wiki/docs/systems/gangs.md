# Gangs (crews)

Gangs are crews of players with a shared bank, upgrades, weekly contracts, crew chat and turf in the Undercroft. They
unlock at **Prestige 1**.

## Commands

| Command | What it does |
| --- | --- |
| `/gang` (`/crew`, `/gangs`) | Open the crew terminal |
| `/gc <message>` (`/crewchat`) | Send a message to your crew |
| `/gang chat` | Toggle sending all your chat to the crew |
| `/gang color <name>` | Leader: recolor the gang tag (rust, oil, hazard, toxic, coolant, plasma, blood, chrome) |

The crew terminal shows your gang's level, weekly rank, upkeep and turf, the bank, upgrades, weekly contracts with
progress bars, the roster and the bank log. The Crew Deck has a **Gang** terminal.

## Founding and joining

- You need **Prestige 1** to found or join a gang.
- Founding costs **25,000 Credits**. Names are 3 to 16 characters, tags 2 to 5.
- Invites last 5 minutes.
- A gang starts with **6 member slots**.

## Ranks

| Rank | Can |
| --- | --- |
| Leader | Everything: make officers, transfer leadership, recolor, disband |
| Officer | Invite, kick lower ranks, withdraw from the bank, buy upgrades, set the message of the day, promote recruits |
| Member | Deposit, chat, contribute |
| Recruit | Deposit, chat, contribute |

Gangmates **can't hurt each other**, with fists or guns.

## Gang points and levels

Points come from everything the crew does:

| Source | Points |
| --- | --- |
| Mining | 0.05 per block |
| Server income | 1 per 20,000 Credits a member earns from the server |
| Rank-up | 25 |
| Prestige | 2,500 |
| Weekly contracts | 1,500 to 3,000 each |
| Capturing an Undercroft lab | 500 |
| Undercroft kills that drop a bounty tag | 15 × the ring number |

**Lifetime points** raise the gang level (levels 2 to 10 need 2.5K, 10K, 30K, 75K, 160K, 300K, 520K, 850K and 1.3M),
which unlocks upgrade levels. **Weekly points** rank gangs on the leaderboard. At the weekly reset (Monday 00:00) the
top 3 gangs get **2M, 1M and 500K** bank Credits, and every gang gets new contracts.

## The bank and the prestige lock

The gang bank holds Credits for upgrades and upkeep (5M to start). Because prestige wipes Credits, the bank can't be
a place to hide them:

1. Credits you deposit are **parked** under your name for the rest of your run.
2. From **grade 21** you can't deposit, and nobody else can withdraw what you parked. You can always take your own
   parked Credits back.
3. When you **prestige**, whatever you still have parked is wiped from the bank.
4. Spending (upgrades, upkeep) uses unparked money first, then everyone's parked Credits in proportion, so your prestige
   never wipes another member's money. Contract, weekly and lab payouts are never parked.
5. If you leave the gang, your parked Credits stay locked for 14 days, then become gang money.

Minimum deposit is 100 Credits. Bank moves are player money, not server income.

## Upgrades

Bought with bank Credits by officers. Cost of level L = base × growth^(L−1).

| Upgrade | Effect per level | Levels | Level 1 cost | Growth | Gang level needed |
| --- | --- | --- | --- | --- | --- |
| Bunks | +2 member slots | 5 | 400K | ×2.2 | 1, 2, 3, 5, 7 |
| Strongbox | +100% bank capacity | 5 | 250K | ×2.5 | 1, 2, 4, 6, 8 |
| Fence Network | +2% ore sell price for every member | 5 | 750K | ×2.4 | 2, 3, 5, 7, 9 |
| Dispatch Board | +1 weekly contract | 2 | 600K | ×3.0 | 3, 6 |

**Upkeep:** every 24 hours the bank pays **2,500 per member + 5,000 per upgrade level**. If it can't pay, gang perks
(like the sell bonus) switch off until it can.

## Weekly gang contracts

Three contracts a week (more with Dispatch Board). Targets grow with the number of members.

| Contract | Goal | Target | Points | Bank Credits |
| --- | --- | --- | --- | --- |
| Strip the Seams | Blocks mined | 50K + 25K per member | 2,000 | 300K |
| Full Holds | Ore sold | 150K + 60K per member | 1,500 | 250K |
| Payday | Credits earned from the server | 5M + 2.5M per member | 2,000 | 400K |
| Clearance Drive | Rank-ups | 6 + 3 per member | 1,500 | 200K |
| Sludge Harvest | Fish caught | 300 + 120 per member | 1,500 | 250K |
| Factory Floor | Workshop goods processed | 2,000 + 800 per member | 2,000 | 350K |
| Blood in the Hull | Undercroft kills | 15 + 5 per member | 3,000 | 500K |
| Through the Checkpoint | Contraband smuggled out | 40 + 15 per member | 3,000 | 500K |

Finished quests, contracts and maintenance runs can also count.

## Undercroft turf

Each ring of the [Undercroft](undercroft.md) has a drug lab your gang can seize: the **Bilge Still** (ring 1),
**Rustworks** (ring 2) and the **Sump Kitchen** (ring 3).

- **Capture:** hold the area within 6 blocks of the lab with no other gang there for **60 seconds**. Another gang
  nearby makes it contested and the progress holds; leaving lets it decay. The current holder is warned.
- **Hourly income** to your bank: **40K, 90K or 180K** by ring.
- **A cut of every batch** someone else cooks there: 25% of the doses, paid as 12,000 bank Credits per dose. Your own
  members keep their full batch.

## How it ties into progression

The Fence Network is one of the few sell bonuses you can stack early in a run, lab income and weekly rewards are
big bank payouts, and gang contracts reward a crew for doing what it already does.
