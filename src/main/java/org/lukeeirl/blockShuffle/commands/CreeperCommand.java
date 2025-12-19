package org.lukeeirl.blockShuffle.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.lukeeirl.blockShuffle.util.CreeperManager;

import java.util.Collections;
import java.util.List;

public class CreeperCommand implements CommandExecutor, TabCompleter {
    private final CreeperManager creeperManager;

    public CreeperCommand(CreeperManager creeperManager) {
        this.creeperManager = creeperManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        // Only ops can use this command
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("Unknown command. Type \"/help\" for help.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /creeper <player>", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        boolean wasAdded = creeperManager.toggleCreeper(target.getUniqueId());

        if (wasAdded) {
            sender.sendMessage(Component.text("Added " + target.getName() + " to the creeper list.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Removed " + target.getName() + " from the creeper list.", NamedTextColor.YELLOW));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (!sender.isOp()) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return Collections.emptyList();
    }
}
