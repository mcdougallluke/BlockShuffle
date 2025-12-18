# AGENTS.MD - BlockShuffle Minecraft Plugin

**Version:** 1.1.0
**Target:** Minecraft 1.21.11 (Paper API)
**Language:** Java 21
**Build System:** Gradle

---

## 🎯 Project Overview

BlockShuffle is a multiplayer Minecraft minigame plugin where players compete in an elimination-style block-finding challenge. Players must locate and stand on randomly assigned blocks before time expires or face elimination.

**Core Gameplay Loop:**
1. Players ready up in lobby (`/blockshuffle ready`)
2. Game starts with 2+ players (`/blockshuffle start`)
3. Each round: players receive random block assignment
4. Players navigate world (Overworld/Nether/End) to find their block
5. Stand on assigned block before timer expires
6. Incomplete players eliminated; survivors advance to next round
7. Last player standing wins

**Two Game Modes:**
- **Classic Mode** (default): Round-based elimination with shared timer
- **Continuous Mode**: Individual per-player timers with continuous block assignments

---

## 🏗️ Architecture Overview

### Package Structure
```
src/main/java/org/lukeeirl/blockShuffle/
├── BlockShuffle.java              # Main plugin class (entry point)
├── commands/                       # Command handlers (6 classes)
│   ├── BlockShuffleCommand.java   # Main /blockshuffle with subcommands
│   ├── SkipBlockCommand.java      # /skipblock
│   ├── LobbyCommand.java          # /lobby
│   ├── StatsCommand.java          # /stats [player]
│   ├── GiveSkipsCommand.java      # /giveskips <player> <amount>
│   └── TestMessageCommand.java    # /testmsg <minimessage>
├── events/
│   └── PlayerListener.java        # Bukkit event handlers (movement, join, respawn, PvP, portals)
├── game/                           # Core game logic
│   ├── BSGameMode.java            # Interface for game implementations
│   ├── GameManager.java           # Facade for game mode delegation
│   ├── ClassicBlockShuffle.java   # Round-based elimination mode
│   ├── ContinuousBlockShuffle.java # Continuous play mode
│   ├── PlayerTracker.java         # Player state tracking
│   └── WorldService.java          # Dynamic world creation/cleanup
├── ui/
│   └── SettingsGUI.java           # Admin settings menu (hopper GUI)
└── util/                           # Utilities
    ├── PlayerUtils.java           # Player state reset, formatting, fireworks
    ├── PlayerStats.java           # Immutable stats record
    ├── StatsManager.java          # Stats persistence (stats.yml)
    └── SkipManager.java           # Skip persistence (skips.yml)

src/main/resources/
├── plugin.yml      # Plugin metadata, commands, permissions
├── settings.yml    # Game configuration (round time, PvP, block list)
├── skips.yml       # Player skip data (UUID → skip count)
└── stats.yml       # Player statistics (UUID → stats object)
```

### Design Patterns
- **Strategy Pattern:** `BSGameMode` interface with `ClassicBlockShuffle`/`ContinuousBlockShuffle` implementations
- **Facade Pattern:** `GameManager` abstracts underlying game mode complexity
- **Observer Pattern:** `PlayerListener` subscribes to Bukkit events
- **Repository Pattern:** `StatsManager` and `SkipManager` handle data persistence
- **Dependency Injection:** Manual constructor injection in `BlockShuffle.onEnable()`

---

## 🔨 Build & Development

### Build Commands
```bash
# Build the plugin JAR
./gradlew build

# Clean build artifacts
./gradlew clean

# Full clean and build
./gradlew clean build
```

**Output:** `build/libs/BlockShuffle-1.1.0.jar`

### Requirements
- **Java 21** (JDK 21+)
- **Gradle** (wrapper included)
- **Paper API 1.21.11-R0.1-SNAPSHOT** (compile-only dependency)

### Dependencies
```groovy
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
```

**Repositories:**
- Maven Central
- PaperMC Repository: `https://repo.papermc.io/repository/maven-public/`
- Sonatype OSS: `https://oss.sonatype.org/content/groups/public/`

---

## 📝 Code Style & Conventions

### General Guidelines
- **Java 21** with modern language features (records, pattern matching, text blocks where appropriate)
- **4-space indentation** (no tabs)
- **UTF-8 encoding** for all source files
- **Explicit imports** (no wildcards except for multiple classes from same package)
- **No Hungarian notation** - use descriptive names

