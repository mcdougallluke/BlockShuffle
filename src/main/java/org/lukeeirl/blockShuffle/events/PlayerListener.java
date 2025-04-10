package org.lukeeirl.blockShuffle.events;

import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.game.PlayerTracker;
import org.lukeeirl.blockShuffle.game.WorldService;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.*;

public class PlayerListener implements Listener {
    private final World lobbyWorld;
    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final Random random = new Random();
    private final int ticksInRound = 6000; // 6000 ticks = 300 sec == 5 min

    private List<Material> materials;
    private int bossBarTask;
    private int roundEndTask;
    private BossBar bossBar;
    private long roundStartTime;
    private World currentGameWorld;
    private int roundNumber = 0;

    private final WorldService worldService = new WorldService();
    private final PlayerTracker tracker;

    public PlayerListener(YamlConfiguration settings, PlayerTracker playerTracker, BlockShuffle plugin) {
        this.settings = settings;
        this.plugin = plugin;
        this.tracker = playerTracker;
        this.lobbyWorld = Bukkit.getWorlds().getFirst();
    }

    public void startGame() {
        currentGameWorld = worldService.createNewWorld();
        String materialPath = "materials";
        this.materials = this.settings.getStringList(materialPath).stream().map(Material::getMaterial).collect(Collectors.toList());
        plugin.setRoundWon(false);

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
        this.plugin.setInProgress(false);
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
            String playersBlock = formatMaterialName(tracker.getUserMaterialMap().get(uuid));
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&7Block Shuffle&8] &f» " + player.getName() +
                            " &cgot eliminated! Their block was: &c&l" + playersBlock));
        }
    }

    private void eliminateIncompletePlayers() {
        Iterator<UUID> iterator = tracker.getUsersInGame().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (!tracker.getCompletedUsers().contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    String playersBlock = formatMaterialName(tracker.getUserMaterialMap().get(uuid));
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&7Block Shuffle&8] &f» " + player.getName() +
                                    " &cgot eliminated! Their block was: &c&l" + playersBlock));
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
                    String playersBlock = formatMaterialName(tracker.getUserMaterialMap().get(uuid));
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&7Block Shuffle&8] &f» " + player.getName() +
                                    " &chad: &c&l" + playersBlock));
                }
            }
        }
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&7Block Shuffle&8] &f» &cNobody stood on their block"));
    }

    public void announceWinnersAndReset() {
        String winnerMessage = this.createWinnerMessage();
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&7Block Shuffle&8] &f» " + winnerMessage));

        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
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
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Material randomBlock = getRandomMaterial();
                BlockShuffle.logger.log(Level.INFO, "[Round Start] " + player.getName() + " " + randomBlock);
                String randomBlockName = formatMaterialName(randomBlock);
                tracker.assignBlock(uuid, randomBlock);
                BlockShuffle.logger.log(Level.INFO, player.getName() + " got " + randomBlockName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&7Block Shuffle&8] &f» &aYour block is: &a&l" + randomBlockName));
            }
        }

        this.roundEndTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, this::nextRound, this.ticksInRound);
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

        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&7Block Shuffle&8] &f» " + player.getName() +
                        " &eskipped: &l" + oldBlockName));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&7Block Shuffle&8] &f» &aYour new block is: &l" + newBlockName));
        BlockShuffle.logger.info(player.getName() + " skipped " + oldBlockName + " and received " + newBlockName);
        return true;
    }

    private String createWinnerMessage() {
        StringBuilder sb = new StringBuilder();
        for (UUID uuid : tracker.getCompletedUsers()) {
            sb.append(Objects.requireNonNull(Bukkit.getPlayer(uuid)).getName()).append(" &awon the game!");
        }
        return sb.toString();
    }

    private BossBar createBossBar() {
        BossBar bossBar = Bukkit.createBossBar("Round: " + roundNumber + " | Time: XXX", BarColor.GREEN, BarStyle.SOLID);
        for (UUID uuid : tracker.getUsersInGame()) {
            Player player = Bukkit.getPlayer(uuid);
            bossBar.addPlayer(player);
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
                        player.sendTitle(ChatColor.RED + String.valueOf(secondsRemaining), "", 0, 20, 0);
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

            String actionBarMessage;
            if (tracker.getCompletedUsers().contains(uuid)) {
                actionBarMessage = ChatColor.translateAlternateColorCodes('&', "&eWaiting for next round...");
            } else {
                Material targetBlock = tracker.getUserMaterialMap().get(uuid);
                String blockName = formatMaterialName(targetBlock);
                actionBarMessage = ChatColor.translateAlternateColorCodes('&', "&aStand on: &l" + blockName);
            }

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new net.md_5.bungee.api.chat.TextComponent(actionBarMessage));
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
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&7Block Shuffle&8] &f» " + player.getName() + " &ais now ready (forced)"));
            }
        }
        BlockShuffle.logger.info("[ReadyAll] All online players have been marked as ready.");
    }

    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!tracker.getUserMaterialMap().containsKey(uuid)) return;

        Material assignedBlock = tracker.getUserMaterialMap().get(uuid);
        Location loc = player.getLocation();

        boolean found = false;
        for (double yOffset : new double[]{0.0, -0.1, -0.3, -0.6, -0.8,}) {
            Block checkBlock = loc.clone().add(0, yOffset, 0).getBlock();
            if (checkBlock.getType() == assignedBlock) {
                found = true;
                break;
            }
        }

        if (found) {
            if (!plugin.isRoundWon()) {
                String blockName = formatMaterialName(assignedBlock);
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&7Block Shuffle&8] &f» " + player.getName() + " &astood on their block. Their block was: &l" + blockName));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                tracker.addCompleted(uuid);
            }

            tracker.getUserMaterialMap().remove(uuid);

            if (tracker.getCompletedUsers().size() == tracker.getUsersInGame().size()) {
                BlockShuffle.logger.info("[Game State] All players completed their block — starting next round");
                Bukkit.getScheduler().cancelTask(this.roundEndTask);
                this.nextRound();
            }
        }
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        tracker.getSpectators().remove(playerUUID);
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.isInProgress()) {
            if (tracker.getUsersInGame().contains(uuid)) {
                // They were already in the game, teleport them back and restore state
                if (currentGameWorld != null) {
                    player.teleport(currentGameWorld.getSpawnLocation());
                    bossBar.addPlayer(player); // Re-add to the boss bar
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&7Block Shuffle&8] &f» &aYou've rejoined the game"));

                    // Re-send their block task, if it still exists
                    Material material = tracker.getUserMaterialMap().get(uuid);
                    if (material != null) {
                        String playerOnBlock2 = formatMaterialName(material);
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&8[&7Block Shuffle&8] &f» &aYour block is: &a&l" + playerOnBlock2));
                    }

                }
            } else {
                // Not in the game anymore, just go to the lobby
                if (lobbyWorld != null) {
                    player.teleport(lobbyWorld.getSpawnLocation());
                    player.setGameMode(GameMode.ADVENTURE);
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&7Block Shuffle&8] &f» &cYou've been eliminated"));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerRespawnEvent(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if the player is still actively playing
        BlockShuffle.logger.log(Level.INFO, "[Respawn] Game in progress: {0}", plugin.isInProgress());
        BlockShuffle.logger.log(Level.INFO, "[Respawn] Player in usersInGame: {0}", tracker.getUsersInGame().contains(uuid));
        BlockShuffle.logger.log(Level.INFO, "[Respawn] Current game world exists: {0}", currentGameWorld != null);

        if (plugin.isInProgress() && tracker.getUsersInGame().contains(uuid) && currentGameWorld != null) {
            BlockShuffle.logger.log(Level.INFO, player.getName() + " deemed still playing");
            Location respawnLocation = currentGameWorld.getSpawnLocation().clone();
            event.setRespawnLocation(respawnLocation);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.teleport(respawnLocation);
                player.setGameMode(GameMode.SURVIVAL);
            }, 1L);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        // Cancel PvP only in the game world
        if (currentGameWorld != null &&
                damager.getWorld().equals(currentGameWorld) &&
                target.getWorld().equals(currentGameWorld)) {
            event.setCancelled(true);
        }

        if (lobbyWorld != null &&
                damager.getWorld().equals(lobbyWorld) &&
                target.getWorld().equals(lobbyWorld)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (lobbyWorld != null && player.getWorld().equals(lobbyWorld)
                && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (lobbyWorld != null && player.getWorld().equals(lobbyWorld)) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(5.0f);
            player.setExhaustion(0.0f);
        }
    }

    public BlockShuffle getPlugin() {
        return plugin;
    }
}
