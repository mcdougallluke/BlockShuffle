package org.lukeeirl.blockShuffle.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.lukeeirl.blockShuffle.game.GameManager;

public class LobbyCommand implements CommandExecutor {
    private final GameManager gameManager;

    public LobbyCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String @NotNull [] args
    ) {
        if (!sender.hasPermission("blockshuffle.command.lobby")) {
            sender.sendMessage("You don’t have permission.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        gameManager.sendPlayerToLobby(player);
        return true;
    }
}

