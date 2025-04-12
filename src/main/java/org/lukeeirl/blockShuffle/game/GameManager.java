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

import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.*;
import static org.lukeeirl.blockShuffle.util.PlayerUtils.formatMaterialName;

public class GameManager {
    private final World lobbyWorld;
    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final Random random;
    private final WorldService worldService;
    private final PlayerTracker tracker;
    private final SettingsGUI settingsGUI;

    private int ticksInRound = 6000; // 6000 ticks = 300 sec = 5 min
    private List<Material> materials;
    private int bossBarTask;
    private int roundEndTask;
    private BossBar bossBar;
    private long roundStartTime;
    private World currentGameWorld;
    private int roundNumber = 0;
    private boolean inProgress;

    public GameManager(PlayerTracker tracker, BlockShuffle plugin, YamlConfiguration settings, SettingsGUI settingsGUI) {
        this.tracker = tracker;
        this.plugin = plugin;
        this.lobbyWorld = Bukkit.getWorlds().getFirst();
        this.settings = settings;
        this.settingsGUI = settingsGUI;
        this.random = new Random();
        this.worldService = new WorldService();
    }

    public void startGame() {
        this.ticksInRound = settingsGUI.getRoundTimeTicks();
        currentGameWorld = worldService.createNewWorld();
        String materialPath = "materials";
        this.materials = this.settings.getStringList(materialPath).stream().map(Material::getMaterial).collect(Collectors.toList());

        for (UUID uuid : tracker.getReadyPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                resetPlayerState(player, GameMode.SURVIVAL);
                player.teleport(currentGameWorld.getSpawnLocation());
                tracker.addInGame(uuid);
            }
        }
        this.bossBar = this.createBossBar();
        this.nextRound();
        this.bossBarTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::updateBossBar, 0, 20);
    }

    public void resetGame() {
        this.roundNumber = 0;
        inProgress = false;
        BlockShuffle.logger.info("[Game State] Game ended — setInProgress(false) from resetGame()");
        this.bossBar.removeAll();
        Bukkit.getScheduler().cancelTask(this.roundEndTask);
        Bukkit.getScheduler().cancelTask(this.bossBarTask);

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

    private void nextRound() {
        this.roundNumber++;

        if (roundNumber > 1) {
            if (tracker.getCompletedUsers().size() == 1) {
                eliminateIncompletePlayers();
                announceWinnersAndReset();
                return;
            } else {
                if (!tracker.getCompletedUsers().isEmpty()) {
                    eliminateIncompletePlayers();
                } else {
                    broadcastUnfoundBlocks();
                }
            }
            tracker.getCompletedUsers().clear();
        }
        startNewRound();
    }

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

    public void playerJoined(Player player) {
        UUID uuid = player.getUniqueId();
        if (tracker.getUsersInGame().contains(uuid)) {
            // They were already in the game, teleport them back and restore state
            if (currentGameWorld != null) {
                player.teleport(currentGameWorld.getSpawnLocation());
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

    public World getCurrentGameWorld() {
        return currentGameWorld;
    }

    public World getLobbyWorld() {
        return this.lobbyWorld;
    }

    public void checkIfAllPlayersDone() {
        if (tracker.getUsersInGame().isEmpty()) return;
        if (tracker.getCompletedUsers().containsAll(tracker.getUsersInGame())) {
            Bukkit.getScheduler().runTask(plugin, this::nextRound);
        }
    }

    public void endGameEarly() {
        Bukkit.getScheduler().cancelTask(this.roundEndTask);
    }

    public void announceElimination(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            String blockName = formatMaterialName(tracker.getUserMaterialMap().get(uuid));
            Bukkit.broadcast(prefixedMessage(
                    Component.text(player.getName() + " ", NamedTextColor.WHITE)
                            .append(Component.text("got eliminated! Their block was: ", NamedTextColor.RED))
                            .append(Component.text(blockName, NamedTextColor.RED, TextDecoration.BOLD))));
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
                            Component.text(player.getName() + " had: ", NamedTextColor.RED)
                                    .append(Component.text(blockName, NamedTextColor.RED, TextDecoration.BOLD))
                    ));
                }
            }
        }
        Bukkit.broadcast(prefixedMessage(
                Component.text("Nobody stood on their block", NamedTextColor.RED)
        ));
    }

    public void announceWinnersAndReset() {
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Bukkit.broadcast(prefixedMessage(
                        Component.text(player.getName() + " ", NamedTextColor.WHITE)
                                .append(Component.text("won the game!", NamedTextColor.GREEN))));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
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

    private void startNewRound() {
        this.bossBar.setVisible(true);
        this.roundStartTime = System.currentTimeMillis();

        for (UUID uuid : tracker.getUsersInGame()) {
            assignNewBlockToPlayer(uuid);
        }

        this.roundEndTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, this::nextRound, this.ticksInRound);
    }

    private void assignNewBlockToPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        Material block = getRandomMaterial();
        tracker.assignBlock(uuid, block);
        BlockShuffle.logger.log(Level.INFO, player.getName() + " got " + formatMaterialName(block));
        player.sendMessage(prefixedMessage(
                Component.text("Your new block is: ", NamedTextColor.GREEN)
                        .append(Component.text(formatMaterialName(block), NamedTextColor.GREEN, TextDecoration.BOLD))
        ));
    }

    public boolean trySkip(UUID uuid) {
        if (!tracker.getUsersInGame().contains(uuid)) return false;
        if (tracker.getSkippedPlayers().contains(uuid)) return false;
        if (tracker.getCompletedUsers().contains(uuid)) return false;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;

        Material oldBlock = tracker.getUserMaterialMap().get(uuid);
        Material newBlock = getRandomMaterial();
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

    private BossBar createBossBar() {
        BossBar bossBar = Bukkit.createBossBar("Round: " + roundNumber + " | Time: XXX", BarColor.GREEN, BarStyle.SOLID);
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                bossBar.addPlayer(player);
            }
        }
        return bossBar;
    }

    private void updateBossBar() {
        bossBar.setColor(BarColor.GREEN);
        long timeSinceRoundStart = System.currentTimeMillis() - this.roundStartTime;
        long millisInRound = ((this.ticksInRound) / 20) * 1000L;
        long millisRemaining = millisInRound - timeSinceRoundStart;

        if (millisRemaining < 0) millisRemaining = 0;

        double progress = millisRemaining / (double) millisInRound;
        this.bossBar.setProgress(progress);

        long secondsRemaining = millisRemaining / 1000;
        long minutes = secondsRemaining / 60;
        long seconds = secondsRemaining % 60;

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

        String timeString;
        if (minutes > 0) {
            timeString = String.format("%d min %d sec", minutes, seconds);
        } else {
            timeString = String.format("%d sec", seconds);
        }

        this.bossBar.setTitle("Round: " + roundNumber + " | Time: " + timeString);

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

    public void readyAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!tracker.getReadyPlayers().contains(uuid)) {
                tracker.getReadyPlayers().add(uuid);
                Bukkit.broadcast(prefixedMessage(
                        Component.text(player.getName() + " ", NamedTextColor.WHITE)
                                .append(Component.text("is now ready (forced)", NamedTextColor.GREEN))));
            }
        }
        BlockShuffle.logger.info("[ReadyAll] All online players have been marked as ready.");
    }

    public boolean isInProgress() {
        return this.inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }

    public boolean isPvpEnabled() {
        return settingsGUI.isPvpEnabled();
    }

    private boolean isContinuousMode() {
        return settingsGUI.isContinuousMode();
    }

}
