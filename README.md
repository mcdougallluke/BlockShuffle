# 🧱 Block Shuffle (Minecraft 1.21+)

A multiplayer **Block Shuffle** game mode plugin for Minecraft 1.21.11 built with the Spigot API. Players must race to find and stand on a randomly assigned block before time runs out — or be eliminated!

> 🔧 Built with Java 21 and Gradle.  
> 🎮 Supports dynamic worlds, round tracking, player readiness, block skipping, admin settings GUI, broadcast messaging, and more.

---

## 📦 Features

- ✅ Round-based block hunting game
- 🧍 Players stand on a randomly assigned block within a configurable timer
- 🏁 Auto-eliminates players who fail
- ⏳ Visual timer via boss bar
- 🔀 Skip your block for free once per game (`/skipblock`)
- 🧑‍🤝‍🧑 Player ready-up system (`/blockshuffle ready`)
- 🌍 Dynamic world generation for each match
- ⚙️ Admin settings GUI (`/blockshuffle settings`)
- 📄 Configurable block list in `settings.yml`

---

## 🧪 Commands

### `/blockshuffle` subcommands

| Subcommand   | Description                            | Permission                       |
|--------------|----------------------------------------|----------------------------------|
| `ready`      | Toggle your ready status               | `blockshuffle.command.base`      |
| `start`      | Start the game                         | `blockshuffle.command.start`     |
| `stop`       | Stop the current game                  | `blockshuffle.admin.stop`        |
| `settings`   | Open the admin settings GUI            | `blockshuffle.admin.settings`    |
| `readyall`   | Mark all online players as ready       | `blockshuffle.admin.readyall`    |
| `broadcast`  | Broadcast a MiniMessage to everyone    | `blockshuffle.admin.broadcast`   |
| `spectate`   | (Disabled)                             | `blockshuffle.command.base`      |

### Other commands

| Command                          | Description                         | Permission                         |
|----------------------------------|-------------------------------------|------------------------------------|
| `/skipblock`                     | Skip your block once per game       | `blockshuffle.command.skip`        |
| `/lobby`                         | Return to the lobby                 | `blockshuffle.command.lobby`       |
| `/stats [player]`                | View your or another’s stats        | `blockshuffle.command.stats`       |
| `/giveskips <player> <amount>`   | Grant skips to a player             | `blockshuffle.command.giveskips`   |
| `/testmsg <minimessage>`         | Test MiniMessage formatting         | `blockshuffle.command.testmsg`     |

---

## 🔐 Permissions

| Node                                 | Default | Notes                                      |
|--------------------------------------|:-------:|--------------------------------------------|
| `blockshuffle.command.base`          |  true   | Required for any `/blockshuffle` usage      |
| `blockshuffle.command.start`         |  true   | `/blockshuffle start`                       |
| `blockshuffle.command.skip`          |  true   | `/skipblock`                                |
| `blockshuffle.command.lobby`         |  true   | `/lobby`                                    |
| `blockshuffle.command.stats`         |  true   | `/stats`                                    |
| `blockshuffle.command.giveskips`     |  false  | `/giveskips` (must be granted explicitly)   |
| `blockshuffle.command.testmsg`       |   op    | `/testmsg`                                  |
| `blockshuffle.admin.stop`            |   op    | `/blockshuffle stop`                        |
| `blockshuffle.admin.settings`        |   op    | `/blockshuffle settings`                    |
| `blockshuffle.admin.readyall`        |   op    | `/blockshuffle readyall`                    |
| `blockshuffle.admin.broadcast`       |   op    | `/blockshuffle broadcast`                   |
| `blockshuffle.admin.*`               |   op    | All admin subcommands                       |

---

## ⚙️ Configuration

Edit `src/main/resources/settings.yml`:

```yaml
roundTimeSeconds: 300      # Round length in seconds
pvpEnabled: false          # Enable/disable PvP
decreaseTime: true         # Decrease timer each round
gameMode: Classic          # "Classic" or "Continuous"
materials:
  - AIR
  - STONE
  - DIRT
  # …and your other block choices
