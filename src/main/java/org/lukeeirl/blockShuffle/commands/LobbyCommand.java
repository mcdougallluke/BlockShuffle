package org.lukeeirl.blockShuffle.commands;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.game.PlayerTracker;

import java.util.UUID;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.resetPlayerState;

public class LobbyCommand implements CommandExecutor {
    private final BlockShuffle plugin;
    private final PlayerTracker tracker;

    public LobbyCommand(BlockShuffle plugin, PlayerTracker tracker) {
        this.plugin = plugin;
        this.tracker = tracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        // Handle active game players: treat it like surrender
        if (tracker.getUsersInGame().contains(uuid)) {
            tracker.getUsersInGame().remove(uuid);
            tracker.getUserMaterialMap().remove(uuid);
            tracker.getCompletedUsers().remove(uuid);
            tracker.getSkippedPlayers().remove(uuid);
            plugin.getServer().broadcastMessage(ChatColor.RED + player.getName() + " has surrendered and returned to the lobby.");
        }

        // Remove from spectators too
        tracker.getSpectators().remove(uuid);

        World lobbyWorld = plugin.getServer().getWorlds().getFirst();
        resetPlayerState(player, GameMode.ADVENTURE);
        player.teleport(lobbyWorld.getSpawnLocation());

        player.sendMessage(ChatColor.GREEN + "You have returned to the lobby.");
        return true;
    }
}
