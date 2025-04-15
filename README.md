# 🧱 Block Shuffle (Minecraft 1.21+)

A multiplayer **Block Shuffle** game mode plugin for Minecraft 1.21.5 built with the Spigot API. Players must race to find and stand on a randomly assigned block before the time runs out — or be eliminated!

> 🔧 Built with Java 21 and Gradle.  
> 🎮 Supports dynamic worlds, round tracking, player readiness, block skipping, and more.

---

## 📦 Features

- ✅ Round-based block hunting game
- 🧍 Players must stand on a specific block within 5 minutes
- 🏁 Auto-eliminates players who fail
- ⏳ Visual timer via boss bar
- 🔀 One-time block skip per game (`/skipblock`)
- 🧑‍🤝‍🧑 Player ready-up system (`/blockshuffle ready`)
- 🌍 Dynamic world generation for each game
- 📄 Easily configurable block list via `settings.yml`

## 🧪 Commands

| Command                | Description                                 |
|------------------------|---------------------------------------------|
| `/blockshuffle ready`  | Toggles player ready state                  |
| `/blockshuffle start`  | Starts the game (admin only)                |
| `/blockshuffle stop`   | Stops the current game (admin only)         |
| `/blockshuffle spectate` | (Currently disabled)                     |
| `/blockshuffle readyall` | Admin-only command to ready all players  |
| `/skipblock`           | Skip your block **once** per game           |

---

## 🔐 Permissions

| Permission           | Description                                              | Default |
|----------------------|----------------------------------------------------------|---------|
| `blockshuffle.admin` | Allows starting/stopping games, readying all players     | true    |

---

## 💡 Notes

- Players who disconnect and rejoin during a round will be restored if still active.
- When all active players complete their block, the next round starts immediately.
- Winners get fireworks 🎆 and a celebration message.

---

## ✨ Author

Made with ❤️ by [lukeeirl](https://github.com/mcdougallluke)  
Website: [lukemcd.dev](https://lukemcd.dev)