### Naming Conventions
- **Classes:** PascalCase (e.g., `GameManager`, `PlayerTracker`)
- **Methods:** camelCase (e.g., `startGame()`, `playerStandingOnBlock()`)
- **Variables:** camelCase (e.g., `lobbyWorld`, `activeMode`)
- **Constants:** UPPER_SNAKE_CASE (not heavily used in this codebase)
- **Packages:** lowercase (e.g., `org.lukeeirl.blockShuffle.game`)

### Code Organization
- **Constructor injection** for dependencies (no field injection)
- **Private fields** with minimal public accessors
- **Static utility classes** for shared functionality (see `PlayerUtils`)
- **Immutable data** where possible (e.g., `PlayerStats` as record)
- **Early returns** to reduce nesting
- **Null safety:** Use `Objects.requireNonNull()` and `@NotNull` annotations

### Example Code Style
```java
public class GameManager {
    private final World lobbyWorld;
    private final SettingsGUI settingsGUI;
    private final PlayerTracker tracker;
    private BSGameMode activeMode;

    public GameManager(PlayerTracker tracker, BlockShuffle plugin,
                       YamlConfiguration settings, SettingsGUI settingsGUI,
                       SkipManager skipManager, StatsManager stats) {
        this.settingsGUI = settingsGUI;
        this.tracker = tracker;
        this.lobbyWorld = Bukkit.getWorlds().getFirst();
        // ... initialization
    }

    public void startGame() {
        if (settingsGUI.isContinuousMode()) {
            this.activeMode = continuousMode;
        } else {
            this.activeMode = classicMode;
        }
        activeMode.startGame();
    }
}
```

### Messaging & User Interaction
- Use **Kyori Adventure API** for all text components
- Use **MiniMessage** format for colored/styled messages
- Prefix all plugin messages via `PlayerUtils.prefixedMessage()`
- Sound effects: `Sound.ENTITY_ITEM_PICKUP` (success), `Sound.ENTITY_VILLAGER_DEATH` (elimination)
- Boss bars for timers, titles for countdown

---

## 🎮 Key Game Mechanics

### Block Detection Algorithm
Player position checked at multiple Y-offsets for reliability:
- Location: `playerLocation.getBlock().getLocation()`
- Offsets checked: `0.0, -0.1, -0.3, -0.6, -0.8`
- Matches against assigned material from `PlayerTracker`

