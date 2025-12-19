package org.lukeeirl.blockShuffle.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Skull;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.game.PlayerTracker;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.formatMaterialName;
import static org.lukeeirl.blockShuffle.util.PlayerUtils.prefixedMessage;

public class BlockShuffleUtils {

    /**
     * Selects a random non-null material from the provided list.
     * Logs selection for debugging purposes.
     *
     * @param materials List of materials to choose from
     * @param random Random instance for selection
     * @return A randomly selected Material (never null)
     */
    public static Material getRandomMaterial(List<Material> materials, Random random) {
        Material selectedMaterial = null;
        int attemptCount = 0;

        while (selectedMaterial == null) {
            int randomIndex = random.nextInt(materials.size());
            selectedMaterial = materials.get(randomIndex);

            if (selectedMaterial == null) {
                BlockShuffle.logger.warning(String.format(
                        "[ERROR] getRandomMaterial(): Null material at index %d (Attempt #%d). Retrying...",
                        randomIndex,
                        attemptCount
                ));
            } else {
                BlockShuffle.logger.fine(String.format(
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

    /**
     * Broadcasts an elimination message for a player.
     * Shows their assigned block if they still had one.
     * Supports both online and offline players.
     *
     * @param uuid Player UUID
     * @param tracker PlayerTracker instance
     * @param location Player's location (optional, used for coordinate announcement)
     * @param hasItems Whether the player had items dropped
     */
    public static void announceElimination(UUID uuid, PlayerTracker tracker, Location location, boolean hasItems) {
        // Try online player first, fall back to offline player
        Player player = Bukkit.getPlayer(uuid);
        String playerName;

        if (player != null) {
            playerName = player.getName();
        } else {
            // Player is offline - get their name from OfflinePlayer
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            playerName = offlinePlayer.getName();
            if (playerName == null) {
                playerName = "Unknown Player";
            }
        }

        Material material = tracker.getUserMaterialMap().get(uuid);

        Component message = prefixedMessage(
                Component.text(playerName + " ", NamedTextColor.WHITE)
                        .append(Component.text("got eliminated!", NamedTextColor.RED))
        );

        // Only show the block if they still had one assigned
        if (material != null) {
            message = prefixedMessage(
                    Component.text(playerName + " ", NamedTextColor.WHITE)
                            .append(Component.text("got eliminated! Their block was: ", NamedTextColor.RED))
                            .append(Component.text(formatMaterialName(material), NamedTextColor.RED, TextDecoration.BOLD))
            );
        }

        // Add coordinates if items were dropped
        if (hasItems && location != null) {
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            message = message.append(Component.text(" | Items at: ", NamedTextColor.GRAY))
                    .append(Component.text("X: " + x + " Y: " + y + " Z: " + z, NamedTextColor.WHITE, TextDecoration.BOLD));
        }

        Bukkit.broadcast(message);
    }

    /**
     * Assigns a new random block to a player and notifies them.
     *
     * @param uuid Player UUID
     * @param tracker PlayerTracker instance
     * @param materials List of available materials
     * @param random Random instance for selection
     */
    public static void assignNewBlockToPlayer(UUID uuid, PlayerTracker tracker, List<Material> materials, Random random) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        Material block = getRandomMaterial(materials, random);
        String blockName = formatMaterialName(block);
        tracker.assignBlock(uuid, block);

        BlockShuffle.logger.log(Level.INFO, player.getName() + " was assigned " + blockName);

        // Send chat message
        player.sendMessage(prefixedMessage(
                Component.text("Your new block is: ", NamedTextColor.GREEN)
                        .append(Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD))
        ));

        // Show title on screen
        player.showTitle(Title.title(
                Component.text(blockName, NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Find and stand on this block!", NamedTextColor.GRAY),
                Title.Times.times(
                        java.time.Duration.ofMillis(500),  // fade in
                        java.time.Duration.ofSeconds(3),    // stay
                        java.time.Duration.ofMillis(500)    // fade out
                )
        ));
    }

    /**
     * Places a chest at the player's location containing all their items.
     * Clears the player's inventory after transferring items.
     *
     * @param player Player being eliminated
     * @return true if player had items and a chest was created, false otherwise
     */
    public static boolean dropItemsInChest(Player player) {
        if (player == null) return false;

        Location location = player.getLocation();
        Block block = location.getBlock();

        // Store player's items
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        // Check if player has any items
        boolean hasItems = false;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                hasItems = true;
                break;
            }
        }
        if (!hasItems) {
            for (ItemStack item : armorContents) {
                if (item != null && item.getType() != Material.AIR) {
                    hasItems = true;
                    break;
                }
            }
        }
        if (!hasItems && offHand != null && offHand.getType() != Material.AIR) {
            hasItems = true;
        }

        // Only create chest if player has items
        if (hasItems) {
            // Place chest at player's location
            block.setType(Material.CHEST);
            Chest chest = (Chest) block.getState();

            // Add all items to chest
            int slot = 0;
            for (ItemStack item : contents) {
                if (item != null && item.getType() != Material.AIR && slot < 27) {
                    chest.getInventory().setItem(slot++, item);
                }
            }
            for (ItemStack item : armorContents) {
                if (item != null && item.getType() != Material.AIR && slot < 27) {
                    chest.getInventory().setItem(slot++, item);
                }
            }
            if (offHand != null && offHand.getType() != Material.AIR && slot < 27) {
                chest.getInventory().setItem(slot, offHand);
            }

            // Place player head above the chest
            Block headBlock = block.getRelative(0, 1, 0); // Block above chest
            headBlock.setType(Material.PLAYER_HEAD);
            if (headBlock.getState() instanceof Skull) {
                Skull skull = (Skull) headBlock.getState();
                skull.setOwningPlayer((OfflinePlayer) player);
                skull.update();
            }

            // Clear player inventory
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);

            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();

            BlockShuffle.logger.info(String.format("[Item Drop] %s's items placed in chest at X:%d Y:%d Z:%d with player head above",
                    player.getName(), x, y, z));

            return true;
        } else {
            // Clear inventory even if empty
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            return false;
        }
    }

    /**
     * Strikes lightning at the given location without causing fire or damage.
     * Uses visual effect only.
     *
     * @param location Location to strike lightning
     */
    public static void strikeLightningWithoutFire(Location location) {
        if (location != null && location.getWorld() != null) {
            // strikeLightningEffect creates visual lightning without fire or damage
            location.getWorld().strikeLightningEffect(location);
        }
    }
}
