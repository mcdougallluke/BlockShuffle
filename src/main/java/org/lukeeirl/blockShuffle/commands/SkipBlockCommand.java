package org.lukeeirl.blockShuffle.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.lukeeirl.blockShuffle.game.GameManager;
import org.lukeeirl.blockShuffle.game.PlayerTracker;

import java.util.UUID;

public class SkipBlockCommand implements CommandExecutor {
    private final GameManager gameManager;
    private final PlayerTracker playerTracker;

    public SkipBlockCommand(GameManager gameManager, PlayerTracker playerTracker) {
        this.gameManager = gameManager;
        this.playerTracker = playerTracker;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("blockshuffle.command.skip")) {
            sender.sendMessage(Component.text("You don’t have permission.", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        if (!playerTracker.getUsersInGame().contains(uuid)) {
            player.sendMessage(Component.text("You must be in the game to use this command.", NamedTextColor.RED));
            return true;
        }

        if (!gameManager.isInProgress()) {
            player.sendMessage(Component.text("There is no game currently running.", NamedTextColor.RED));
            return true;
        }

        if (playerTracker.getCompletedUsers().contains(uuid)) {
            player.sendMessage(Component.text("You already stood on your block.", NamedTextColor.RED));
            return true;
        }

        boolean success = gameManager.trySkip(uuid);
        if (!success) {
            player.sendMessage(Component.text("You have already used your skip for this game!", NamedTextColor.RED));
        }

        return true;
    }
}
