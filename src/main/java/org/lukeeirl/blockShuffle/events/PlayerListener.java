package org.lukeeirl.blockShuffle.events;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.game.GameManager;
import org.lukeeirl.blockShuffle.game.PlayerTracker;

import java.util.UUID;

public class PlayerListener implements Listener {
    private final BlockShuffle plugin;
    private final PlayerTracker playerTracker;
    private final GameManager gameManager;

    public PlayerListener(BlockShuffle plugin, PlayerTracker playerTracker, GameManager gameManager) {
        this.plugin = plugin;
        this.playerTracker = playerTracker;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        // Does this player have an assigned block?
        if (!playerTracker.getUserMaterialMap().containsKey(uuid)) return;

        // Get their block and location
        Material assignedBlock = playerTracker.getUserMaterialMap().get(uuid);
        Location loc = player.getLocation();

        // Check different depth layers under the player
        boolean found = false;
        for (double yOffset : new double[]{0.0, -0.1, -0.3, -0.6, -0.8,}) {
            Block checkBlock = loc.clone().add(0, yOffset, 0).getBlock();
            if (checkBlock.getType() == assignedBlock) {
                found = true;
                break;
            }
        }

        if (found) {
            gameManager.playerStandingOnBlock(player);
        }
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Always call playerJoined to handle cleanup of stale spectators
        // even when no game is in progress
        if (gameManager.isInProgress() || playerTracker.getSpectators().contains(uuid)) {
            gameManager.playerJoined(player);
        }
    }

    @EventHandler
    public void onPlayerRespawnEvent(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        World currentGameWorld = gameManager.getCurrentGameWorld();

        if (!gameManager.isInProgress() || !playerTracker.getUsersInGame().contains(uuid) || currentGameWorld == null) {
            return;
        }

        @SuppressWarnings("deprecation")
        Location spawn = player.getBedSpawnLocation();
        if (spawn == null || !spawn.getWorld().equals(currentGameWorld)) {
            spawn = currentGameWorld.getSpawnLocation();
        }

        spawn = spawn.clone().add((Math.random() - 0.5) * 2, 0, (Math.random() - 0.5) * 2);

        event.setRespawnLocation(spawn);
        Location finalSpawn = spawn.clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.teleport(finalSpawn);
            player.setGameMode(GameMode.SURVIVAL);
        }, 1L);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        World currentGameWorld = gameManager.getCurrentGameWorld();

        if (currentGameWorld != null &&
                damager.getWorld().equals(currentGameWorld) &&
                target.getWorld().equals(currentGameWorld) &&
                !gameManager.isPvpEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!gameManager.isInProgress() || !playerTracker.getUsersInGame().contains(id)) return;

        World fromWorld = event.getFrom().getWorld();
        String base = gameManager.getCurrentGameWorld().getName();

        switch (event.getCause()) {
            case NETHER_PORTAL -> {
                // Overworld → Nether
                if (fromWorld.getName().equals(base)) {
                    World nether = Bukkit.getWorld(base + "_nether");
                    if (nether != null) {
                        Location loc = event.getTo(); // already scaled from overworld → nether
                        event.setTo(new Location(nether, loc.getX(), loc.getY(), loc.getZ()));
                        event.setCanCreatePortal(true);
                        event.setSearchRadius(128);
                        event.setCreationRadius(16);
                    }
                }
                // Nether → Overworld
                else if (fromWorld.getName().equals(base + "_nether")) {
                    World overworld = Bukkit.getWorld(base);
                    if (overworld != null) {
                        Location loc = event.getTo(); // scaled nether → overworld
                        event.setTo(new Location(overworld, loc.getX(), loc.getY(), loc.getZ()));
                        event.setCanCreatePortal(true);
                        event.setSearchRadius(128);
                        event.setCreationRadius(16);
                    }
                }
            }

            case END_PORTAL -> {
                // Overworld → The End
                if (fromWorld.getName().equals(base)) {
                    World theEnd = Bukkit.getWorld(base + "_the_end");
                    if (theEnd != null) {
                        event.setTo(theEnd.getSpawnLocation());
                    }
                }
                // The End → Overworld
                else if (fromWorld.getName().equals(base + "_the_end")) {
                    World overworld = Bukkit.getWorld(base);
                    if (overworld != null) {
                        event.setTo(overworld.getSpawnLocation());
                    }
                }
            }

            default -> {
                // leave END_GATEWAY, etc. to vanilla
            }
        }
    }
}
