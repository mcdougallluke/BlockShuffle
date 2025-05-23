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
import org.lukeeirl.blockShuffle.util.SkipManager;
import org.lukeeirl.blockShuffle.util.StatsManager;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.*;

public class ClassicBlockShuffle implements BSGameMode {

    private final PlayerTracker tracker;
    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final SettingsGUI settingsGUI;
    private final WorldService worldService;
    private final SkipManager skipManager;
    private final StatsManager stats;
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

    public ClassicBlockShuffle(PlayerTracker tracker, BlockShuffle plugin, YamlConfiguration settings, SettingsGUI settingsGUI, WorldService worldService, World lobbyWorld, SkipManager skipManager, StatsManager stats) {
        this.tracker = tracker;
        this.plugin = plugin;
        this.settings = settings;
        this.settingsGUI = settingsGUI;
        this.worldService = worldService;
        this.lobbyWorld = lobbyWorld;
        this.skipManager = skipManager;
        this.stats = stats;
        this.random = new Random();
    }

    @Override
    public void startGame() {
        this.inProgress = true;
        this.ticksInRound = settingsGUI.getRoundTimeTicks();
        String baseWorldName = "blockshuffle_" + System.currentTimeMillis();
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
    }

    @Override
    public void resetGame() {
        this.roundNumber = 0;
        inProgress = false;
        BlockShuffle.logger.info("[Game State] Game ended — setInProgress(false) from resetGame()");
        this.bossBar.removeAll();
        Bukkit.getScheduler().cancelTask(this.roundEndTask);
        Bukkit.getScheduler().cancelTask(this.playerUITask);

        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                resetPlayerState(player, GameMode.ADVENTURE);
                player.teleport(lobbyWorld.getSpawnLocation());
            }
        }

        for (UUID uuid : tracker.getSpectators()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                resetPlayerState(player, GameMode.ADVENTURE);
                player.teleport(lobbyWorld.getSpawnLocation());
            }
        }

        tracker.clearAll();

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
        if (tracker.getUsersInGame().contains(uuid)) {
            // They were already in the game, teleport them back and restore state
            if (currentGameWorld != null) {
                //player.teleport(currentGameWorld.getSpawnLocation());
                bossBar.addPlayer(player); // Re-add to the boss bar
                player.sendMessage(prefixedMessage(
                        Component.text("You've rejoined the game", NamedTextColor.GREEN)));
                // Re-send their block task, if it still exists
                Material material = tracker.getUserMaterialMap().get(uuid);
                if (material != null) {
                    String blockName = formatMaterialName(material);
                    player.sendMessage(prefixedMessage(
                            Component.text("Your block is: ", NamedTextColor.GREEN)
                                    .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD))));
                }

            }
        } else {
            // Not in the game anymore, just go to the lobby
            if (lobbyWorld != null) {
                player.teleport(lobbyWorld.getSpawnLocation());
                player.setGameMode(GameMode.ADVENTURE);
            }
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

        Material newBlock = getRandomMaterial();
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
    public void sendPlayerToLobby(Player player) {
        UUID uuid = player.getUniqueId();

        boolean wasInGame = tracker.getUsersInGame().remove(uuid);
        tracker.getSpectators().remove(uuid);
        tracker.getCompletedUsers().remove(uuid);
        tracker.getSkippedPlayers().remove(uuid);

        if (wasInGame) {
            announceElimination(uuid);
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

        if (lobbyWorld != null) {
            resetPlayerState(player, GameMode.ADVENTURE);
            player.teleport(lobbyWorld.getSpawnLocation());
        }
    }

    private void startNewRound() {
        this.roundNumber++;
        this.bossBar.setVisible(true);
        this.roundStartTime = System.currentTimeMillis();

        for (UUID uuid : tracker.getUsersInGame()) {
            assignNewBlockToPlayer(uuid);
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

    private Material getRandomMaterial() {
        Material selectedMaterial = null;
        int attemptCount = 0;

        while (selectedMaterial == null) {
            int randomIndex = this.random.nextInt(this.materials.size());
            selectedMaterial = this.materials.get(randomIndex);

            if (selectedMaterial == null) {
                BlockShuffle.logger.info(String.format(
                        "[ERROR] getRandomMaterial(): Null material at index %d (Attempt #%d). Retrying...",
                        randomIndex,
                        attemptCount
                ));
            } else {
                BlockShuffle.logger.info(String.format(
                        "[DEBUG] getRandomMaterial(): Selected index %d on attempt #%d. Material: %s",
                        randomIndex,
                        attemptCount,
                        selectedMaterial.name()
                ));
            }

            attemptCount++;
        }

        return selectedMaterial;
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

    private void assignNewBlockToPlayer(UUID uuid) {
        Material block = getRandomMaterial();
        tracker.assignBlock(uuid, block);
        BlockShuffle.logger.log(Level.INFO, uuid + " was assigned " + formatMaterialName(block));

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            String blockName = formatMaterialName(block);
            player.sendMessage(prefixedMessage(
                    Component.text("Your new block is: ", NamedTextColor.GREEN)
                            .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD))));
        }
    }


    private void announceElimination(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            Material material = tracker.getUserMaterialMap().get(uuid);

            Component message = prefixedMessage(
                    Component.text(player.getName() + " ", NamedTextColor.WHITE)
                            .append(Component.text("got eliminated!", NamedTextColor.RED))
            );

            // Only show the block if they still had one assigned
            if (material != null) {
                message = prefixedMessage(
                        Component.text(player.getName() + " ", NamedTextColor.WHITE)
                                .append(Component.text("got eliminated! Their block was: ", NamedTextColor.RED))
                                .append(Component.text(formatMaterialName(material), NamedTextColor.RED, TextDecoration.BOLD))
                );
            }

            Bukkit.broadcast(message);
        }
    }

    private void eliminateIncompletePlayers() {
        Iterator<UUID> iterator = tracker.getUsersInGame().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (!tracker.getCompletedUsers().contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    String blockName = formatMaterialName(tracker.getUserMaterialMap().get(uuid));
                    Bukkit.broadcast(prefixedMessage(
                            Component.text(player.getName() + " ", NamedTextColor.WHITE)
                                    .append(Component.text("got eliminated! Their block was: ", NamedTextColor.RED))
                                    .append(Component.text(blockName, NamedTextColor.RED, TextDecoration.BOLD))));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1.0f, 1.0f);
                    tracker.addSpectator(uuid);
                    player.setGameMode(GameMode.SPECTATOR);
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
}
