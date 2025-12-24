package org.lukeeirl.blockShuffle.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.lukeeirl.blockShuffle.util.StatsManager;

import java.util.*;
import java.util.stream.Collectors;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.*;
import static org.lukeeirl.blockShuffle.util.BlockShuffleUtils.*;

public class FirstToBlockShuffle implements BSGameMode {

    private final PlayerTracker tracker;
    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final SettingsGUI settingsGUI;
    private final WorldService worldService;
    private final CreeperManager creeperManager;
    private final StatsManager stats;
    private final World lobbyWorld;
    private final Random random;

    private List<Material> materials;
    private World currentGameWorld;
    private boolean inProgress;
    private long gameInstanceId;
    private boolean hasHandledWin = false;
    private int creeperSoundTask = -1;

    private BossBar sharedBossBar;
    private long gameStartTime;
    private int blocksToWin;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, Long> blockAssignmentTime = new HashMap<>();
    private final Set<UUID> hasReceivedBlockReminder = new HashSet<>();
    private static final long BLOCK_REMINDER_DELAY = 5 * 60 * 1000; // 5 minutes in milliseconds

    public FirstToBlockShuffle(
            PlayerTracker tracker,
            BlockShuffle plugin,
            YamlConfiguration settings,
            SettingsGUI settingsGUI,
            WorldService worldService,
            World lobbyWorld,
            CreeperManager creeperManager,
            StatsManager stats
    ) {
        this.tracker = tracker;
        this.plugin = plugin;
        this.settings = settings;
        this.settingsGUI = settingsGUI;
        this.worldService = worldService;
        this.lobbyWorld = lobbyWorld;
        this.creeperManager = creeperManager;
        this.stats = stats;
        this.random = new Random();
    }

    @Override
    public void startGame() {
        // Initialize game settings
        BlockShuffle.logger.info("[Game State] FirstTo game started — setInProgress(true) from startGame()");
        this.inProgress = true;
        this.gameInstanceId = System.currentTimeMillis();
        this.hasHandledWin = false;
        this.gameStartTime = System.currentTimeMillis();
        this.blocksToWin = settingsGUI.getBlocksToWin();

        String baseWorldName = "blockshuffle_" + this.gameInstanceId;
        currentGameWorld = worldService.createLinkedWorlds(baseWorldName);
        String materialPath = "materials";
        this.materials = this.settings.getStringList(materialPath).stream().map(Material::getMaterial).collect(Collectors.toList());

        for (UUID uuid : tracker.getReadyPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                resetPlayerState(player, GameMode.SURVIVAL);
                player.teleport(currentGameWorld.getSpawnLocation());

                tracker.addInGame(uuid);
                tracker.getPlayerRounds().put(uuid, 0); // Start at 0 blocks completed
                assignNewBlockToPlayer(uuid, tracker, materials, random);
                blockAssignmentTime.put(uuid, System.currentTimeMillis()); // Track when block was assigned

                // Record stats
                stats.recordPlayed(uuid);
            }
        }
        stats.saveAll();

