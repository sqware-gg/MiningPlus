# MiningPlus

**Join the SQWARE Discord: [discord.sqware.gg](https://discord.sqware.gg).**

MiningPlus is a modular vanilla+ mining enhancement for Paper. Players earn mining XP from natural ore mining, unlock ranks, collect rare artifacts, spend Mine Shards, earn Vault money, build PointsPlus point totals, and progress through long-term mining goals. Mine Shards power MiningPlus utility, Vault money represents economic value, and PointsPlus points act as prestige/proof progress. Every feature is an independently toggleable module.

Use it when you want mining progression, ore rewards, utility upgrades, artifacts, missions, and mining-specific economy sinks without turning every mining feature into a separate plugin.

## Modules

- **Mining Levels** - per-player XP and levels with a configurable curve, ranks, level-up sound/particle/broadcast effects, and an optional action-bar XP ticker.
- **Custom Drops** - per-block drop tables (item, min/max, chance, optional fortune scaling), optionally overriding vanilla drops.
- **Rewards** - per-block Vault money payouts, PointsPlus point payouts, Mine Shards, plus level milestone rewards.
- **Sell Shop** - optional `/mining sell` converts configured mining drops and artifacts into Vault money. It is enabled in the bundled config and can be disabled if the server uses a separate shop/worth economy.
- **Vein Miner** - break connected ore veins in one swing with max-block cap, tool whitelist, durability/hunger cost, sneak-toggle, and a per-player on/off switch.
- **Auto-Smelt** - smelt mined drops into their cooked result via configurable recipes (vanilla + custom drops).
- **Auto-Pickup** - send mined drops straight to the player's inventory, overflowing to the ground.
- **Abilities** - random mining procs gated by permission, unlock level, and chance: explosive ore breaks, haste, bonus XP, Ore Sense, and Stoneguard.
- **Mining Events** - random treasure and hazard events with block filters, level gates, permissions, particles, sounds, custom drops, money, points, Mine Shards, commands, damage, and potion effects.
- **Quests / Journal** - fully configurable progression chapters with ordered prerequisites, objective types, lore, icons, item/currency/XP/point rewards, pickaxe/tool-upgrade objectives, and console commands.
- **Missions** - repeatable opt-in mining missions with per-player baselines, active mission limits, journal/level locks, and rewards for short mining sessions. Internally these remain under the `commissions` config key for compatibility.
- **Perks** - level-earned perk points with configurable upgrades for mining XP, pickaxe XP, money, points, Mine Shards, treasure chance, artifact chance, and hazard mitigation.
- **Artifacts** - rare persistent Mining+-tagged collectible drops with optional money, points, shard, command, sell-price rewards, and configurable dry-streak protection.
- **Pickaxe Progression** - pickaxes gain persistent Mining+ XP and levels on the item itself, granting mining XP and artifact chance bonuses; players can refine pickaxes with Vault money and Mine Shards, buy pickaxe XP or guaranteed utility upgrades from the shard shop, and find rare permanent upgrades through the enchanting table.
- **Player Feedback** - configurable action-bars, titles, sounds, and particles for mining, artifacts, journal claims, shop purchases, pickaxe refinement, level-ups, and optional selling using Paper/Minecraft namespaced IDs.
- **Anti-Exploit** - tracks player-placed blocks so they grant no XP/drops/money when re-broken.
- **GUI** - a `/mining` inventory menu showing level, XP progress bar, rank, leaderboard, quests, missions, shop rewards, pickaxe progression, and feature toggles.
- **Placeholders** - `%miningplus_*%` placeholders via PlaceholderAPI.

Toggle each under `modules:` in `config.yml`.

## Configuration

`config.yml` is the small root file for module toggles, storage, leaderboard size, and the list of split config files. Gameplay content is split under `sections/`:

- `progression.yml` - levels, ranks, multipliers, currency, perks, pickaxe progression, tool upgrades, milestones.
- `mining.yml` - material groups, block rewards, vein mining, auto-smelt recipes.
- `abilities-events.yml` - mining abilities, treasure events, and hazards.
- `artifacts.yml` - artifact drops and artifact rewards.
- `economy.yml` - optional `/mining sell` pricing.
- `journal.yml` - questline chapters.
- `commissions.yml` - repeatable short-form mining missions.
- `shop.yml` - Mine Shard shop rewards.
- `interface.yml` - feedback, GUI, and messages.

For backward compatibility, paths still present in `config.yml` take priority over the same paths in section files. To migrate an old full config gradually, move a top-level section into the matching `sections/*.yml` file and remove that top-level section from `config.yml`.

Tune sound and particle polish under `feedback:` in `sections/interface.yml`. Sound and particle types accept 26.2-style namespaced keys such as `minecraft:block.amethyst_block.chime`, `minecraft:vault_connection`, `minecraft:dust_plume`, and `minecraft:totem_of_undying`.

Quest chapters live under `journal.chapters` in `sections/journal.yml` and can be exposed to players with `/mining journal`, `/mining quest`, or `/mining quests`. Server owners can configure prerequisites, objective types, story lore, GUI icons, XP/money/points/shard/perk rewards, `reward.item`, multi-item `reward.items`, and console commands. The bundled progression uses Vault and PointsPlus when present; by default, earned-money and earned-point objectives auto-complete if the matching plugin is not installed.

Missions live under `commissions.definitions` in `sections/commissions.yml` and are exposed through `/mining missions` plus the main `/mining` GUI. Clicking an available GUI mission accepts it; clicking an active mission attempts to claim it when complete. The older `/mining commissions` command still works as an alias.

## Pickaxe Upgrades

Pickaxe upgrades are persistent item abilities found from the vanilla enchanting table. They are not registered Minecraft enchantments, so they are stored in item data and shown in the pickaxe lore/menu. The bundled config includes a dry-streak fallback so repeated eligible enchants eventually grant an upgrade.

- **Auto Pickup** - sends mined drops straight into the player's inventory.
- **Auto Smelt** - smelts configured ore drops while mining.
- **Luminous** - briefly refreshes night vision while mining rewarded ore.
- **Prospector** - slightly improves artifact find chance.
- **Reinforced** - reduces extra durability wear from vein mining.
- **Shard Magnet** - increases Mine Shards earned from mining, treasure, and artifact shard rewards.
- **Vein Miner** - breaks connected ore veins in one swing when the player's toggle is enabled.

## Requirements

- Paper `26.2+`
- Java `25+`
- Recommended for bundled progression: Vault and PointsPlus
- Optional: PlaceholderAPI for placeholders
- Maven wrapper included

## Commands

```text
/mining
/mining stats
/mining progress
/mining top
/mining veinminer
/mining notifications
/mining perks
/mining perk <id>
/mining journal
/mining quest
/mining quests
/mining missions
/mining mission accept <id>
/mining mission claim <id>
/mining mission abandon <id>
/mining shop
/mining shards
/mining artifacts
/mining pickaxe
/mining pickaxe refine
/mining help

/miningplus reload
/miningplus save
/miningplus stats
/miningplus setlevel <player> <level>
/miningplus addxp <player> <amount>
```

Aliases: `/mine`, `/miningstats`, `/mplus`, `/miningadmin`

## Permissions

```text
miningplus.use              - gain mining rewards and use /mining (default: true)
miningplus.veinminer        - use the vein miner (default: true)
miningplus.autosmelt        - auto-smelt mined drops (default: true)
miningplus.autopickup       - auto-pickup mined drops (default: true)
miningplus.sell             - use optional /mining sell when the sell module is enabled (default: true)
miningplus.perks            - view and buy mining perks (default: true)
miningplus.journal          - view and claim Mining+ journal chapters (default: true)
miningplus.commissions      - accept and claim Mining+ missions (default: true)
miningplus.shop             - use the Mining+ shard shop (default: true)
miningplus.artifacts        - find Mining+ artifacts (default: true)
miningplus.pickaxe          - use Mining+ pickaxe progression (default: true)
miningplus.ability.explosive - explosive ability can proc (default: true)
miningplus.ability.haste    - haste ability can proc (default: true)
miningplus.ability.bonusxp  - bonus XP ability can proc (default: true)
miningplus.ability.oresense - Ore Sense ability can proc (default: true)
miningplus.ability.stoneguard - Stoneguard ability can proc (default: true)
miningplus.event.treasure   - treasure events can proc (default: true)
miningplus.event.hazard     - hazard events can proc (default: true)
miningplus.admin            - all admin commands (default: op)
miningplus.multiplier.<id>  - permission-based XP/money multipliers (see config)
```

## Placeholders

```text
%miningplus_level%
%miningplus_rank%
%miningplus_xp%
%miningplus_xp_required%
%miningplus_progress%
%miningplus_blocks_mined%
%miningplus_perk_points%
%miningplus_perk_<id>%
%miningplus_artifacts_found%
%miningplus_artifacts_unique%
%miningplus_artifact_<id>%
%miningplus_shards%
%miningplus_shards_formatted%
%miningplus_shards_earned%
%miningplus_shards_spent%
%miningplus_journal_claimed%
%miningplus_journal_next%
%miningplus_commissions_active%
%miningplus_commissions_completed%
%miningplus_commission_active%
%miningplus_commission_progress%
%miningplus_best_pickaxe_level%
%miningplus_pickaxe_refines%
%miningplus_tool_upgrades_found%
%miningplus_tool_upgrade_<id>%
%miningplus_pickaxe_level%
%miningplus_pickaxe_xp%
%miningplus_pickaxe_xp_required%
%miningplus_pickaxe_progress%
%miningplus_pickaxe_xp_bonus%
%miningplus_pickaxe_artifact_bonus%
```

## Build

```powershell
.\mvnw.cmd package
```

The jar is written to `target/MiningPlus-0.1.0.jar`.

## Data Files

- `players-data.yml` - per-player level, XP, blocks mined, toggles, perks, Mine Shards, journal claims, active missions, dry-streak counters, and pickaxe progression counters.
- `placed-blocks-data.yml` - tracked player-placed blocks for the anti-exploit module.

Both are saved on the `storage.save-interval-seconds` timer and on shutdown.

## License

MiningPlus is licensed under the Apache License, Version 2.0.
