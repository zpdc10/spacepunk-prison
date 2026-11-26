# Guns

Guns are server-side weapons for the [Undercroft](undercroft.md). They **only fire in PvP zones**, and they drop on
death like any other gear.

## Commands

| Command | What it does |
| --- | --- |
| `/gunsmith` (`/guns`) | Buy guns and ammo |
| `/gun` | How guns work |
| `/rig gun <gun>` | Open that gun's Weapon Rig |

## Controls

| Action | How |
| --- | --- |
| Fire | **Right-click** (hold for the SMG's bursts) |
| Reload | **Q** or **F**, using ammo from your inventory |
| Aim | **Sneak** tightens spread; the Bolt Rifle and Plasma Lance zoom while sneaking |

- Jumping widens your spread. Recoil kicks your view up a little each shot.
- **Headshots** do extra damage. Damage **falls off** with range.
- The action bar shows loaded and spare ammo and a reload bar. The item's durability bar is the magazine.

## The guns

Damage is in hit points per pellet (a player has 20). Prices are at grade 1 and grow ×1.21 per grade.

| Gun | Role | Damage | Headshot | Magazine | Range | Ammo | Price |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rivet Pistol | Cheap, reliable sidearm | 4.5 | ×1.6 | 12 | 40 | Rivet Rounds | 15K |
| Scrap SMG | 3-round bursts, close range | 2.6 | ×1.4 | 30 | 30 | Rivet Rounds | 60K + 4 parts |
| Breach Shotgun | 8 pellets, brutal up close | 2.4 ×8 | ×1.3 | 6 | 18 | Breach Shells | 90K + 6 parts |
| Bolt Rifle | Scoped, slow, pierces one target | 13 | ×1.75 | 4 | 90 | Bolt Slugs | 150K + 10 parts |
| Plasma Lance | Accurate, pierces two (Prestige 1) | 8 | ×1.5 | 8 | 55 | Plasma Cells | 400K + 24 parts |

**Gun parts** come from Undercroft supply drops and Smuggled Tech Crates. The Bolt Rifle and Plasma Lance can also
drop from tech crates.

## Ammo

| Ammo | Per pack | Price at grade 1 |
| --- | --- | --- |
| Rivet Rounds | 24 | 300 |
| Breach Shells | 12 | 500 |
| Bolt Slugs | 10 | 700 |
| Plasma Cells | 8 | 1,500 |

## Weapon Rigs

Every gun **model** has its own **Weapon Rig**, a module grid that works like the drill's
[Module Rig](drill.md#3-modules-and-the-module-rig). The rig belongs to you, not to the item: every Rivet Pistol you
carry uses your Rivet Pistol rig, so losing a gun on death doesn't lose its modules. Open it with `/rig gun <gun>`
(or `/rig`, then that gun's tab), or the **Weapon Rigs** button at the gunsmith.

| Gun | Weapon Rig |
| --- | --- |
| Rivet Pistol | 2×2 |
| Scrap SMG | 3×2 |
| Breach Shotgun | 3×2 |
| Bolt Rifle | 4×2, 1 core cell |
| Plasma Lance | 4×3, 1 core cell |

Weapon modules multiply the gun's numbers. Lower spread, recoil and reload time are better.

| Module | Rarity | Family | Shape | Upside | Downside | Rule |
| --- | --- | --- | --- | --- | --- | --- |
| Extended Mag | Common | Ballistic | Bar 2 | Magazine +30% | Reload 15% slower | |
| Recoil Damper | Common | Optics | Dot | Recoil −25% | Fire rate −5% | |
| Laser Sight | Rare | Optics | Bar 2 | Spread −25% | Recoil +10% | Needs the edge |
| Hair Trigger | Rare | Ballistic | L | Fire rate +15% | Spread +20% | |
| Speed Loader | Rare | Ballistic | T | Reload 25% faster | Magazine −15% | |
| Long Barrel | Epic | Optics | Bar 3 | Range +30%, damage +6% | Fire rate −10% | |
| Hollow-Point Press | Epic | Ballistic | Square | Damage +12% | Headshot −10%, magazine −10% | Must touch Ballistic |
| Smart Targeter | Legendary | Optics | Zig-zag | Headshot +25%, spread −20% | Fire rate −12% | Core cell |

| Family | 2 online | 3 online |
| --- | --- | --- |
| Ballistic | Magazine +10% | Damage +5% |
| Optics | Spread −10% | Headshot +10%, recoil −15% |

Two hidden combos exist for Weapon Rigs. Experiment to find them.

**Where weapon modules come from:** a random one drops on **3% of Undercroft kills** and **5% of smuggling runs**.
You can also build any of them at the workshop [Assembly Bench](workshop.md#the-assembly-bench) from its schematic;
everyone starts knowing the Extended Mag. They fuse to Mk V, trade and survive prestige like drill modules.

## How it ties into progression

Guns are bought with Credits and parts, so they're a Credits sink. The Static stim adds ×1.2 gun damage. Gangmates
can't shoot each other.
