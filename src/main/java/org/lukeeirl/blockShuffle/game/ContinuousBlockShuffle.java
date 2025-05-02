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

import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.*;

public class ContinuousBlockShuffle implements BSGameMode {

    private final PlayerTracker tracker;
    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final SettingsGUI settingsGUI;
    private final WorldService worldService;
    private final World lobbyWorld;
    private final Random random;

    private int ticksInRound = 6000;
    private List<Material> materials;
    private World currentGameWorld;
    private boolean inProgress;
    private boolean hasHandledWin = false;

    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();
    private static final long MAX_TIME_MILLIS = 15 * 60 * 1000; // 15 minutes
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    public ContinuousBlockShuffle(
            PlayerTracker tracker,
            BlockShuffle plugin,
            YamlConfiguration settings,
            SettingsGUI settingsGUI,
            WorldService worldService,
            World lobbyWorld
    ) {
        this.tracker = tracker;
        this.plugin = plugin;
        this.settings = settings;
        this.settingsGUI = settingsGUI;
        this.worldService = worldService;
        this.lobbyWorld = lobbyWorld;
        this.random = new Random();
    }

    @Override
    public void startGame() {
        // Initialize game settings
        BlockShuffle.logger.info("[Game State] Continuous game started — setInProgress(true) from startGame()");
        this.inProgress = true;
        this.hasHandledWin = false;

        this.ticksInRound = settingsGUI.getRoundTimeTicks();
        String baseWorldName = "blockshuffle_" + System.currentTimeMillis();
        currentGameWorld = worldService.createLinkedWorlds(baseWorldName);
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
                assignNewBlockToPlayer(uuid);
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
    }

    @Override
    public void resetGame() {
        BlockShuffle.logger.info("[Game State] Continuous game ended — setInProgress(false) from resetGame()");
        inProgress = false;
        this.hasHandledWin = false;

        // Send all players in the game back to lobby
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                resetPlayerState(player, GameMode.ADVENTURE);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                player.teleport(lobbyWorld.getSpawnLocation());
            }
        }
        for (UUID uuid : tracker.getSpectators()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                resetPlayerState(player, GameMode.ADVENTURE);
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                player.teleport(lobbyWorld.getSpawnLocation());
            }
        }

        for (BossBar bar : playerBossBars.values()) {
            bar.removeAll(); // Removes all players and hides the bar
        }
        playerBossBars.clear();


        tracker.clearAll();
        playerScoreboards.clear();

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
        assignNewBlockToPlayer(uuid);
    }

    @Override
    public void playerJoined(Player player) {
        UUID uuid = player.getUniqueId();

        if (tracker.getUsersInGame().contains(uuid)) {
            // Re-add boss bar
            BossBar bossBar = playerBossBars.get(uuid);
            if (bossBar != null) {
                bossBar.addPlayer(player);
            }

            // Re-add scoreboard
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

        } else if (tracker.getSpectators().contains(uuid)) {
            // Spectators also get their scoreboard back
            Scoreboard scoreboard = playerScoreboards.get(uuid);
            if (scoreboard != null) {
                player.setScoreboard(scoreboard);
            }
            if (lobbyWorld != null) {
                player.teleport(lobbyWorld.getSpawnLocation());
                player.setGameMode(GameMode.ADVENTURE);
            }
        } else {
            // Not in game or spectators, just send to lobby
            if (lobbyWorld != null) {
                player.teleport(lobbyWorld.getSpawnLocation());
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
    }

    @Override
    public void sendPlayerToLobby(Player player) {
        // send a temp message to the player for now
        player.sendMessage(prefixedMessage(
                Component.text("You have been sent to the lobby", NamedTextColor.RED)));
    }

    @Override
    public boolean trySkip(UUID uuid) {
        if (!tracker.getUsersInGame().contains(uuid)) return false;
        if (tracker.getSkippedPlayers().contains(uuid)) return false;

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

    @Override
    public boolean isInProgress() {
        return this.inProgress;
    }

    @Override
    public World getCurrentGameWorld() {
        return this.currentGameWorld;
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
                    announceElimination(uuid);
                    tracker.addSpectator(uuid);
                    tracker.getUsersInGame().remove(uuid);
                    tracker.getUserMaterialMap().remove(uuid);
                    player.setGameMode(GameMode.SPECTATOR);
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
                assignNewBlockToPlayer(uuid);
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

    private void assignNewBlockToPlayer(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        Material block = getRandomMaterial();
        String blockName = formatMaterialName(block);
        tracker.assignBlock(uuid, block);
        BlockShuffle.logger.log(Level.INFO, player.getName() + " got " + formatMaterialName(block));
        player.sendMessage(prefixedMessage(
                Component.text("Your new block is: ", NamedTextColor.GREEN)
                        .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD))
        ));
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
}
