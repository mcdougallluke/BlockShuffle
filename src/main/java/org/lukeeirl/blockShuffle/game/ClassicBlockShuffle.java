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
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.ui.SettingsGUI;
import org.lukeeirl.blockShuffle.util.CreeperManager;
import org.lukeeirl.blockShuffle.util.SkipManager;
import org.lukeeirl.blockShuffle.util.StatsManager;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.*;
import static org.lukeeirl.blockShuffle.util.BlockShuffleUtils.*;

public class ClassicBlockShuffle implements BSGameMode {

    private final PlayerTracker tracker;
    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final SettingsGUI settingsGUI;
    private final WorldService worldService;
    private final SkipManager skipManager;
    private final StatsManager stats;
    private final CreeperManager creeperManager;
    private final World lobbyWorld;
    private final Random random;

    private int ticksInRound = 6000; // 6000 ticks = 300 sec = 5 min
    private int roundNumber = 0;

    private List<Material> materials;
    private int playerUITask;
    private int roundEndTask;
    private BossBar bossBar;
    private long roundStartTime;
    private World currentGameWorld;
    private boolean inProgress;
    private long gameInstanceId;
    private int creeperSoundTask = -1;

    public ClassicBlockShuffle(PlayerTracker tracker, BlockShuffle plugin, YamlConfiguration settings, SettingsGUI settingsGUI, WorldService worldService, World lobbyWorld, SkipManager skipManager, StatsManager stats, CreeperManager creeperManager) {
        this.tracker = tracker;
        this.plugin = plugin;
        this.settings = settings;
        this.settingsGUI = settingsGUI;
        this.worldService = worldService;
        this.lobbyWorld = lobbyWorld;
        this.skipManager = skipManager;
        this.stats = stats;
        this.creeperManager = creeperManager;
        this.random = new Random();
    }

