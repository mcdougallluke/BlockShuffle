package org.lukeeirl.blockShuffle.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.ui.SettingsGUI;
import org.lukeeirl.blockShuffle.util.CreeperManager;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.*;
import static org.lukeeirl.blockShuffle.util.BlockShuffleUtils.*;

public class ContinuousBlockShuffle implements BSGameMode {

    private final PlayerTracker tracker;
    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final SettingsGUI settingsGUI;
    private final WorldService worldService;
    private final WorldPoolService worldPoolService;
    private final CreeperManager creeperManager;
    private final World lobbyWorld;
    private final Random random;

    private int ticksInRound = 6000;
    private List<Material> materials;
    private World currentGameWorld;
    private WorldPoolService.PooledWorld currentPooledWorld;
    private boolean inProgress;
    private long gameInstanceId;
    private boolean hasHandledWin = false;
    private int creeperSoundTask = -1;

    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private static final long MAX_TIME_MILLIS = 15 * 60 * 1000; // 15 minutes
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    public ContinuousBlockShuffle(
            PlayerTracker tracker,
            BlockShuffle plugin,
            YamlConfiguration settings,
            SettingsGUI settingsGUI,
            WorldService worldService,
            World lobbyWorld,
            CreeperManager creeperManager,
            WorldPoolService worldPoolService
    ) {
        this.tracker = tracker;
        this.plugin = plugin;
        this.settings = settings;
        this.settingsGUI = settingsGUI;
        this.worldService = worldService;
        this.worldPoolService = worldPoolService;
        this.lobbyWorld = lobbyWorld;
        this.creeperManager = creeperManager;
        this.random = new Random();
    }

    @Override
    public void startGame() {
        // Initialize game settings
        BlockShuffle.logger.info("[Game State] Continuous game started — setInProgress(true) from startGame()");
        this.inProgress = true;
        this.gameInstanceId = System.currentTimeMillis();
        this.hasHandledWin = false;

        this.ticksInRound = settingsGUI.getRoundTimeTicks();

        // Try to get world from pool first
        if (worldPoolService != null) {
            currentPooledWorld = worldPoolService.getReadyWorld();
        }

        if (currentPooledWorld != null) {
            currentGameWorld = currentPooledWorld.getOverworld();
            plugin.getLogger().info("[World Pool] Using pre-generated world for Continuous mode");
        } else {
            String baseWorldName = "blockshuffle_" + this.gameInstanceId;
            currentGameWorld = worldService.createLinkedWorlds(baseWorldName);
            plugin.getLogger().warning("[World Pool] No pooled world available, created new world");
        }

        String materialPath = "materials";
        this.materials = this.settings.getStringList(materialPath).stream().map(Material::getMaterial).collect(Collectors.toList());

        long now = System.currentTimeMillis();
        int roundTimeMillis = ticksInRound * 50;

        for (UUID uuid : tracker.getReadyPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                resetPlayerState(player, GameMode.SURVIVAL);
                player.teleport(currentGameWorld.getSpawnLocation());

                tracker.addInGame(uuid);
                tracker.getPlayerRounds().put(uuid, 1);
                tracker.getPlayerEndTime().put(uuid, now + roundTimeMillis);
                assignNewBlockToPlayer(uuid, tracker, materials, random);
                BossBar bossBar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
                bossBar.addPlayer(player);
                playerBossBars.put(uuid, bossBar);

            }
        }
        setupScoreboards();

