# Workshop (hull bays)

Every player gets a private hull bay in the `workshop` void world (one plot per player on a grid, 128 blocks
apart). Inside it they place machines that turn mined ore into goods worth more than raw ore, and decorate
freely. Package `workshop/`, config `workshop.yml`, art `tools/resourcepack/workshop_art.py`.

## Getting there

- `/ws` (aliases `/workshop`, `/bay`, `/hullbay`) claims and builds the bay on first use, then teleports in.
- The station's Workshop Airlock uses the warp `workshop` (registered with `WarpService`, category `private`),
  which builds the bay on the spot if needed.
- Right-click the lodestone **terminal** in the north wall for the workshop menu (`/ws menu`).

## The loop

1. **Cargo Intake** pulls ore from the cargo hold (or the Bay Locker) and pushes it out its front.
2. **Jaw Crusher** → grit (+30% chance of Slag Dust to the locker).
3. **Sludge Washer** → concentrate (needs coolant from a **Coolant Pump**; 15% Toxic Sludge byproduct).
4. **Arc Furnace** → refined goods (ingots, lenses, wafers...). Very hot.
5. **Alloy Forge** → alloys from two refined goods (needs coolant).
6. **Fabricator** → components (wiring harness, armor plate, circuit board, induction coil, firing mechanism,
   weapon receiver, cryo coil, focusing array, void capacitor, reactor core, fuel cell).
7. **Depot Hatch** stores whatever reaches it in the Bay Locker; **Trade Chute** sells it at 90% of the market.

Machines push out of their front (the way the player faced when placing; rotate in the machine menu). The
block in front can be another machine, or a **conveyor** line (restyled repeaters) that ends at a machine.

**Power:** Slag Burners burn fuel (slag dust, carbon, fuel cells; from their input or straight from the
locker) only as much as the bay needs. Short on power = every machine runs slower (brownout).
**Coolant:** pumps share coolant with every washer and forge; short = they slow down.
**Heat:** running machines heat up, +35% per running neighbour; all shed 3/s; a touching Heat Sink sheds
4/s more. At 100 the machine jams until it cools to 25. An Arc Furnace (6/s) overheats alone without a sink.

**Marks:** Mk II (6x base cost) and Mk III (30x + 4 wiring harness + 2 armor plate) are faster and roll
quality on smelt/alloy/fabricate: Refined (x1.35 price) and Pristine (x1.9). Better inputs raise the roll.

## Prices and the market

Goods prices are derived, so tuning is in one place: each family has a `value` (Credits per crushed unit),
each ore yields `sell price / value` crushed units on average, and every step multiplies by its machine's
`markup` (crush 1.15, wash 1.17, smelt 1.33, alloy 1.25, fabricate 1.3). Copper scrap (6) becomes an ingot
worth about 10.7; a full copper+steel to wiring harness chain is about 2.9x the raw ore value.

The Trade Terminal market is shared by the whole server: daily demand x0.7-1.4 per good (seeded by date),
three priority orders at x1.5, and saturation (price falls as the server sells more of a good that day:
`1 / (1 + Credits sold today / 3M)`, floor 40%). The seller's grade/prestige sell multiplier applies.
All workshop payouts use the economy reason `income:workshop`, so they count toward the prestige Token payout.

Throughput is the brake on the pace model (`progression-balance.md`): a Mk I crusher handles 1 ore/s, about
the mining rate of an early player, and every extra stage needs another machine, power and slots. Expect the
workshop to add roughly 30-60% to a run's income once a full chain is running, which shortens the first
prestige toward the low end of the 50-100h target. Tune `markup`, `step-time` and machine costs if it runs hot.

## Bays

| Level | Size | Height | Machines | Locker | Cost | Grade |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 11x11 | 6 | 6 | 2K | free | 1 |
| 1 | 15x15 | 7 | 10 | 5K | 30K | 3 |
| 2 | 21x21 | 8 | 16 | 12K | 400K | 8 |
| 3 | 27x27 | 9 | 24 | 30K | 5M + 10 Shards | 12 |
| 4 | 35x35 | 10 | 34 | 75K | 60M + 40 Shards | 18 |
| 5 | 45x45 | 12 | 48 | 200K | 400M + 120 Shards | 22 |

Bays are private by default. `/ws open|close` lets visitors in; `/ws crew add|remove <player>` lets someone
build and use machines. Bays and machines survive prestige.

Bays tick every second while the owner is online. On login a bay catches up on up to 4 hours away at 50%.

## Integration

- `ProgressionService.cargoWithdraw` / `cargoContents` for intakes; `sellMultiplier` for payouts.
- `GangService`: contract objective `goods_processed` (units finished by machines).
- Assembly Bench: components + Credits → drill modules via `drill admin module {player} <id> 1` (console).
- `WorkshopService` (api): locker read/take/give, goods items for trading/auctions, `itemId(ItemStack)`,
  bay location. Gun parts for the guns module: `spacepunk:firing_mechanism`, `spacepunk:weapon_receiver`,
  `spacepunk:focusing_array`, `spacepunk:armor_plate`.

## Data

- Per player: `moduleData("workshop")` (machines, buffers, locker, conveyors, purchases).
- Global: `plugins/SpacepunkPrison/workshop/plots.json` (owner, size, open, crew) and `market.json`
  (today's sales). The bay blocks and machine displays live in the `workshop` world.

## Admin

`/wsadmin reload | give <player> <machine> [mark] [amount] | conveyors <player> [n] | goods <player> <good> <n> | tp <player>`.
If machine models face backwards in game, flip `display.yaw-offset` (0 or 180) and `/wsadmin reload`
(existing displays update when rotated or re-placed).
