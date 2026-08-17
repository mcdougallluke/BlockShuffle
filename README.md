# Block Shuffle

Block Shuffle is a Paper plugin for a Minecraft minigame: everyone gets assigned a
random block, you race to find and stand on it before the timer runs out, and
whoever doesn't make it gets eliminated.

Runs on Minecraft **26.2** (Paper API), built with **Java 25** and Gradle.

## Game modes

Set in `settings.yml` or from the in-game settings GUI (`/blockshuffle settings`).

- **Classic**: everyone shares one round timer. Find your block before it
  hits zero or you're eliminated. Last player standing wins.
- **Continuous**: no shared clock. Each player gets their own timer and a
  fresh block the moment they finish the last one, so faster players just
  keep going instead of waiting on the round.
- **FirstTo**: a race to a target number of blocks (`blocksToWin`). First
  player to hit the count wins.

Every match spins up a brand-new Overworld/Nether/End set so nothing carries
over between games, and gets torn down after.

## Commands

### `/blockshuffle` (`/bs`)

| Subcommand  | What it does                                              | Permission                    |
|-------------|-----------------------------------------------------------|--------------------------------|
| `ready`     | Toggle your ready status                                  | `blockshuffle.command.base`    |
| `start`     | Start the game once enough players are ready              | `blockshuffle.command.start` |
| `stop`      | End the current game                                      | `blockshuffle.admin.stop`      |
| `settings`  | Open the settings GUI                                     | `blockshuffle.admin.settings` |
| `readyall`  | Force-ready everyone online                               | `blockshuffle.admin.readyall`  |
| `broadcast` | Send a MiniMessage-formatted server broadcast             | `blockshuffle.admin.broadcast` |
| `spectate`  | Watch a game in progress                                  | `blockshuffle.command.base`    |
| `newblock`  | Request a new block if you've been stuck on yours 5+ minutes | `blockshuffle.command.base` |

### Standalone

| Command | What it does | Permission |
|---|---|---|
| `/skipblock` (`/skip`) | Skip your current block, once per game | `blockshuffle.command.skip` |
| `/lobby` (`/l`) | Leave the game and return to the lobby | `blockshuffle.command.lobby` |
| `/stats [player]` | Show games played/won, blocks found, skips bought/remaining | `blockshuffle.command.stats` |
| `/giveskips <player> <amount>` | Grant extra skips | `blockshuffle.command.giveskips` |

## Permissions

| Node | Default | Notes |
|---|:---:|---|
| `blockshuffle.command.base` | true | Base `/blockshuffle` access |
| `blockshuffle.command.start` | true | `/blockshuffle start` |
| `blockshuffle.command.skip` | true | `/skipblock` |
| `blockshuffle.command.lobby` | true | `/lobby` |
| `blockshuffle.command.stats` | true | `/stats` |
| `blockshuffle.command.giveskips` | false | `/giveskips` — grant explicitly |
| `blockshuffle.command.testmsg` | op | `/testmsg` |
| `blockshuffle.admin.stop` | op | `/blockshuffle stop` |
| `blockshuffle.admin.settings` | op | `/blockshuffle settings` |
| `blockshuffle.admin.readyall` | op | `/blockshuffle readyall` |
| `blockshuffle.admin.broadcast` | op | `/blockshuffle broadcast` |
| `blockshuffle.admin.*` | op | All of the above admin nodes |

## Configuration

`src/main/resources/settings.yml` — most of this is also editable live through
`/blockshuffle settings`:

```yaml
roundTimeSeconds: 300     # Length of a round, in seconds
pvpEnabled: false         # Allow players to fight each other
decreaseTime: true        # Shorten the timer each round (Classic mode)
gameMode: Classic         # Classic | Continuous | FirstTo
blocksToWin: 5            # Target for FirstTo mode

materials:
  - AIR
  - STONE
  - DIRT
  # ...the full pool ships with ~250 blocks; trim or extend it to taste
```

## Building

```bash
./gradlew build
```

Drop the resulting jar from `build/libs/` into your server's `plugins/`
folder.

---

Built by [lukemcd](https://lukemcd.dev).
