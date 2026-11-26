# The drill

Every player gets one soulbound drill on first join. It can't be dropped, stored, framed or lost on death,
and it is kept through prestige. All numbers live in `drill.yml` (`/drill admin reload` after edits).

## Controls

- **Shift + right-click** the drill (or `/drill`): open the drill bay.
- **Q** with the drill in hand: vent heat.

## Four ways it grows

1. **Frames** (Rusted → Salvaged @ grade 10 → Industrial @ P1 → Military @ P5 → Prototype @ P15). Each frame
   raises every enchant's level cap, base speed and heat limit, and adds a module socket (0 to 4). Salvaged
   costs Credits; later frames cost Prestige Shards. A frame is kept forever.
2. **Token enchants**: Bore Speed, Yield, Token Siphon, Shaped Charge, Jackhammer, Cutting Laser, Overclock,
   Salvage Sense, plus the Coolant System. Cost grows exponentially per level. Left-click +1, right-click +10,
   shift-click max. Jackhammer, Cutting Laser and Salvage Sense are locked on the Rusted frame.
3. **Modules**: rare items found while mining (Salvage Sense raises the odds) or given by admins. Slot them in
   the drill bay. Each has an upside and a downside: Magnet Coil, Plasma Cutter, Scavenger Rig, Cryo Coolant Loop,
   Token Condenser, Seismic Dampener, Overcharger.
4. **Heat**: hand mining and heavy enchants heat the drill (gently on the first decks, harder deeper down).
   At the limit it stalls for a few seconds. Vent with Q any time above 30% for a speed burst; venting between
   75% and 95% is a perfect vent with double the burst. Coolant upgrades raise the limit and cool faster.

## Economy

Tokens drop while mining: uncommon finds (0.6% per hand-broken block, 4-7 Tokens) that grow x1.10 per mine grade
and x1.15 per prestige, so a few hours in the mine always buys a couple of enchant upgrades. About 100/h at grade 1,
230/h at grade 10, 600/h at grade 20, ~40K over a first run. Token Siphon adds extra find chance, Salvage Sense adds
caches; both scale the same way. Prestige pays no Tokens. Full model: `docs/progression-balance.md`. Rough cost to max every enchant: Rusted ~2K, Salvaged ~60K, Industrial ~540K, Military ~4M,
Prototype ~72M Tokens.

## Admin

`/drill admin give|module|frame|level|heat|reset <player> ...`, `/drill admin reload`. Permission `spacepunk.admin`.
Set `item.use-item-models: false` in drill.yml while the resource pack lacks the `spacepunk:*` models. Modules without pack art (token_condenser, seismic_dampener) use `module/unknown`.
