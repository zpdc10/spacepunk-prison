# Direct trading

Trade face to face with another player: items, <span class="cur credits">Credits</span>, or both, in one window.
Use it for modules, parts, relics, fish, workshop goods, gun parts, or to pay a crewmate for something. For selling to
anyone on the station, use the [Black Market](black-market.md) instead.

## Commands

| Command | What it does |
| --- | --- |
| `/trade <player>` (`/tr`) | Ask a player to trade. If they already asked you, the window opens straight away |
| `/trade accept <player>` | Accept a request (or click **[ACCEPT]** in chat) |
| `/trade deny <player>` | Turn a request down (or click **[DENY]**) |
| `/trade history` | Your last 10 trades |
| `/trade locks` | Your prestige trade locks, if any |
| `/trade toggle` | Stop or allow trade requests |

A request lasts **60 seconds**. You can trade with anyone online, wherever they are on the station.

## The trade window

Your offer is on the **left**, theirs on the **right**.

1. **Put items up:** click an item in your own inventory. Left-click moves the whole stack, right-click moves one.
   Up to **16 stacks** each.
2. **Take items back:** click them on your side of the window.
3. **Offer Credits:** click the gold nugget, then type the amount in chat (`500`, `12.5k`, `3M`, `all`, or `0` to
   clear). The window comes back after you type. You can't offer more than you have.
4. **Accept:** click the accept button when you're happy. When **both** players accept, a **5 second countdown**
   starts in the middle column.
5. Any change by either player during that time (an item moved, Credits changed) **resets both accepts**, so nobody
   can swap something at the last second.

When the countdown ends, everything changes hands at once. The trade is refused if either player no longer has the
Credits they offered or doesn't have room for what they're getting.

Closing the window, logging out, dying or pressing **Cancel** ends the trade, and everything goes back to its owner.
If the server restarts mid-trade, your items are handed back the next time you join.

## What you can't trade

- Your **drill** and your **fishing rod** (soulbound), or a shulker box or bundle holding either.
- A few admin blocks (bedrock, barriers, command blocks).

## Prestige locks

Prestige wipes Credits. These rules stop players from parking Credits with a friend and getting them back after:

- **From Grade 21** (Core Foundry) you can't put Credits into a trade until you prestige. Items are fine. This is the
  same point at which the gang bank stops taking your deposits.
- Credits you send someone are tracked for your current run. When you prestige, that player **can't send Credits
  back to you for 30 days** (or until they prestige themselves). Paying each other back during the same run cancels
  out.
- You **can't prestige** with a trade window open.

Check yours with `/trade locks`. The Credits button in the window turns grey and says why when a lock applies.

## How it ties into progression

- Traded Credits are **player money**, not server income. They never count toward gang points or "earn Credits"
  quest goals.
- There's no fee or tax on direct trades.
- Every trade is logged, so staff can look into scams or mistakes.
