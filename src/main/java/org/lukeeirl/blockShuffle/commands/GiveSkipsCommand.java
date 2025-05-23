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
import org.lukeeirl.blockShuffle.util.SkipManager;

import java.util.Collections;
import java.util.List;

public class GiveSkipsCommand implements CommandExecutor, TabCompleter {
    private final SkipManager skipManager;

    public GiveSkipsCommand(SkipManager skipManager) {
        this.skipManager = skipManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (sender instanceof Player && !sender.hasPermission("blockshuffle.command.giveskips")) {
            sender.sendMessage(Component.text("You don't have permission.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /giveskips <player> <amount>", NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid number of skips.", NamedTextColor.RED));
            return true;
        }

        skipManager.addSkips(target.getUniqueId(), amount);
        sender.sendMessage(Component.text("Added " + amount + " skips to " + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return Collections.emptyList();
    }
}