        Bukkit.getScheduler().runTaskTimer(plugin, this::checkForTimeouts, 0L, 20L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkForTimeouts();
            updateScoreboards();
        }, 0L, 20L);

        this.scheduleCreeperSound();
    }

    @Override
    public void resetGame() {
        BlockShuffle.logger.info("[Game State] Continuous game ended — setInProgress(false) from resetGame()");
        inProgress = false;
        this.gameInstanceId = 0;
        this.hasHandledWin = false;

        if (this.creeperSoundTask != -1) {
            Bukkit.getScheduler().cancelTask(this.creeperSoundTask);
            this.creeperSoundTask = -1;
        }

        // Send all players in the game back to lobby
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                resetPlayerState(player, GameMode.ADVENTURE);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                player.teleport(lobbyWorld.getSpawnLocation());
            }
        }
        // Collect offline spectators before clearing
        Set<UUID> offlineSpectators = new HashSet<>();
        for (UUID uuid : tracker.getSpectators()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                // Online spectator - teleport to lobby
                resetPlayerState(player, GameMode.ADVENTURE);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                player.teleport(lobbyWorld.getSpawnLocation());
            } else {
                // Offline spectator - preserve for cleanup on rejoin
                offlineSpectators.add(uuid);
            }
        }

        for (BossBar bar : playerBossBars.values()) {
            bar.removeAll(); // Removes all players and hides the bar
        }
        playerBossBars.clear();


        tracker.clearAll();
        playerScoreboards.clear();

        // Restore offline spectators to the tracking (they'll be cleaned up when they rejoin)
        for (UUID uuid : offlineSpectators) {
            tracker.addSpectator(uuid);
            // spectatorGameId was cleared by clearAll(), which is fine - cleanup will work without it
        }

        // Delete used world (worlds are never recycled - each game gets fresh seed)
        if (currentPooledWorld != null && worldPoolService != null) {
            plugin.getLogger().info("[World Pool] Deleting used world: " + currentPooledWorld.getBaseName());
            worldPoolService.deleteUsedWorld(currentPooledWorld);
            currentPooledWorld = null;
            currentGameWorld = null;
        } else if (currentGameWorld != null) {
            plugin.getLogger().info("[World Cleanup] Deleting non-pooled world: " + currentGameWorld.getName());
            Bukkit.unloadWorld(currentGameWorld, false);
            worldService.deleteWorld(currentGameWorld);
            currentGameWorld = null;
        }

    }

    @Override
    public void playerStandingOnBlock(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long endTime = tracker.getPlayerEndTime().getOrDefault(uuid, now);
        long timeLeft = Math.max(0, endTime - now);
        int roundTimeMillis = settingsGUI.getRoundTimeTicks() * 50;
        int newRound = tracker.getPlayerRounds().getOrDefault(uuid, 1) + 1;
        tracker.getPlayerRounds().put(uuid, newRound);
        long maxEndTime = now + MAX_TIME_MILLIS;
        long newEndTime = now + roundTimeMillis + timeLeft;
        tracker.getPlayerEndTime().put(uuid, Math.min(newEndTime, maxEndTime));

        Material assignedBlock = tracker.getUserMaterialMap().get(uuid);
        String blockName = formatMaterialName(assignedBlock);
        Bukkit.broadcast(prefixedMessage(
                Component.text(player.getName() + " ", NamedTextColor.WHITE)
                        .append(Component.text("stood on their block. Their block was: ", NamedTextColor.GREEN))
                        .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD))));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        tracker.getUserMaterialMap().remove(uuid);
        assignNewBlockToPlayer(uuid, tracker, materials, random);
    }

    @Override
    public void playerJoined(Player player) {
        UUID uuid = player.getUniqueId();

        // Handle active players
        if (tracker.getUsersInGame().contains(uuid)) {
            BossBar bossBar = playerBossBars.get(uuid);
            if (bossBar != null) {
                bossBar.addPlayer(player);
            }

            Scoreboard scoreboard = playerScoreboards.get(uuid);
            if (scoreboard != null) {
                player.setScoreboard(scoreboard);
            }

            if (currentGameWorld != null) {
                player.teleport(currentGameWorld.getSpawnLocation());
                player.sendMessage(prefixedMessage(Component.text("You've rejoined the game", NamedTextColor.GREEN)));

                Material material = tracker.getUserMaterialMap().get(uuid);
                if (material != null) {
                    String blockName = formatMaterialName(material);
                    player.sendMessage(prefixedMessage(
                            Component.text("Your block is: ", NamedTextColor.GREEN)
                                    .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD))));
                }
            }
            return;
        }

        // Handle spectators
        if (tracker.getSpectators().contains(uuid)) {
            Long spectatorGameId = tracker.getSpectatorGameId().get(uuid);

            if (spectatorGameId != null && spectatorGameId == this.gameInstanceId && this.inProgress) {
                // Same game still running - restore spectator state
                player.setGameMode(GameMode.SPECTATOR);

                Scoreboard scoreboard = playerScoreboards.get(uuid);
                if (scoreboard != null) {
                    player.setScoreboard(scoreboard);
                }

                player.sendMessage(prefixedMessage(
                        Component.text("You've rejoined as a spectator", NamedTextColor.YELLOW)));
                BlockShuffle.logger.info("[Spectator Rejoin] " + player.getName() + " rejoined game " + this.gameInstanceId + " as spectator");
            } else {
                // Game ended - cleanup
                tracker.getSpectators().remove(uuid);
                tracker.getSpectatorGameId().remove(uuid);

                if (lobbyWorld != null) {
                    resetPlayerState(player, GameMode.ADVENTURE);
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    player.teleport(lobbyWorld.getSpawnLocation());
                    player.sendMessage(prefixedMessage(
                            Component.text("The game you were spectating has ended", NamedTextColor.GRAY)));
                }
                BlockShuffle.logger.info("[Spectator Cleanup] " + player.getName() + " cleaned up (was watching game " + spectatorGameId + ", current is " + this.gameInstanceId + ")");
            }
            return;
        }

        // Not in game or spectators - send to lobby
        if (lobbyWorld != null) {
            player.teleport(lobbyWorld.getSpawnLocation());
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    @Override
    public void sendPlayerToLobby(Player player) {
        UUID uuid = player.getUniqueId();

        boolean wasInGame = tracker.getUsersInGame().remove(uuid);
        boolean wasSpectator = tracker.getSpectators().remove(uuid);
        tracker.getSpectatorGameId().remove(uuid);
        tracker.getUserMaterialMap().remove(uuid);
        tracker.getPlayerEndTime().remove(uuid);
        tracker.getPlayerRounds().remove(uuid);

        if (wasInGame) {
            // Strike lightning and drop items
            strikeLightningWithoutFire(player.getLocation());
            boolean hasItems = dropItemsInChest(player);
            announceElimination(uuid, tracker, player.getLocation(), hasItems);

            // Remove boss bar
            BossBar bossBar = playerBossBars.remove(uuid);
            if (bossBar != null) {
                bossBar.removeAll();
            }

            // Remove from scoreboard tracking
            playerScoreboards.remove(uuid);

            // Check for auto-win
            if (!hasHandledWin && tracker.getUsersInGame().size() == 1) {
                hasHandledWin = true;
                UUID winner = tracker.getUsersInGame().iterator().next();
                Player winnerPlayer = Bukkit.getPlayer(winner);
                if (winnerPlayer != null) {
                    Bukkit.broadcast(prefixedMessage(
                            Component.text(winnerPlayer.getName(), NamedTextColor.WHITE)
                                    .append(Component.text(" won the game!", NamedTextColor.GREEN))));
                    winnerPlayer.playSound(winnerPlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);

                    for (int i = 0; i < 16; i++) {
                        int delay = i * 5;
                        Bukkit.getScheduler().runTaskLater(plugin, () ->
                                launchFireworkAt(winnerPlayer.getLocation()), delay);
                    }
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 140L);
            }
        }

        if (wasSpectator) {
            // Remove scoreboard
            playerScoreboards.remove(uuid);

            player.sendMessage(prefixedMessage(
                    Component.text("You have left spectator mode", NamedTextColor.GRAY)));
            BlockShuffle.logger.info("[Lobby] Spectator " + player.getName() + " left spectating via /lobby command");
        }

        if (lobbyWorld != null) {
            resetPlayerState(player, GameMode.ADVENTURE);
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            player.teleport(lobbyWorld.getSpawnLocation());
        }
    }

    @Override
    public boolean trySkip(UUID uuid) {
        if (!tracker.getUsersInGame().contains(uuid)) return false;
        if (tracker.getSkippedPlayers().contains(uuid)) return false;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;

        Material oldBlock = tracker.getUserMaterialMap().get(uuid);
        Material newBlock = getRandomMaterial(materials, random);
        tracker.assignBlock(uuid, newBlock);
        tracker.addSkipped(uuid);

        String oldBlockName = formatMaterialName(oldBlock);
        String newBlockName = formatMaterialName(newBlock);

        Bukkit.broadcast(prefixedMessage(
                Component.text(player.getName() + " ", NamedTextColor.WHITE)
                        .append(Component.text("skipped: ", NamedTextColor.YELLOW))
                        .append(Component.text(oldBlockName, NamedTextColor.YELLOW, TextDecoration.BOLD))
        ));
        player.sendMessage(prefixedMessage(
                Component.text("Your new block is: ", NamedTextColor.GREEN)
                        .append(Component.text(newBlockName, NamedTextColor.GREEN, TextDecoration.BOLD))));
        BlockShuffle.logger.info(player.getName() + " skipped " + oldBlockName + " and received " + newBlockName);
        return true;
    }

    @Override
    public boolean isInProgress() {
        return this.inProgress;
    }

    @Override
    public World getCurrentGameWorld() {
        return this.currentGameWorld;
    }

    @Override
    public long getGameInstanceId() {
        return this.gameInstanceId;
    }

    @Override
    public void enterSpectatorMode(Player player) {
        UUID uuid = player.getUniqueId();

        boolean wasInGame = tracker.getUsersInGame().contains(uuid);

        if (wasInGame) {
            // Active player forfeiting - apply FULL elimination effects

            strikeLightningWithoutFire(player.getLocation());
            boolean hasItems = dropItemsInChest(player);

            // Announce elimination (same as regular elimination)
            announceElimination(uuid, tracker, player.getLocation(), hasItems);

            // Remove from game tracking
            tracker.getUsersInGame().remove(uuid);
            tracker.getUserMaterialMap().remove(uuid);
            tracker.getPlayerEndTime().remove(uuid);
            tracker.getPlayerRounds().remove(uuid);

            // Remove boss bar
            BossBar bossBar = playerBossBars.remove(uuid);
            if (bossBar != null) {
                bossBar.removeAll();
            }

            // Add to spectators
            tracker.addSpectator(uuid);
            tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);

            // Keep scoreboard so they can watch
            Scoreboard scoreboard = playerScoreboards.get(uuid);
            if (scoreboard != null) {
                player.setScoreboard(scoreboard);
            }

            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(prefixedMessage(
                    Component.text("You are now spectating", NamedTextColor.YELLOW)));

            BlockShuffle.logger.info("[Spectate] " + player.getName() + " forfeited and became spectator of game " + this.gameInstanceId);

            // Check for auto-win
            if (!hasHandledWin && tracker.getUsersInGame().size() == 1) {
                hasHandledWin = true;
                UUID winner = tracker.getUsersInGame().iterator().next();
                Player winnerPlayer = Bukkit.getPlayer(winner);
                if (winnerPlayer != null) {
                    Bukkit.broadcast(prefixedMessage(
                            Component.text(winnerPlayer.getName(), NamedTextColor.WHITE)
                                    .append(Component.text(" won the game!", NamedTextColor.GREEN))));
                    winnerPlayer.playSound(winnerPlayer.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);

                    for (int i = 0; i < 16; i++) {
                        int delay = i * 5;
                        Bukkit.getScheduler().runTaskLater(plugin, () ->
                                launchFireworkAt(winnerPlayer.getLocation()), delay);
                    }
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 140L);
            }
        } else if (tracker.getSpectators().contains(uuid)) {
            player.sendMessage(prefixedMessage(
                    Component.text("You are already spectating", NamedTextColor.GRAY)));
        } else {
            // New spectator joining
            tracker.addSpectator(uuid);
            tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);

            // Give them the scoreboard so they can see player status
            Scoreboard spectatorBoard = Bukkit.getScoreboardManager().getNewScoreboard();
            // Copy the scoreboard from existing players if available
            if (!playerScoreboards.isEmpty()) {
                UUID firstPlayer = playerScoreboards.keySet().iterator().next();
                spectatorBoard = playerScoreboards.get(firstPlayer);
            }
            playerScoreboards.put(uuid, spectatorBoard);
            player.setScoreboard(spectatorBoard);

            player.setGameMode(GameMode.SPECTATOR);

            // Teleport to game world
            if (currentGameWorld != null) {
                player.teleport(currentGameWorld.getSpawnLocation());
            }

            player.sendMessage(prefixedMessage(
                    Component.text("You are now spectating", NamedTextColor.YELLOW)));

            BlockShuffle.logger.info("[Spectate] " + player.getName() + " joined as spectator of game " + this.gameInstanceId);
        }
    }

    private void checkForTimeouts() {
        long now = System.currentTimeMillis();

        // Get the max round reached
        int maxRound = tracker.getPlayerRounds().values().stream().max(Integer::compareTo).orElse(1);
        List<UUID> toEliminate = new ArrayList<>();
        List<UUID> toReset = new ArrayList<>();

        for (UUID uuid : tracker.getUsersInGame()) {
            long endTime = tracker.getPlayerEndTime().getOrDefault(uuid, now);
            if (now >= endTime) {
                int round = tracker.getPlayerRounds().getOrDefault(uuid, 1);
                if (round < maxRound) {
                    toEliminate.add(uuid);
                } else {
                    toReset.add(uuid);
                }
            }
        }

        if (!toEliminate.isEmpty()) {
            for (UUID uuid : toEliminate) {
                Player player = Bukkit.getPlayer(uuid);

                if (player != null) {
                    // Player is online - apply full elimination effects
                    // Strike lightning at elimination location
                    strikeLightningWithoutFire(player.getLocation());

                    // Drop items in chest
                    boolean hasItems = dropItemsInChest(player);

                    // Announce elimination with coordinates if items were dropped
                    announceElimination(uuid, tracker, player.getLocation(), hasItems);

                    tracker.addSpectator(uuid);
                    tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);
                    tracker.getUsersInGame().remove(uuid);
                    tracker.getUserMaterialMap().remove(uuid);
                    player.setGameMode(GameMode.SPECTATOR);
                } else {
                    // Player is offline - still announce elimination and track as spectator
                    announceElimination(uuid, tracker, null, false);

                    tracker.addSpectator(uuid);
                    tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);
                    tracker.getUsersInGame().remove(uuid);
                    tracker.getUserMaterialMap().remove(uuid);

                    BlockShuffle.logger.info("[Offline Elimination] " + uuid + " was eliminated while offline and will rejoin as spectator");
                }
            }
        }

        if (!toReset.isEmpty()) {
            long newStartTime = System.currentTimeMillis();
            long newEndTime = newStartTime + settingsGUI.getRoundTimeTicks() * 50L;

            Bukkit.broadcast(prefixedMessage(
                    Component.text("Nobody stood on their block", NamedTextColor.RED)
            ));
            for (UUID uuid : toReset) {
                tracker.getPlayerEndTime().put(uuid, newEndTime);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    String blockName = formatMaterialName(tracker.getUserMaterialMap().get(uuid));
                    Bukkit.broadcast(prefixedMessage(
                            Component.text(player.getName() + " ", NamedTextColor.WHITE)
                                    .append(Component.text("had: ", NamedTextColor.RED))
                                    .append(Component.text(blockName, NamedTextColor.RED, TextDecoration.BOLD))
                    ));
                }
                assignNewBlockToPlayer(uuid, tracker, materials, random);
            }
        }

        if (!hasHandledWin && tracker.getUsersInGame().size() == 1) {
            hasHandledWin = true;

            UUID winner = tracker.getUsersInGame().iterator().next();
            Player player = Bukkit.getPlayer(winner);
            if (player != null) {
                Bukkit.broadcast(prefixedMessage(
                        Component.text(player.getName(), NamedTextColor.WHITE)
                                .append(Component.text(" won the game!", NamedTextColor.GREEN))));

                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);

                for (int i = 0; i < 16; i++) {
                    int delay = i * 5;
                    Bukkit.getScheduler().runTaskLater(plugin, () ->
                            launchFireworkAt(player.getLocation()), delay);
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 140L);
        }

    }

    private void setupScoreboards() {
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            org.bukkit.scoreboard.Objective objective = scoreboard.registerNewObjective("status", "dummy", blockShuffleTitle());
            objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);

            player.setScoreboard(scoreboard);
            playerScoreboards.put(uuid, scoreboard);
        }
    }

    private void updateScoreboards() {
        long now = System.currentTimeMillis();
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            org.bukkit.scoreboard.Scoreboard scoreboard = playerScoreboards.get(uuid);
            if (player == null || scoreboard == null) continue;

            org.bukkit.scoreboard.Objective obj = scoreboard.getObjective("status");
            if (obj == null) continue;

            // Clear old entries
            scoreboard.getEntries().forEach(scoreboard::resetScores);

            // Sort by highest round, then remaining time
            List<UUID> sortedPlayers = tracker.getUsersInGame().stream()
                    .sorted((a, b) -> {
                        int roundA = tracker.getPlayerRounds().getOrDefault(a, 1);
                        int roundB = tracker.getPlayerRounds().getOrDefault(b, 1);
                        if (roundA != roundB) return Integer.compare(roundB, roundA);

                        long timeA = tracker.getPlayerEndTime().getOrDefault(a, now) - now;
                        long timeB = tracker.getPlayerEndTime().getOrDefault(b, now) - now;
                        return Long.compare(timeB, timeA);
                    })
                    .toList();

            for (UUID id : sortedPlayers) {
                String name = Bukkit.getOfflinePlayer(id).getName();
                long millisLeft = tracker.getPlayerEndTime().getOrDefault(id, now) - now;

                long totalSeconds = Math.max(0, millisLeft / 1000);
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;

                String line;
                if (minutes > 0) {
                    line = String.format("§f%s: §a%dm %ds", name, minutes, seconds);
                } else {
                    line = String.format("§f%s: §a%ds", name, seconds);
                }
                obj.getScore(line).setScore(sortedPlayers.indexOf(id));

                if (totalSeconds <= 5) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        p.showTitle(Title.title(
                                Component.text(String.valueOf(totalSeconds), NamedTextColor.RED),
                                Component.empty(),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                        ));
                        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
                    }
                }
            }

            // Text above the hot bar display current block
            Material targetBlock = tracker.getUserMaterialMap().get(uuid);
            String blockName = formatMaterialName(targetBlock);
            player.sendActionBar(Component.text("Stand on: ", NamedTextColor.GREEN)
                    .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD)));

            // Update everyone's bossbar timer
            BossBar bossBar = playerBossBars.get(uuid);
            if (bossBar != null) {
                long endTime = tracker.getPlayerEndTime().getOrDefault(uuid, now);
                long timeLeftMillis = Math.max(0, endTime - now);
                double progress = timeLeftMillis / (ticksInRound * 50.0);
                progress = Math.max(0.0, Math.min(1.0, progress));
                bossBar.setProgress(progress);

                long totalSeconds = timeLeftMillis / 1000;
                long minutes = totalSeconds / 60;
                long seconds = totalSeconds % 60;

                String timeString = (minutes > 0)
                        ? String.format("%d min %d sec", minutes, seconds)
                        : String.format("%d sec", seconds);

                int round = tracker.getPlayerRounds().getOrDefault(uuid, 1);
                bossBar.setTitle("Round: " + round + " | Time: " + timeString);
            }
        }
    }

    private void scheduleCreeperSound() {
        // Check if any creeper players are in the game
        boolean hasCreeperPlayer = false;
        for (UUID uuid : tracker.getUsersInGame()) {
            if (creeperManager.isCreeper(uuid)) {
                hasCreeperPlayer = true;
                break;
            }
        }

        if (hasCreeperPlayer) {
            // Random delay between 5-10 minutes (6000-12000 ticks)
            int minTicks = 6000; // 5 minutes
            int maxTicks = 12000; // 10 minutes
            int randomDelay = minTicks + random.nextInt(maxTicks - minTicks + 1);

            this.creeperSoundTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, () -> {
                // Play sound for all creeper players in the game
                for (UUID uuid : tracker.getUsersInGame()) {
                    if (creeperManager.isCreeper(uuid)) {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null && player.isOnline() && inProgress) {
                            player.playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.0f);
                            BlockShuffle.logger.info("[Creeper Sound] Played creeper hiss for " + player.getName());
                        }
                    }
                }
                // Schedule next sound if game is still in progress
                if (inProgress) {
                    scheduleCreeperSound();
                }
            }, randomDelay);
        }
    }
}
