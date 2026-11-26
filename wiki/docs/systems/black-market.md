# The Black Market

The Black Market is the station's player auction house. Sell modules, relics, workshop goods, fish, lures and anything
else you can hold to other players for <span class="cur credits">Credits</span>, either as buy-it-now listings or as
timed auctions. To swap with one player directly, use [direct trading](trading.md).

## Commands

| Command | What it does |
| --- | --- |
| `/ah` (`/auction`, `/blackmarket`, `/bm`) | Browse listings |
| `/ah sell <price> [hours]` | List the item in your hand at a fixed buy-it-now price |
| `/ah auction <start bid> [hours]` | Start a timed auction for the item in your hand |
| `/ah bid ...` | Bid on an auction (you can also bid from the menu) |
| `/ah mine` (`stalls`, `listings`) | Your active listings |
| `/ah claims` (`claim`, `collect`) | Collect items you bought, unsold items and sale proceeds |

The Exchange has **Black Market** and **Auction Claims** terminals.

## Listing

- Lengths: **6, 12, 24 or 48 hours**. Buy-it-now defaults to 24 hours, auctions to 12.
- **Listing fee**, paid up front and never refunded: 1% of the price, times 0.75 (6h), 1.0 (12h), 1.5 (24h) or 2.5
  (48h), at least 25 Credits.
- **Sales tax:** 5% of every sale is taken from the seller's proceeds.
- Prices from 10 Credits up.
- **Stalls** (listings at once): 3, plus 1 per prestige level, up to 14.
- You can't list new items while 45 or more are waiting in your claims.
- Soulbound items (your drill, your rod) and a few admin blocks can't be listed.

## Bidding

- A new bid must beat the top bid by **5%** (at least 10 Credits).
- **No sniping:** a bid in the last 60 seconds pushes the end out to 60 seconds from now.
- If you're outbid, your Credits come back to you.

## Prestige lock

From **Grade 21** (Core Foundry) you can't buy or bid until you prestige. You can still list and sell. Prestige
wipes Credits, and this stops anyone from overpaying for a friend's junk listing to park Credits with them first.
It's the same grade as the gang bank and [trading](trading.md) locks. You also can't prestige while you're the top
bidder on an auction.

## Big sales

Any sale of **1,000,000 Credits** or more is announced to the whole server.

## How it ties into progression

- It's where rare finds turn into Credits: modules, relics, blueprints, lures, legendary fish trophies, workshop
  components and gun parts.
- Fees and tax take Credits out of the economy, which keeps prices from inflating.
- Auction money is **player money**, not server income: it doesn't count toward gang points or "earn Credits" quest
  goals, so you can't launder Credits around a prestige.
- Selling your first item completes the "The Exchange" tutorial.
