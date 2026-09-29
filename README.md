# PlayerVaults

Personal vaults for every player on PowerNukkitX. Type `/pv` and get a private chest that follows you everywhere, saved on the server. Give more vaults with a permission, look into anyone's vault as an admin, even when they are offline.

## What it does

- **`/pv` opens your vault.** With one vault it opens straight away. With several, a picker shows every vault and how many stacks it holds.
- **Double chest vaults** (54 slots) by default, or single chests (27 slots) if you prefer.
- **Vaults by permission:** `playervaults.amount.3` gives 3 vaults, up to the maximum you set.
- **Admin access:** `/pv <number> <player>` opens the vault of another player, online or offline. If the owner has it open at the same time, you both see the same items live.
- **Blacklist:** block items you don't want stored (bedrock, command blocks and barriers by default).
- **Safe storage:** one compressed file per player, written through a temporary file so a crash never leaves a half saved vault. A damaged file is renamed and kept instead of being overwritten.
- **Reliable menus:** vaults use their own chest handling, so they open every time and never leave a ghost chest behind.

## Install

1. Drop `PlayerVaults.jar` in your `plugins` folder.
2. Restart the server.

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/pv` | Open your vault, or the picker if you have several | `playervaults.use` (everyone) |
| `/pv <number>` | Open one of your vaults | `playervaults.use` (everyone) |
| `/pv <number> <player>` | Open another player's vault | `playervaults.admin` (op) |

Aliases: `/vault`, `/vaults`, `/playervault`.

## Permissions

| Permission | Default | Effect |
|---|---|---|
| `playervaults.use` | everyone | Open your own vaults |
| `playervaults.amount.<n>` | nobody | Number of vaults the player gets (the highest one wins) |
| `playervaults.admin` | op | Open and edit other players' vaults |
| `playervaults.bypass.blacklist` | op | Store blacklisted items |

Players without any `playervaults.amount.<n>` permission get `default-vaults`.

## Config

```yaml
default-vaults: 1
max-vaults: 27
vault-size: 54
open-delay-ticks: 10
command-delay-ticks: 10
blacklist:
  - minecraft:bedrock
  - minecraft:command_block
  - minecraft:barrier
```

Every message is in the `messages` section of `config.yml`.

## Compatibility

PowerNukkitX 3.x (API 3.0.0), tested on 3.0.5 with Minecraft Bedrock 1.26.50.

## About

Made by DeepSlate Dev. Released under the MIT license, free to use on any server.
