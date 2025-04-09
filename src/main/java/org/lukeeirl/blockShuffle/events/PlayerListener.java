package org.lukeeirl.blockShuffle.events;

import com.google.common.collect.Sets;

import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.jetbrains.annotations.NotNull;
import org.lukeeirl.blockShuffle.BlockShuffle;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PlayerListener implements Listener {
    private final World lobbyWorld;
    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final Random random = new Random();
    private final int ticksInRound = 6000; // 6000 ticks = 300 sec == 5 min

    private final Map<UUID, Material> userMaterialMap = new ConcurrentHashMap<>();
    private final Set<UUID> readyPlayers = Sets.newConcurrentHashSet();
    private final Set<UUID> completedUsers = Sets.newConcurrentHashSet();
    private final Set<UUID> usersInGame = Sets.newConcurrentHashSet();
    private final Set<UUID> spectators = Sets.newConcurrentHashSet();
    private final Set<UUID> skippedPlayers = Sets.newConcurrentHashSet();
    private final Set<UUID> disconnectedPlayers = Sets.newConcurrentHashSet();

    private List<Material> materials;
    private int bossBarTask;
    private int roundEndTask;
    private BossBar bossBar;
    private long roundStartTime;
    private World currentGameWorld;
    private int roundNumber = 0;

    public PlayerListener(YamlConfiguration settings, BlockShuffle plugin) {
        this.settings = settings;
        this.plugin = plugin;
        this.lobbyWorld = Bukkit.getWorlds().getFirst();
    }

    public void startGame() {
        currentGameWorld = createNewWorld();
        String materialPath = "materials";
        this.materials = this.settings.getStringList(materialPath).stream().map(Material::getMaterial).collect(Collectors.toList());
        plugin.setRoundWon(false);

        for (UUID uuid : readyPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                resetPlayerState(player, GameMode.SURVIVAL);
                player.teleport(currentGameWorld.getSpawnLocation());
                this.usersInGame.add(uuid);
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

        for (UUID uuid : usersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                resetPlayerState(player, GameMode.ADVENTURE);
                player.teleport(lobbyWorld.getSpawnLocation());
            }
        }

        for (UUID uuid : spectators) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && lobbyWorld != null) {
                resetPlayerState(player, GameMode.ADVENTURE);
                player.teleport(lobbyWorld.getSpawnLocation());
            }
        }

        this.userMaterialMap.clear();
        this.usersInGame.clear();
        this.completedUsers.clear();
        this.spectators.clear();
        this.readyPlayers.clear();
        this.skippedPlayers.clear();

        if (currentGameWorld != null) {
            Bukkit.unloadWorld(currentGameWorld, false);
            try {
                File worldFolder = currentGameWorld.getWorldFolder();
                deleteWorldFolder(worldFolder);
            } catch (Exception e) {
                BlockShuffle.logger.warning("Failed to delete world folder: " + e.getMessage());
            }
            currentGameWorld = null;
        }
    }

    private void nextRound() {
        this.roundNumber++;

        if (roundNumber > 1) {
            if (this.completedUsers.size() == 1) {
                eliminateIncompletePlayers();
                announceWinnersAndReset();
                return;
            } else {
                if (!this.completedUsers.isEmpty()) {
                    eliminateIncompletePlayers();
                } else {
                    broadcastUnfoundBlocks();
                }
            }
            this.completedUsers.clear();
        }
        startNewRound();
    }

    private void eliminateIncompletePlayers() {
        Iterator<UUID> iterator = usersInGame.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (!completedUsers.contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    String playersBlock = formatMaterialName(userMaterialMap.get(uuid));
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&7Block Shuffle&8] &f» " + player.getName() +
                                    " &cgot eliminated! Their block was: &c&l" + playersBlock));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_DEATH, 1.0f, 1.0f);
                    spectators.add(uuid);
                    player.setGameMode(GameMode.SPECTATOR);
                }
                iterator.remove();
                userMaterialMap.remove(uuid);
            }
        }
    }

    private void broadcastUnfoundBlocks() {
        for (UUID uuid : usersInGame) {
            if (!completedUsers.contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    String playersBlock = formatMaterialName(userMaterialMap.get(uuid));
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&7Block Shuffle&8] &f» " + player.getName() +
                                    " &chad: &c&l" + playersBlock));
                }
            }
        }
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&7Block Shuffle&8] &f» &cNobody stood on their block"));
    }

    private void announceWinnersAndReset() {
        String winnerMessage = this.createWinnerMessage();
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&7Block Shuffle&8] &f» " + winnerMessage));
        String title = ChatColor.translateAlternateColorCodes('&', "&f" + winnerMessage);

        for (UUID uuid : usersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1.0f, 1.0f);
            }
        }

        for (UUID uuid : completedUsers) {
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

    private void launchFireworkAt(Location location) {
        Firework firework = (Firework) location.getWorld().spawnEntity(location, org.bukkit.entity.EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = firework.getFireworkMeta();

        meta.addEffect(FireworkEffect.builder()
                .flicker(true)
                .trail(true)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.GREEN)
                .withFade(Color.LIME)
                .build());
        meta.setPower(1);

        firework.setFireworkMeta(meta);
    }

    private void startNewRound() {
        this.bossBar.setVisible(true);
        this.roundStartTime = System.currentTimeMillis();

        for (UUID uuid : usersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Material randomBlock = getRandomMaterial();
                BlockShuffle.logger.log(Level.INFO, "[Round Start] " + player.getName() + " " + randomBlock);
                String randomBlockName = formatMaterialName(randomBlock);
                userMaterialMap.put(uuid, randomBlock);
                BlockShuffle.logger.log(Level.INFO, player.getName() + " got " + randomBlockName);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&7Block Shuffle&8] &f» &aYour block is: &a&l" + randomBlockName));
            }
        }

        this.roundEndTask = Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, this::nextRound, this.ticksInRound);
    }

    public boolean trySkip(UUID uuid) {
        if (!usersInGame.contains(uuid)) return false;
        if (skippedPlayers.contains(uuid)) return false;
        if (completedUsers.contains(uuid)) return false;

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;

        Material oldBlock = userMaterialMap.get(uuid);
        Material newBlock = getRandomMaterial();
        userMaterialMap.put(uuid, newBlock);
        skippedPlayers.add(uuid);

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
        for (UUID uuid : this.completedUsers) {
            sb.append(Objects.requireNonNull(Bukkit.getPlayer(uuid)).getName()).append(" &awon the game!");
        }
        return sb.toString();
    }

    private BossBar createBossBar() {
        BossBar bossBar = Bukkit.createBossBar("Round: " + roundNumber + " | Time: XXX", BarColor.GREEN, BarStyle.SOLID);
        for (UUID uuid : this.usersInGame) {
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
            for (UUID uuid : usersInGame) {
                if (!completedUsers.contains(uuid)) {
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

        for (UUID uuid : usersInGame) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            String actionBarMessage;
            if (completedUsers.contains(uuid)) {
                actionBarMessage = ChatColor.translateAlternateColorCodes('&', "&eWaiting for next round...");
            } else {
                Material targetBlock = userMaterialMap.get(uuid);
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

    private String formatMaterialName(Material material) {
        return Arrays.stream(material.name().split("_"))
                .map(word -> word.toLowerCase(Locale.ROOT))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    public void readyAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!readyPlayers.contains(uuid)) {
                readyPlayers.add(uuid);
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

        if (!userMaterialMap.containsKey(uuid)) return;

        Material assignedBlock = userMaterialMap.get(uuid);
        Location loc = player.getLocation();

        // Check blocks from 0.0 to -1.2 beneath the player's feet
        boolean found = false;
        for (double yOffset : new double[]{0.0, -0.1, -0.3, -0.6, -1.0, -1.2}) {
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
                completedUsers.add(uuid);
            }

            userMaterialMap.remove(uuid);

            if (completedUsers.size() == usersInGame.size()) {
                BlockShuffle.logger.info("[Game State] All players completed their block — starting next round");
                Bukkit.getScheduler().cancelTask(this.roundEndTask);
                this.nextRound();
            }
        }
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        spectators.remove(playerUUID);
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.isInProgress()) {
            if (this.usersInGame.contains(uuid)) {
                // They were already in the game, teleport them back and restore state
                if (currentGameWorld != null) {
                    player.teleport(currentGameWorld.getSpawnLocation());
                    bossBar.addPlayer(player); // Re-add to the boss bar
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&7Block Shuffle&8] &f» &aYou've rejoined the game"));

                    // Re-send their block task, if it still exists
                    Material material = this.userMaterialMap.get(uuid);
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
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Bukkit.getScheduler().runTaskLater(plugin, () -> player.spigot().respawn(), 1L);
    }

    @EventHandler
    public void onPlayerRespawnEvent(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check if the player is still actively playing
        BlockShuffle.logger.log(Level.INFO, "[Respawn] Game in progress: {0}", plugin.isInProgress());
        BlockShuffle.logger.log(Level.INFO, "[Respawn] Player in usersInGame: {0}", usersInGame.contains(uuid));
        BlockShuffle.logger.log(Level.INFO, "[Respawn] Current game world exists: {0}", currentGameWorld != null);

        if (plugin.isInProgress() && usersInGame.contains(uuid) && currentGameWorld != null) {
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

    public World createNewWorld() {
        String worldName = "blockshuffle_" + System.currentTimeMillis(); // unique world name
        WorldCreator creator = new WorldCreator(worldName);
        return Bukkit.createWorld(creator); // creates and loads the world
    }

    private void deleteWorldFolder(File path) {
        if (path.isDirectory()) {
            for (File file : path.listFiles()) {
                deleteWorldFolder(file);
            }
        }
        path.delete();
    }

    private void resetPlayerState(Player player, GameMode gameMode) {
        player.setGameMode(gameMode);
        player.getInventory().clear();
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setExhaustion(0f);
        player.setExp(0f);
        player.setLevel(0);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        // Remove all active potion effects
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        // Remove all advancements
        AdvancementProgress progress;
        for (@NotNull Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement advancement = it.next();
            progress = player.getAdvancementProgress(advancement);
            for (String criterion : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criterion);
            }
        }
    }

    public BlockShuffle getPlugin() {
        return plugin;
    }

    public boolean isReady(UUID uuid) {
        return readyPlayers.contains(uuid);
    }

    public void setReady(UUID uuid) {
        readyPlayers.add(uuid);
    }

    public void setNotReady(UUID uuid) {
        readyPlayers.remove(uuid);
    }

    public Set<UUID> getReadyPlayers() {
        return readyPlayers;
    }

    public Set<UUID> getUsersInGame() {
        return usersInGame;
    }

    public Set<UUID> getCompletedUsers() {
        return completedUsers;
    }

}
