# In-game guide (`guide/`, `guide.yml`)

Onboarding for every system, vanilla client only.

- **`/guide`** (`/help`, `/tutorial`, `/tutorials`, `/info`): a chest menu with one chapter per system. Left-click
  reads the chapter as a written book (clickable commands, a link to its wiki page), right-click starts its checklist.
  `/guide <chapter>` opens one directly. `/guide bar|tips|skip|restart <chapter>|wiki`.
- **First-visit briefings**: each chapter has `triggers`. The first time one fires for a player they get the
  chapter's title (if any) and a chat briefing with [Read the guide] / [Wiki] / [All guides] buttons. Popups are
  spaced `popup-gap-seconds` apart so several at once don't pile up.
- **Checklists**: chapters with `steps` run a checklist on a boss bar (the most recently started one is shown).
  Each step prints its hint lines in chat when it becomes current. Steps listen to the same events as triggers.
- **Info boards**: floating text + a clickable ⓘ (Interaction entity) that opens the chapter. Fixed boards in the
  station wings (`boards.fixed`), boards in front of the fishing hub and Undercroft spawns (`boards.world-spawns`),
  a text board above every mine's landing pad (`boards.mines`), and a per-owner "first line" board inside each
  workshop bay that disappears once the workshop checklist is done. Boards are non-persistent entities tagged
  `spacepunk_guide`; a 5 s ticker respawns any that are missing in loaded chunks, so moving one is a config edit.
- **Events**: `join`, `world:<name>`, `warp:<id or glob>`, `command:/a,/b`, `menu:<part of the menu holder's class
  name>`, `objective:<type>` (every quest objective report, via `ObjectiveProgressEvent`), `grade:<n>`,
  `prestige:<n>`, `region:<world>:<x1>:<z1>:<x2>:<z2>`, `guide`.
- Player state (seen, read, running/finished checklists, toggles) is JSON in profile module data `guide`.

## Content and config

`guide.yml` is read from the jar, so text updates ship with the plugin (servers kept stale copies of other configs).
To override something on one server, create `plugins/SpacepunkPrison/guide.yml` with only those keys (for example
`settings.wiki-url` once the wiki has a public address) and run `/guideadmin reload`.

When you add or change a system, update its chapter here as well as its wiki page.

## Admin

- `/guideadmin board add <chapter>` places a board at your feet (saved to `guide-boards.yml`), `board remove` deletes
  the nearest placed one, `board list` lists all fixed boards.
- `/guideadmin reset <player> [chapter]` makes briefings fire again; `/guideadmin show <player> <chapter>` plays a
  briefing and starts its checklist (testing).
- `/guideadmin reload`.

The station board positions in `guide.yml` were picked from the hub layout (`station/HubLayout.java`); if one sits
somewhere awkward, move it in the config or place a replacement with `/guideadmin board add`.
