package org.lukeeirl.blockShuffle.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.lukeeirl.blockShuffle.events.PlayerListener;
import org.lukeeirl.blockShuffle.game.PlayerTracker;

import java.util.UUID;

public class SkipBlockCommand implements CommandExecutor {
    private final PlayerListener playerListener;
    private final PlayerTracker playerTracker;

    public SkipBlockCommand(PlayerListener playerListener, PlayerTracker playerTracker) {
        this.playerListener = playerListener;
        this.playerTracker = playerTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (!playerTracker.getUsersInGame().contains(uuid)) {
            player.sendMessage(ChatColor.RED + "You must be in the game to use this command.");
            return true;
        }

        if (!playerListener.getPlugin().isInProgress()) {
            player.sendMessage(ChatColor.RED + "There is no game currently running.");
            return true;
        }

        if (playerTracker.getCompletedUsers().contains(uuid)) {
            player.sendMessage(ChatColor.RED + "You already stood on your block.");
            return true;
        }

        boolean success = playerListener.trySkip(uuid);
        if (!success) {
            player.sendMessage(ChatColor.RED + "You have already used your skip for this game!");
        }

        return true;
    }
}
