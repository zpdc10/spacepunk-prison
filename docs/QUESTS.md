# Quests and maintenance runs

## Quests (`quests/`, `quests.yml`)

- **Story, "The Warden's Ledger"**: 5 chapters (one per deck, unlocking at grades 1, 6, 11, 16, 21), 32 steps told by
  station characters (Foreman Rusk, Tech Vell, Archivist Nhem, Kade, the Warden). One step is active at a time; the
  finale asks for your first prestige. Progress is kept forever, also through prestige.
- **Tutorials**: one per system, started automatically when unlocked. Tutorials for systems that aren't installed
  (fishing, workshop, auction, rare drops, Undercroft) appear as soon as that module registers its objective type.
- **Contracts**: 3 dailies (reset at midnight) and 3 weeklies (reset Monday) rolled from pools that scale with grade;
  finishing all dailies pays a bonus. Contracts give points for the monthly **Contract Track** (30 tiers, resets on
  the 1st, claimed in `/quests track`).
- Rewards resolve when a quest starts; Credits use `income:quest` / `income:contract`, so they count toward the
  prestige "earned this run" payout.
- Deliver objectives take ore from the cargo hold when the player clicks the quest in the log.
- `/quests [story|tutorials|contracts|track]`, `/contracts`. The tracked quest shows on the sidebar (click a quest
  in the log to track it).
- Admin: `/questadmin npc <character|board|remove>` places villager NPCs (right-click opens the log),
  `reset`, `complete`, `points`, `reroll`, `info`, `types`, `reload`.

### Plugging in a new system

```java
// in your module's enable():
Objectives.register(ObjectiveTypes.CATCH_FISH, "Catch fish");
// whenever it happens:
Objectives.report(player, ObjectiveTypes.CATCH_FISH, 1, fishItemId, "rarity:" + rarity);
```

Then add tutorials or contract pool entries with `type: catch_fish` to `quests.yml` (examples are already there).
Gang contracts can count finished quests and contracts through the gang objective `quests_done`.

## Maintenance runs (`parkour/`, `parkour.yml`)

- After `/mineadmin generate`, run `/parkouradmin generate` (or `generate MINE`) to build one course per mine in the
  void just north of its spawn rim, joined to the rim through a gap in the parapet.
- Courses snake east-west and climb; deck 1 is wide platforms and short gaps, deck 5 is single blocks, trapdoors,
  diagonals and full sprint jumps. Length grows from 14 jumps (grade 1) to 38 (grade 25). All jumps stay within
  vanilla limits. Checkpoints every 5-7 jumps; falling puts you back on the last one.
- Step on the iron plate to start; the clock shows on the action bar; the gold plates are checkpoints and the finish.
  One reward roll per course per day (table per deck in `parkour.yml`, with rare Token caches, modules and Shards);
  personal bests and course records are kept. Finishing a rewarded run counts for quests and gang contracts
  (`parkour_runs`).
- `/parkour` (menu: status, best times, click to go), `/parkour leave`, `/parkour cp`, `/parkour go MINE`.
  Each run is also a station warp `parkour-<mine>`.
- `/parkouradmin clear all|MINE` removes courses and restores the rim.
