package org.lukeeirl.blockShuffle.events;

import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPortalEvent;
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

        Location spawn = player.getRespawnLocation();
        if (spawn == null || !isGameWorld(spawn.getWorld())) {
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

    // The server links portals in custom worlds to its own main dimensions, which are the
    // lobby's, so portal destinations must be swapped to the game's world trio. Only the
    // destination world is changed; vanilla handles coordinate scaling, portal search and
    // portal creation.

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortalReady(EntityPortalReadyEvent event) {
        if (event.getPortalType() != PortalType.NETHER) return;

        World gameWorld = runningGameWorld();
        if (gameWorld == null) return;

        World from = event.getEntity().getWorld();
        if (from.equals(gameWorld)) {
            World gameNether = Bukkit.getWorld(gameWorld.getName() + "_nether");
            if (gameNether != null) event.setTargetWorld(gameNether);
        } else if (from.getName().equals(gameWorld.getName() + "_nether")) {
            event.setTargetWorld(gameWorld);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;
        Location redirected = redirectEndPortal(event.getFrom().getWorld(), event.getTo());
        if (redirected != null) event.setTo(redirected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getPortalType() != PortalType.ENDER) return;
        Location redirected = redirectEndPortal(event.getFrom().getWorld(), event.getTo());
        if (redirected != null) event.setTo(redirected);
    }

    private Location redirectEndPortal(World from, Location vanillaTo) {
        World gameWorld = runningGameWorld();
        if (gameWorld == null || vanillaTo == null) return null;
        String base = gameWorld.getName();

        // Leaving the game's end: players go through the respawn flow instead
        // (onPlayerRespawnEvent), so this mainly reroutes entities
        if (from.getName().equals(base + "_the_end")) {
            return gameWorld.getSpawnLocation();
        }

        // Entering the end: keep the vanilla spawn-platform coordinates, swap the world
        if (from.equals(gameWorld) || from.getName().equals(base + "_nether")) {
            World gameEnd = Bukkit.getWorld(base + "_the_end");
            if (gameEnd == null) return null;
            Location to = vanillaTo.clone();
            to.setWorld(gameEnd);
            // Vanilla generated the obsidian platform in the main end world before this
            // event fired, so it has to be rebuilt in the game's end world
            createEndPlatform(to);
            return to;
        }
        return null;
    }

    private World runningGameWorld() {
        if (!gameManager.isInProgress()) return null;
        return gameManager.getCurrentGameWorld();
    }

    private boolean isGameWorld(World world) {
        World gameWorld = runningGameWorld();
        if (gameWorld == null || world == null) return false;
        String base = gameWorld.getName();
        String name = world.getName();
        return name.equals(base) || name.equals(base + "_nether") || name.equals(base + "_the_end");
    }

    // Mirrors vanilla's EndPlatformFeature: a 5x5 obsidian platform two blocks below the
    // arrival point, with three layers of air cleared out above it
    private static void createEndPlatform(Location spawn) {
        World world = spawn.getWorld();
        int x = spawn.getBlockX();
        int y = spawn.getBlockY() - 1;
        int z = spawn.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy < 3; dy++) {
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    Material material = dy == -1 ? Material.OBSIDIAN : Material.AIR;
                    if (block.getType() != material) block.setType(material);
                }
            }
        }
    }
}