        // Create single shared boss bar
        this.sharedBossBar = Bukkit.createBossBar("Time: 0 sec", BarColor.BLUE, BarStyle.SOLID);
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                sharedBossBar.addPlayer(player);
            }
        }

        setupScoreboards();

        // Schedule updates every second (20 ticks)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateElapsedTimeBossBar();
            updateScoreboards();
            checkBlockReminders();
        }, 0L, 20L);

        this.scheduleCreeperSound();
    }

    @Override
    public void resetGame() {
        BlockShuffle.logger.info("[Game State] FirstTo game ended — setInProgress(false) from resetGame()");
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

        // Remove shared boss bar
        if (sharedBossBar != null) {
            sharedBossBar.removeAll();
            sharedBossBar = null;
        }

        tracker.clearAll();
        playerScoreboards.clear();
        blockAssignmentTime.clear();
        hasReceivedBlockReminder.clear();

        // Restore offline spectators to the tracking (they'll be cleaned up when they rejoin)
        for (UUID uuid : offlineSpectators) {
            tracker.addSpectator(uuid);
        }

        // Unload and delete the game world
        if (currentGameWorld != null) {
            Bukkit.unloadWorld(currentGameWorld, false);
            worldService.deleteWorld(currentGameWorld);
            currentGameWorld = null;
        }
    }

    @Override
    public void playerStandingOnBlock(Player player) {
        UUID uuid = player.getUniqueId();

        // Increment blocks completed
        int blocksCompleted = tracker.getPlayerRounds().getOrDefault(uuid, 0) + 1;
        tracker.getPlayerRounds().put(uuid, blocksCompleted);

        // Announce progress
        Material assignedBlock = tracker.getUserMaterialMap().get(uuid);
        String blockName = formatMaterialName(assignedBlock);
        Bukkit.broadcast(prefixedMessage(
                Component.text(player.getName() + " ", NamedTextColor.WHITE)
                        .append(Component.text("stood on their block (", NamedTextColor.GREEN))
                        .append(Component.text(blocksCompleted + "/" + blocksToWin, NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text("). Block was: ", NamedTextColor.GREEN))
                        .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD))));

        // Play sound and remove old block
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        tracker.getUserMaterialMap().remove(uuid);

        // Record stat
        stats.recordBlockSteppedOn(uuid);
        stats.save(uuid);

        // Check win condition
        if (blocksCompleted >= blocksToWin) {
            handleWin(uuid);
        } else {
            assignNewBlockToPlayer(uuid, tracker, materials, random);
            blockAssignmentTime.put(uuid, System.currentTimeMillis()); // Reset timer for new block
            hasReceivedBlockReminder.remove(uuid); // Reset reminder flag
        }
    }

    private void handleWin(UUID winnerUuid) {
        if (hasHandledWin) return;
        hasHandledWin = true;

        Player winner = Bukkit.getPlayer(winnerUuid);
        if (winner == null) return;

        // Calculate elapsed time
        long elapsedMillis = System.currentTimeMillis() - gameStartTime;
        long totalSeconds = elapsedMillis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        String timeString = (minutes > 0)
                ? String.format("%d min %d sec", minutes, seconds)
                : String.format("%d sec", seconds);

        // Announce winner
        Bukkit.broadcast(prefixedMessage(
                Component.text(winner.getName(), NamedTextColor.WHITE)
                        .append(Component.text(" won in ", NamedTextColor.GREEN))
                        .append(Component.text(timeString, NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text("!", NamedTextColor.GREEN))));

        // Winner effects
        winner.playSound(winner.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
        for (int i = 0; i < 16; i++) {
            int delay = i * 5;
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    launchFireworkAt(winner.getLocation()), delay);
        }

        // Record win stat
        stats.recordWin(winnerUuid);
        stats.save(winnerUuid);

        // Eliminate all other players (without item chests since everyone loses at once)
        for (UUID uuid : new HashSet<>(tracker.getUsersInGame())) {
            if (!uuid.equals(winnerUuid)) {
                Player loser = Bukkit.getPlayer(uuid);
                if (loser != null) {
                    // Clear inventory instead of dropping in chest
                    loser.getInventory().clear();

                    tracker.addSpectator(uuid);
                    tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);
                    loser.setGameMode(GameMode.SPECTATOR);
                } else {
                    // Handle offline players
                    tracker.addSpectator(uuid);
                    tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);
                    BlockShuffle.logger.info("[Offline Elimination] " + uuid + " eliminated while offline");
                }
                tracker.getUsersInGame().remove(uuid);
                tracker.getUserMaterialMap().remove(uuid);
                tracker.getPlayerRounds().remove(uuid);
                playerScoreboards.remove(uuid);
            }
        }

        // Reset after delay
        Bukkit.getScheduler().runTaskLater(plugin, this::resetGame, 140L);
    }

    private void updateElapsedTimeBossBar() {
        if (sharedBossBar == null) return;

        long elapsedMillis = System.currentTimeMillis() - gameStartTime;
        long totalSeconds = elapsedMillis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        String timeString = (minutes > 0)
                ? String.format("Elapsed: %d min %d sec", minutes, seconds)
                : String.format("Elapsed: %d sec", seconds);

        sharedBossBar.setTitle(timeString);
        sharedBossBar.setProgress(1.0);
    }

    @Override
    public void playerJoined(Player player) {
        UUID uuid = player.getUniqueId();

        // Handle active players
        if (tracker.getUsersInGame().contains(uuid)) {
            if (sharedBossBar != null) {
                sharedBossBar.addPlayer(player);
            }

            Scoreboard scoreboard = playerScoreboards.get(uuid);
            if (scoreboard != null) {
                player.setScoreboard(scoreboard);
            }

            if (currentGameWorld != null) {
                // Only teleport if player is not already in the game world
                if (!player.getWorld().equals(currentGameWorld)) {
                    player.teleport(currentGameWorld.getSpawnLocation());
                }
                player.sendMessage(prefixedMessage(Component.text("You've rejoined the game", NamedTextColor.GREEN)));

                Material material = tracker.getUserMaterialMap().get(uuid);
                if (material != null) {
                    String blockName = formatMaterialName(material);
                    player.sendMessage(prefixedMessage(
                            Component.text("Your block is: ", NamedTextColor.GREEN)
                                    .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD))));

                    // If player was eligible for a new block before logging out, remind them again
                    if (hasReceivedBlockReminder.contains(uuid)) {
                        Component message = prefixedMessage(
                            Component.text("Having trouble finding ", NamedTextColor.YELLOW)
                                .append(Component.text(blockName, NamedTextColor.GOLD, TextDecoration.BOLD))
                                .append(Component.text("? ", NamedTextColor.YELLOW))
                                .append(Component.text("[CLICK HERE]", NamedTextColor.GREEN, TextDecoration.BOLD)
                                    .clickEvent(ClickEvent.runCommand("/blockshuffle newblock")))
                                .append(Component.text(" to get a new random block!", NamedTextColor.YELLOW))
                        );
                        player.sendMessage(message);
                        BlockShuffle.logger.info("[Block Reminder] Re-sent reminder to " + player.getName() + " upon rejoin for block: " + blockName);
                    }
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

                if (sharedBossBar != null) {
                    sharedBossBar.addPlayer(player);
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
        tracker.getPlayerRounds().remove(uuid);

        if (wasInGame) {
            // Strike lightning and drop items
            strikeLightningWithoutFire(player.getLocation());
            boolean hasItems = dropItemsInChest(player);
            announceElimination(uuid, tracker, player.getLocation(), hasItems);

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
            tracker.getPlayerRounds().remove(uuid);

            // Add to spectators
            tracker.addSpectator(uuid);
            tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);

            // Keep scoreboard so they can watch
            Scoreboard scoreboard = playerScoreboards.get(uuid);
            if (scoreboard != null) {
                player.setScoreboard(scoreboard);
            }

            if (sharedBossBar != null) {
                sharedBossBar.addPlayer(player);
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

            if (sharedBossBar != null) {
                sharedBossBar.addPlayer(player);
            }

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
        // Sort by blocks completed (descending)
        List<UUID> sortedPlayers = tracker.getUsersInGame().stream()
                .sorted((a, b) -> {
                    int blocksA = tracker.getPlayerRounds().getOrDefault(a, 0);
                    int blocksB = tracker.getPlayerRounds().getOrDefault(b, 0);
                    return Integer.compare(blocksB, blocksA);
                })
                .toList();

        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            Scoreboard scoreboard = playerScoreboards.get(uuid);
            if (player == null || scoreboard == null) continue;

            org.bukkit.scoreboard.Objective obj = scoreboard.getObjective("status");
            if (obj == null) continue;

            // Clear old entries
            scoreboard.getEntries().forEach(scoreboard::resetScores);

            // Add sorted player entries
            int displayOrder = sortedPlayers.size();
            for (UUID id : sortedPlayers) {
                String name = Bukkit.getOfflinePlayer(id).getName();
                int blocksCompleted = tracker.getPlayerRounds().getOrDefault(id, 0);

                String line = String.format("§f%s: §a%d/%d", name, blocksCompleted, blocksToWin);
                obj.getScore(line).setScore(displayOrder--);
            }

            // Action bar - current block
            Material targetBlock = tracker.getUserMaterialMap().get(uuid);
            if (targetBlock != null) {
                String blockName = formatMaterialName(targetBlock);
                player.sendActionBar(Component.text("Stand on: ", NamedTextColor.GREEN)
                        .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD)));
            }
        }
    }

    private void checkBlockReminders() {
        long now = System.currentTimeMillis();

        for (UUID uuid : tracker.getUsersInGame()) {
            // Skip if player already received reminder for this block
            if (hasReceivedBlockReminder.contains(uuid)) continue;

            Long assignmentTime = blockAssignmentTime.get(uuid);
            if (assignmentTime == null) continue;

            long timeOnBlock = now - assignmentTime;

            // If player has been on the same block for 5+ minutes, send reminder
            if (timeOnBlock >= BLOCK_REMINDER_DELAY) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    Material currentBlock = tracker.getUserMaterialMap().get(uuid);
                    String blockName = formatMaterialName(currentBlock);

                    Component message = prefixedMessage(
                        Component.text("Having trouble finding ", NamedTextColor.YELLOW)
                            .append(Component.text(blockName, NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("? ", NamedTextColor.YELLOW))
                            .append(Component.text("[CLICK HERE]", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/blockshuffle newblock")))
                            .append(Component.text(" to get a new random block!", NamedTextColor.YELLOW))
                    );

                    player.sendMessage(message);
                    hasReceivedBlockReminder.add(uuid);
                    BlockShuffle.logger.info("[Block Reminder] Sent reminder to " + player.getName() + " for block: " + blockName);
                }
            }
        }
    }

    public boolean tryGetNewBlock(UUID uuid) {
        if (!tracker.getUsersInGame().contains(uuid)) return false;
        if (!hasReceivedBlockReminder.contains(uuid)) return false; // Only allow if they received the reminder

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;

        Material oldBlock = tracker.getUserMaterialMap().get(uuid);
        Material newBlock = getRandomMaterial(materials, random);
        tracker.assignBlock(uuid, newBlock);

        String oldBlockName = formatMaterialName(oldBlock);
        String newBlockName = formatMaterialName(newBlock);

        player.sendMessage(prefixedMessage(
            Component.text("You've been assigned a new block: ", NamedTextColor.GREEN)
                .append(Component.text(newBlockName, NamedTextColor.GREEN, TextDecoration.BOLD))
        ));

        // Reset tracking
        blockAssignmentTime.put(uuid, System.currentTimeMillis());
        hasReceivedBlockReminder.remove(uuid);

        BlockShuffle.logger.info(player.getName() + " requested new block after 5min timeout. Old: " + oldBlockName + ", New: " + newBlockName);
        return true;
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
