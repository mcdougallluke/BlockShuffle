package org.lukeeirl.blockShuffle.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.events.PlayerListener;
import org.lukeeirl.blockShuffle.game.PlayerTracker;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BlockShuffleCommand implements CommandExecutor, TabCompleter {

    private final PlayerListener playerListener;
    private final PlayerTracker playerTracker;
    private final BlockShuffle plugin;

    public BlockShuffleCommand(PlayerListener playerListener, PlayerTracker playerTracker, BlockShuffle plugin, YamlConfiguration settings) {
        this.playerListener = playerListener;
        this.playerTracker = playerTracker;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /blockshuffle <ready|start|stop|spectate>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "ready":
                UUID uuid = player.getUniqueId();
                if (playerTracker.isReady(uuid)) {
                    playerTracker.setNotReady(uuid);
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&7Block Shuffle&8] &f» " + player.getName() + " &cis no longer ready"));
                } else {
                    playerTracker.setReady(uuid);
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                            "&8[&7Block Shuffle&8] &f» " + player.getName() + " &ais now ready"));
                }
                break;

            case "start":
                if (!player.hasPermission("blockshuffle.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to start the game.");
                    return true;
                }

                if (plugin.isInProgress()) {
                    player.sendMessage(ChatColor.RED + "A game is already in progress.");
                    return true;
                }

                if (playerTracker.getReadyPlayers().isEmpty()) {
                    player.sendMessage(ChatColor.RED + "No players are ready.");
                    return true;
                }

                plugin.setInProgress(true);
                BlockShuffle.logger.info("[Game State] Admin started game — setInProgress(true) from /blockshuffle start");
                plugin.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&7Block Shuffle&8] &f» &aThe game is starting"));
                playerListener.startGame();
                break;

            case "stop":
                if (!player.hasPermission("blockshuffle.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to stop the game.");
                    return true;
                }

                if (!plugin.isInProgress()) {
                    player.sendMessage(ChatColor.RED + "No game is currently running.");
                    return true;
                }

                playerListener.resetGame();
                plugin.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                        "&8[&7Block Shuffle&8] &f» &cThe game has been stopped"));
                break;

            case "spectate":
//                UUID spectateUUID = player.getUniqueId();
//
//                // Remove from active game participants
//                if (playerListener.getUsersInGame().contains(spectateUUID)) {
//                    playerListener.getUsersInGame().remove(spectateUUID);
//                    playerListener.getUserMaterialMap().remove(spectateUUID);
//                    BlockShuffle.logger.info(player.getName() + " left the game via /spectate — removed from usersInGame.");
//                }
//
//                // Add to spectators
//                if (!playerListener.getSpectators().contains(spectateUUID)) {
//                    playerListener.getSpectators().add(spectateUUID);
//                    BlockShuffle.logger.info(player.getName() + " is now marked as a spectator.");
//                }
//
//                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(ChatColor.GRAY + "Command currently disabled");
                break;

            case "readyall":
                if (!player.hasPermission("blockshuffle.admin")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to ready all players.");
                    return true;
                }

                playerListener.readyAllPlayers();
                player.sendMessage(ChatColor.GREEN + "All players have been marked as ready.");
                break;


            default:
                player.sendMessage(ChatColor.YELLOW + "Unknown subcommand. Try: /blockshuffle <ready|start|stop|spectate|readyall>");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("ready", "start", "stop", "spectate", "readyall");

            return subcommands.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }

}