**Implementation:** [PlayerListener.java:45-80](src/main/java/org/lukeeirl/blockShuffle/events/PlayerListener.java#L45-L80)

### Skip System
- **First skip per game:** Free
- **Additional skips:** Must be purchased via `/giveskips` (admin command)
- **Skip data persisted** in `skips.yml` (UUID → skip count)
- **Tracked separately:** `SkipManager` (persistent) vs `PlayerTracker` (in-game usage)

### Round Progression (Classic Mode)
1. Assign random blocks from 268-block pool (configurable in `settings.yml`)
2. Start countdown timer (default: 300 seconds)
3. Boss bar shows round number and time remaining
4. Players who stand on block marked as completed
5. When timer expires: incomplete players eliminated
6. If `decreaseTime: true`, next round timer reduced by 200 seconds
7. Repeat until one winner remains

### World Management
- **Dynamic world creation:** Each game creates new world trio (base, _nether, _the_end)
- **Portal travel:** Players can explore Nether/End via portals (event-driven teleportation)
- **World cleanup:** Previous game worlds deleted on new game start
- **Game rules set:** `doImmediateRespawn: true`, `doInsomnia: false`, view distance: 10

**Implementation:** [WorldService.java](src/main/java/org/lukeeirl/blockShuffle/game/WorldService.java)

---

## 🗂️ Configuration Files

### settings.yml
```yaml
roundTimeSeconds: 300      # Timer per round (seconds)
pvpEnabled: false          # Enable/disable PvP combat
decreaseTime: true         # Reduce timer each round
gameMode: Classic          # "Classic" or "Continuous"
materials:                 # 268 valid Minecraft blocks
  - AIR
  - STONE
  - GRASS_BLOCK
  # ... (268 total materials)
```

**Location:** `plugins/BlockShuffle/settings.yml`
**Edited by:** Admin via `/blockshuffle settings` GUI or manual file edit

### stats.yml
```yaml
<uuid>:
  gamesPlayed: 10
  gamesWon: 2
  skipsBought: 5
  blocksSteppedOn: 47
```

**Managed by:** `StatsManager` (automatic save/load)

### skips.yml
```yaml
<uuid>: 3  # Player has 3 skips available
```

**Managed by:** `SkipManager` (automatic save/load)

---

## 🔐 Permissions System

### Hierarchy
- `blockshuffle.admin.*` → All admin permissions (default: op)
  - `blockshuffle.admin.stop`
  - `blockshuffle.admin.settings`
  - `blockshuffle.admin.readyall`
  - `blockshuffle.admin.broadcast`

### Full Permission List
| Permission | Default | Command |
|------------|---------|---------|
| `blockshuffle.command.base` | true | `/blockshuffle` (required for any usage) |
| `blockshuffle.command.start` | true | `/blockshuffle start` |
| `blockshuffle.command.skip` | true | `/skipblock` |
| `blockshuffle.command.lobby` | true | `/lobby` |
| `blockshuffle.command.stats` | true | `/stats [player]` |
| `blockshuffle.command.giveskips` | false | `/giveskips <player> <amount>` |
| `blockshuffle.command.testmsg` | op | `/testmsg <minimessage>` |
| `blockshuffle.admin.stop` | op | `/blockshuffle stop` |
| `blockshuffle.admin.settings` | op | `/blockshuffle settings` |
| `blockshuffle.admin.readyall` | op | `/blockshuffle readyall` |
| `blockshuffle.admin.broadcast` | op | `/blockshuffle broadcast <msg>` |

---

## 🧪 Commands Reference

### Primary Commands
```bash
/blockshuffle ready          # Toggle ready status
/blockshuffle start          # Start game (2+ ready players required)
/blockshuffle stop           # Stop current game (admin)
/blockshuffle settings       # Open admin GUI (admin)
/blockshuffle readyall       # Mark all online players ready (admin)
/blockshuffle broadcast <msg> # Broadcast MiniMessage (admin)
/blockshuffle spectate       # (Currently disabled)
```

### Secondary Commands
```bash
/skipblock                   # Skip assigned block (free once per game)
/lobby                       # Return to lobby (leaves game if in-progress)
/stats [player]              # View player statistics (tab completion)
/giveskips <player> <amount> # Grant skips to player (admin)
/testmsg <minimessage>       # Test MiniMessage formatting (debug)
```

**Tab Completion:**
- `/stats` supports player name tab completion
- `/giveskips` supports player name tab completion

---

## 🔍 Important Code Locations

### Game State Management
- **Game mode switching:** [GameManager.java:38-45](src/main/java/org/lukeeirl/blockShuffle/game/GameManager.java#L38-L45)
- **Classic round logic:** [ClassicBlockShuffle.java](src/main/java/org/lukeeirl/blockShuffle/game/ClassicBlockShuffle.java)
- **Player tracking:** [PlayerTracker.java](src/main/java/org/lukeeirl/blockShuffle/game/PlayerTracker.java)

### Event Handling
- **Block detection:** [PlayerListener.java:45-80](src/main/java/org/lukeeirl/blockShuffle/events/PlayerListener.java#L45-L80)
- **Portal travel:** [PlayerListener.java:112-140](src/main/java/org/lukeeirl/blockShuffle/events/PlayerListener.java#L112-L140)
- **PvP enforcement:** [PlayerListener.java:95-110](src/main/java/org/lukeeirl/blockShuffle/events/PlayerListener.java#L95-L110)

### UI & Messaging
- **Message formatting:** [PlayerUtils.java:41-54](src/main/java/org/lukeeirl/blockShuffle/util/PlayerUtils.java#L41-L54)
- **Settings GUI:** [SettingsGUI.java](src/main/java/org/lukeeirl/blockShuffle/ui/SettingsGUI.java)
- **Victory fireworks:** [PlayerUtils.java:56-70](src/main/java/org/lukeeirl/blockShuffle/util/PlayerUtils.java#L56-L70)

### Data Persistence
- **Stats management:** [StatsManager.java](src/main/java/org/lukeeirl/blockShuffle/util/StatsManager.java)
- **Skip management:** [SkipManager.java](src/main/java/org/lukeeirl/blockShuffle/util/SkipManager.java)

---

## 🧩 Common Development Tasks

### Adding a New Command
1. Create command class in `commands/` package
2. Extend `CommandExecutor` interface
3. Register in [BlockShuffle.java:40-45](src/main/java/org/lukeeirl/blockShuffle/BlockShuffle.java#L40-L45)
4. Add to [plugin.yml](src/main/resources/plugin.yml) with permissions
5. Use `PlayerUtils.prefixedMessage()` for user feedback

### Adding a New Game Mode
1. Implement `BSGameMode` interface
2. Add instance to [GameManager.java:21-35](src/main/java/org/lukeeirl/blockShuffle/game/GameManager.java#L21-L35)
3. Add mode selection logic in `GameManager.startGame()`
4. Add toggle option to [SettingsGUI.java](src/main/java/org/lukeeirl/blockShuffle/ui/SettingsGUI.java)
5. Update `settings.yml` with new `gameMode` value

### Modifying Block Pool
- Edit `src/main/resources/settings.yml` → `materials` list
- Use valid Material enum names from Spigot API
- Avoid non-placeable blocks (e.g., `PLAYER_HEAD` without placement context)

### Adding Player Statistics
1. Add field to [PlayerStats.java](src/main/java/org/lukeeirl/blockShuffle/util/PlayerStats.java) record
2. Update `StatsManager.loadStats()` deserialization
3. Update `StatsManager.saveStats()` serialization
4. Modify `StatsCommand` display logic

---

### Debug Commands
```bash
/testmsg <minimessage>  # Test MiniMessage color/format codes
```

### Logging
- Static logger: `BlockShuffle.logger` (accessible anywhere)
- Example: `BlockShuffle.logger.info("Game started with " + playerCount + " players");`

---

## 📚 Dependencies & APIs

### Primary APIs
- **Paper API 1.21.11-R0.1-SNAPSHOT** - Core Minecraft server API
- **Kyori Adventure** - Text component system (included in Paper)
- **Bukkit/Spigot API** - Event system, scheduler, configuration

### Key Classes to Know
- `org.bukkit.plugin.java.JavaPlugin` - Plugin lifecycle
- `org.bukkit.event.Listener` - Event handling
- `org.bukkit.configuration.file.YamlConfiguration` - Config management
- `net.kyori.adventure.text.Component` - Chat components
- `net.kyori.adventure.text.minimessage.MiniMessage` - Message parsing
- `org.bukkit.boss.BossBar` - Boss bar timers
- `org.bukkit.scheduler.BukkitScheduler` - Async/delayed tasks

---

## 🚀 Deployment

### Installation
1. Build plugin: `./gradlew build`
2. Copy `build/libs/BlockShuffle-1.1.0.jar` to server's `plugins/` folder
3. Start/restart Paper 1.21.11+ server
4. Plugin auto-generates config files in `plugins/BlockShuffle/`

### Configuration
1. Edit `plugins/BlockShuffle/settings.yml` as needed
2. Set permissions via permissions plugin (LuckPerms, etc.)
3. Use `/blockshuffle settings` in-game for runtime changes

### Server Requirements
- **Server Software:** Paper 1.21.11 or newer
- **Java Version:** Java 21+
- **Memory:** 2GB+ recommended (for dynamic world generation)
- **Permissions Plugin:** Optional but recommended

---

## 📖 Additional Resources

- **Paper API Docs:** https://jd.papermc.io/paper/1.21/
- **Kyori Adventure Docs:** https://docs.advntr.dev/
- **MiniMessage Format:** https://docs.advntr.dev/minimessage/format.html
- **Spigot Plugin Tutorial:** https://www.spigotmc.org/wiki/spigot-plugin-development/

## ✅ Agent Working Checklist

When working on this codebase:
- [ ] Build with `./gradlew build` before committing changes
- [ ] Update `plugin.yml` if adding commands or permissions
- [ ] Follow existing code style (4 spaces, camelCase methods, PascalCase classes)
- [ ] Use `PlayerUtils.prefixedMessage()` for all user-facing messages
- [ ] Document new config options in `settings.yml` comments
- [ ] Add permissions to [plugin.yml](src/main/resources/plugin.yml)
- [ ] Update `AGENTS.MD` if architecture changes significantly

---

**Last Updated:** 2025-12-16
**Maintainer:** org.lukeeirl