    @Override
    public void startGame() {
        this.inProgress = true;
        this.gameInstanceId = System.currentTimeMillis();
        this.ticksInRound = settingsGUI.getRoundTimeTicks();
        String baseWorldName = "blockshuffle_" + this.gameInstanceId;
        currentGameWorld = worldService.createLinkedWorlds(baseWorldName);
        String materialPath = "materials";
        this.materials = this.settings.getStringList(materialPath).stream().map(Material::getMaterial).collect(Collectors.toList());

        for (UUID uuid : tracker.getReadyPlayers()) {
            stats.recordPlayed(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                resetPlayerState(player, GameMode.SURVIVAL);
                player.teleport(currentGameWorld.getSpawnLocation());
                tracker.addInGame(uuid);
            }
        }
        stats.saveAll();
        this.bossBar = this.createBossBar();
        this.startNewRound();
        this.playerUITask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::refreshPlayerUI, 0, 20);
        this.scheduleCreeperSound();
    }

    @Override
    public void resetGame() {
        this.roundNumber = 0;
        inProgress = false;
        this.gameInstanceId = 0;
        BlockShuffle.logger.info("[Game State] Game ended — setInProgress(false) from resetGame()");
        this.bossBar.removeAll();
        Bukkit.getScheduler().cancelTask(this.roundEndTask);
        Bukkit.getScheduler().cancelTask(this.playerUITask);
        if (this.creeperSoundTask != -1) {
            Bukkit.getScheduler().cancelTask(this.creeperSoundTask);
            this.creeperSoundTask = -1;
        }

        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                resetPlayerState(player, GameMode.SURVIVAL);
                player.teleport(lobbyWorld.getSpawnLocation());
            }
        }

        // Collect offline spectators before clearing
        Set<UUID> offlineSpectators = new HashSet<>();
        for (UUID uuid : tracker.getSpectators()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                resetPlayerState(player, GameMode.SURVIVAL);
                player.teleport(lobbyWorld.getSpawnLocation());
            } else {
                offlineSpectators.add(uuid);
            }
        }

        tracker.clearAll();

        for (UUID uuid : offlineSpectators) {
            tracker.addSpectator(uuid);
        }

        if (currentGameWorld != null) {
            Bukkit.unloadWorld(currentGameWorld, false);
            worldService.deleteWorld(currentGameWorld);
            currentGameWorld = null;
        }
    }

    @Override
    public void playerStandingOnBlock(Player player) {
        UUID uuid = player.getUniqueId();
        Material assignedBlock = tracker.getUserMaterialMap().get(uuid);

        String blockName = formatMaterialName(assignedBlock);
        Bukkit.broadcast(prefixedMessage(
                Component.text(player.getName() + " ", NamedTextColor.WHITE)
                        .append(Component.text("stood on their block. Their block was: ", NamedTextColor.GREEN))
                        .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD))));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        tracker.addCompleted(uuid);
        stats.recordBlockSteppedOn(uuid);
        stats.save(uuid);
        tracker.getUserMaterialMap().remove(uuid);

        if (tracker.getCompletedUsers().size() == tracker.getUsersInGame().size()) {
            BlockShuffle.logger.info("[Game State] All players completed their block — starting next round");
            Bukkit.getScheduler().cancelTask(this.roundEndTask);
            this.nextRound();
        }
    }

    @Override
    public void playerJoined(Player player) {
        UUID uuid = player.getUniqueId();

        // Handle active players
        if (tracker.getUsersInGame().contains(uuid)) {
            if (currentGameWorld != null) {
                bossBar.addPlayer(player);
                player.sendMessage(prefixedMessage(
                        Component.text("You've rejoined the game", NamedTextColor.GREEN)));

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

            // Check if they were spectating THIS game
            if (spectatorGameId != null && spectatorGameId == this.gameInstanceId && this.inProgress) {
                // Same game still running - restore spectator state
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(prefixedMessage(
                        Component.text("You've rejoined as a spectator", NamedTextColor.YELLOW)));
                BlockShuffle.logger.info("[Spectator Rejoin] " + player.getName() + " rejoined game " + this.gameInstanceId + " as spectator");
            } else {
                // Game ended or different game - cleanup
                tracker.getSpectators().remove(uuid);
                tracker.getSpectatorGameId().remove(uuid);

                if (lobbyWorld != null) {
                    resetPlayerState(player, GameMode.SURVIVAL);
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
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    @Override
    public boolean trySkip(UUID uuid) {
        BlockShuffle.logger.info("[SKIP] Player " + uuid + " is attempting to skip.");

        if (!tracker.getUsersInGame().contains(uuid)) {
            BlockShuffle.logger.info("[SKIP] Player " + uuid + " is not in game. Denying skip.");
            return false;
        }

        if (tracker.getCompletedUsers().contains(uuid)) {
            BlockShuffle.logger.info("[SKIP] Player " + uuid + " already completed their block. Denying skip.");
            return false;
        }

        int usedSkips = tracker.getUsedSkips(uuid);
        int purchasedSkips = skipManager.getPurchasedSkips(uuid);
        BlockShuffle.logger.info("[SKIP] usedSkips=" + usedSkips + ", purchasedSkips=" + purchasedSkips);

        if (usedSkips == 0) {
            // First skip is free
            BlockShuffle.logger.info("[SKIP] First (free) skip being used.");
        } else if (purchasedSkips > 0) {
            // Beyond first, use purchased skip
            BlockShuffle.logger.info("[SKIP] Consuming one purchased skip for " + uuid);
            skipManager.consumeSkip(uuid);
        } else {
            BlockShuffle.logger.info("[SKIP] No purchased skips remaining. Denying skip.");
            return false;
        }

        // Now safe to increment skip count
        tracker.incrementSkips(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            BlockShuffle.logger.warning("[SKIP] Player " + uuid + " not found online. Denying skip.");
            return false;
        }

        Material oldBlock = tracker.getUserMaterialMap().get(uuid);
        if (oldBlock == null) {
            BlockShuffle.logger.warning("[SKIP] Player " + uuid + " has no assigned block. Denying skip.");
            return false;
        }

        Material newBlock = getRandomMaterial(materials, random);
        tracker.assignBlock(uuid, newBlock);
        tracker.addSkipped(uuid);

        String oldBlockName = formatMaterialName(oldBlock);
        String newBlockName = formatMaterialName(newBlock);

        BlockShuffle.logger.info("[SKIP] " + player.getName() + " skipped " + oldBlockName + " → " + newBlockName);

        Bukkit.broadcast(prefixedMessage(
                Component.text(player.getName() + " ", NamedTextColor.WHITE)
                        .append(Component.text("skipped: ", NamedTextColor.YELLOW))
                        .append(Component.text(oldBlockName, NamedTextColor.YELLOW, TextDecoration.BOLD))
        ));
        player.sendMessage(prefixedMessage(
                Component.text("Your new block is: ", NamedTextColor.GREEN)
                        .append(Component.text(newBlockName, NamedTextColor.GREEN, TextDecoration.BOLD))));

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
    public void sendPlayerToLobby(Player player) {
        UUID uuid = player.getUniqueId();

        boolean wasInGame = tracker.getUsersInGame().remove(uuid);
        boolean wasSpectator = tracker.getSpectators().remove(uuid);
        tracker.getSpectatorGameId().remove(uuid);  // Clear game ID tracking
        tracker.getCompletedUsers().remove(uuid);
        tracker.getSkippedPlayers().remove(uuid);

        if (wasInGame) {
            // Strike lightning at elimination location
            strikeLightningWithoutFire(player.getLocation());

            // Drop items in chest
            boolean hasItems = dropItemsInChest(player);

            // Announce elimination with coordinates if items were dropped
            announceElimination(uuid, tracker, player.getLocation(), hasItems);

            tracker.getUserMaterialMap().remove(uuid);

            if (tracker.getUsersInGame().size() == 1) {
                UUID winnerUUID = tracker.getUsersInGame().iterator().next();
                tracker.getCompletedUsers().add(winnerUUID);
                announceWinnersAndReset();
                Bukkit.getScheduler().cancelTask(this.roundEndTask);
            } else if (tracker.getCompletedUsers().containsAll(tracker.getUsersInGame())) {
                Bukkit.getScheduler().cancelTask(roundEndTask);
                nextRound();
            }
        }

        if (wasSpectator) {
            player.sendMessage(prefixedMessage(
                    Component.text("You have left spectator mode", NamedTextColor.GRAY)));
            BlockShuffle.logger.info("[Lobby] Spectator " + player.getName() + " left spectating via /lobby command");
        }

        if (lobbyWorld != null) {
            resetPlayerState(player, GameMode.SURVIVAL);
            player.teleport(lobbyWorld.getSpawnLocation());
        }
    }

    @Override
    public void enterSpectatorMode(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if player is in the active game
        boolean wasInGame = tracker.getUsersInGame().contains(uuid);

        if (wasInGame) {
            strikeLightningWithoutFire(player.getLocation());
            boolean hasItems = dropItemsInChest(player);
            announceElimination(uuid, tracker, player.getLocation(), hasItems);

            // Remove from active game
            tracker.getUsersInGame().remove(uuid);
            tracker.getCompletedUsers().remove(uuid);
            tracker.getSkippedPlayers().remove(uuid);
            tracker.getUserMaterialMap().remove(uuid);

            // Add to spectators with game ID
            tracker.addSpectator(uuid);
            tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);
            player.setGameMode(GameMode.SPECTATOR);

            player.sendMessage(prefixedMessage(
                    Component.text("You are now spectating", NamedTextColor.YELLOW)));

            BlockShuffle.logger.info("[Spectate] " + player.getName() + " forfeited and became spectator of game " + this.gameInstanceId);

            // Check for auto-win (only 1 player left)
            if (tracker.getUsersInGame().size() == 1) {
                UUID winnerUUID = tracker.getUsersInGame().iterator().next();
                tracker.getCompletedUsers().add(winnerUUID);
                announceWinnersAndReset();
                Bukkit.getScheduler().cancelTask(this.roundEndTask);
            }
            // Check if all remaining players completed their blocks
            else if (tracker.getCompletedUsers().containsAll(tracker.getUsersInGame())) {
                Bukkit.getScheduler().cancelTask(roundEndTask);
                nextRound();
            }
        } else if (tracker.getSpectators().contains(uuid)) {
            // Already spectating - send message
            player.sendMessage(prefixedMessage(
                    Component.text("You are already spectating", NamedTextColor.GRAY)));
        } else {
            tracker.addSpectator(uuid);
            tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);
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

    private void startNewRound() {
        this.roundNumber++;
        this.bossBar.setVisible(true);
        this.roundStartTime = System.currentTimeMillis();

        for (UUID uuid : tracker.getUsersInGame()) {
            assignNewBlockToPlayer(uuid, tracker, materials, random);
        }

        this.roundEndTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, this::nextRound, this.ticksInRound);
    }

    private void nextRound() {
        if (tracker.getCompletedUsers().size() == 1) {
            eliminateIncompletePlayers();
            announceWinnersAndReset();
            return;
        } else {
            if (!tracker.getCompletedUsers().isEmpty()) {
                eliminateIncompletePlayers();
                if (settingsGUI.isDecreaseTime() && this.ticksInRound > 1200) { this.ticksInRound -= 200; }
            } else {
                broadcastUnfoundBlocks();
            }
        }
        tracker.getCompletedUsers().clear();

        startNewRound();
    }

    private BossBar createBossBar() {
        BossBar bossBar = Bukkit.createBossBar("Round: " + roundNumber + " | Time: XXX", BarColor.GREEN, BarStyle.SOLID);
        bossBar.setColor(BarColor.GREEN);
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                bossBar.addPlayer(player);
            }
        }
        return bossBar;
    }

    private void refreshPlayerUI() {
        long timeSinceRoundStart = System.currentTimeMillis() - this.roundStartTime;
        long millisInRound = ((this.ticksInRound) / 20) * 1000L;
        long millisRemaining = millisInRound - timeSinceRoundStart;

        if (millisRemaining < 0) millisRemaining = 0;

        double progress = millisRemaining / (double) millisInRound;

        long secondsRemaining = millisRemaining / 1000;
        long minutes = secondsRemaining / 60;
        long seconds = secondsRemaining % 60;

        // Format the time string
        String timeString;
        if (minutes > 0) {
            timeString = String.format("%d min %d sec", minutes, seconds);
        } else {
            timeString = String.format("%d sec", seconds);
        }

        // Update the boss bar text title and progress
        this.bossBar.setTitle("Round: " + roundNumber + " | Time: " + timeString);
        this.bossBar.setProgress(progress);

        // Show countdown title to players with less than 5 seconds remaining
        if (secondsRemaining <= 5) {
            for (UUID uuid : tracker.getUsersInGame()) {
                if (!tracker.getCompletedUsers().contains(uuid)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        player.showTitle(Title.title(
                                Component.text(String.valueOf(secondsRemaining), NamedTextColor.RED),
                                Component.empty(),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                        ));
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
                    }
                }
            }
        }

        // Update action bar for players in the game (text above hotbar)
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            if (tracker.getCompletedUsers().contains(uuid)) {
                player.sendActionBar(Component.text("Waiting for next round...", NamedTextColor.YELLOW));
            } else {
                Material targetBlock = tracker.getUserMaterialMap().get(uuid);
                String blockName = formatMaterialName(targetBlock);
                player.sendActionBar(Component.text("Stand on: ", NamedTextColor.GREEN)
                        .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD)));
            }
        }
    }

    private void eliminateIncompletePlayers() {
        Iterator<UUID> iterator = tracker.getUsersInGame().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (!tracker.getCompletedUsers().contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);

                if (player != null) {
                    // Player is online - apply full elimination effects
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1.0f, 1.0f);

                    // Strike lightning at elimination location
                    strikeLightningWithoutFire(player.getLocation());

                    // Drop items in chest and get location
                    boolean hasItems = dropItemsInChest(player);

                    // Announce elimination with coordinates if items were dropped
                    announceElimination(uuid, tracker, player.getLocation(), hasItems);

                    tracker.addSpectator(uuid);
                    tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);
                    player.setGameMode(GameMode.SPECTATOR);
                } else {
                    // Player is offline - still announce elimination and track as spectator
                    announceElimination(uuid, tracker, null, false);

                    tracker.addSpectator(uuid);
                    tracker.getSpectatorGameId().put(uuid, this.gameInstanceId);

                    BlockShuffle.logger.info("[Offline Elimination] " + uuid + " was eliminated while offline and will rejoin as spectator");
                }

                iterator.remove();
                tracker.getUserMaterialMap().remove(uuid);
            }
        }
    }

    private void broadcastUnfoundBlocks() {
        for (UUID uuid : tracker.getUsersInGame()) {
            if (!tracker.getCompletedUsers().contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    String blockName = formatMaterialName(tracker.getUserMaterialMap().get(uuid));
                    Bukkit.broadcast(prefixedMessage(
                            Component.text(player.getName() + " ", NamedTextColor.WHITE)
                                    .append(Component.text("had: ", NamedTextColor.RED))
                                    .append(Component.text(blockName, NamedTextColor.RED, TextDecoration.BOLD))
                    ));
                }
            }
        }
        Bukkit.broadcast(prefixedMessage(
                Component.text("Nobody stood on their block", NamedTextColor.RED)
        ));
    }

    private void announceWinnersAndReset() {
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Bukkit.broadcast(prefixedMessage(
                        Component.text(player.getName() + " ", NamedTextColor.WHITE)
                                .append(Component.text("won the game!", NamedTextColor.GREEN))));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
                stats.recordWin(uuid);
                stats.save(uuid);
            }
        }

        for (UUID uuid : tracker.getCompletedUsers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                for (int i = 0; i < 16; i++) {
                    int delay = i * 5;
                    Bukkit.getScheduler().runTaskLater(this.plugin, () -> launchFireworkAt(player.getLocation()), delay);
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(this.plugin, this::resetGame, 140L);
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
