package org.lukeeirl.blockShuffle.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TestMessageCommand implements CommandExecutor {

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String @NotNull [] args
    ) {
        if (!sender.hasPermission("blockshuffle.command.testmsg")) {
            return true;
        }

        if (!(sender instanceof Player player)) return false;

        String input = String.join(" ", args);
        Component parsed = MiniMessage.miniMessage().deserialize(input);
        player.sendMessage(parsed);
        return true;
    }
}
