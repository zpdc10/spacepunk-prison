# For admins

Admin commands need the `spacepunk.admin` permission (ops have it). Every system's numbers live in its own config
file in `plugins/SpacepunkPrison/` and reload without a restart.

| System | Config file | Admin command |
| --- | --- | --- |
| Map protection | | `/buildmode` (`/build`): toggle editing protected blocks |
| Economy | | `/eco` |
| Mines | `mines.yml` | `/mineadmin` (`/madmin`) `generate [all/ID] / list / info / define / create / setspawn / remove / reset / reload` |
| Progression | `progression.yml` | `/progression reload` and admin tools |
| Drill | `drill.yml` | `/drill admin give/module/frame/level/heat/reset <player> ...`, `/drill admin reload` |
| Rare drops | `raredrops.yml` | `/salvage give/roll/reset/reload` |
| Black Market | `auction.yml` | in `/ah` |
| Station hub | `station.yml` | `/stationadmin` (`/sadmin`) `build / setspawn / setwarp ID [category] [name] / delwarp / warps / reload` |
| HUD | `hud.yml` | `/hud reload` |
| Quests | `quests.yml` | `/questadmin` (`/qadmin`) `npc / reset / complete / points / reroll / info / types / reload` |
| Parkour | `parkour.yml` | `/parkouradmin` (`/pkadmin`) `generate [all/MINE] / clear all/MINE / reload` |
| Fishing | `fishing.yml` | `/fishadmin build / reload / resetweek / give <player> fish/lure/bait/scales/xp/rod ...` |
| Workshop | `workshop.yml` | `/wsadmin reload / give / conveyors / goods / tp` |
| Gangs | `gangs.yml` | `/gang admin reload / disband / setbank / addpoints / weeklyreset / territory` |
| Undercroft | `undercroft.yml` | `/undercroft admin generate / regrow / reload / tp / drop [ring] / give / lab` |
| Guns | `guns.yml` | `/gun admin give/ammo/parts <player> ... / reload` |
| Resource pack | `resourcepack.yml` | `/resourcepack reload` |
| In-game guide | `guide.yml` (ships in the jar; a server copy only overrides keys) | `/guideadmin board add CHAPTER / board remove / board list / reset PLAYER [CHAPTER] / show PLAYER CHAPTER / reload` |

## Fresh server setup

1. Start the server once. The station hub, fishing waters, Undercroft and workshop world build themselves.
2. `/mineadmin generate all` builds all 25 mines.
3. `/parkouradmin generate all` builds a maintenance run for each mine.
4. The quest characters place themselves on the Crew Deck.

## This wiki

The wiki is built from the `wiki/` folder of the plugin repository with MkDocs Material. Update the page for a system
in the same change that alters it, so the wiki always matches the configs.
