package org.lukeeirl.blockShuffle.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.lukeeirl.blockShuffle.util.PlayerStats;
import org.lukeeirl.blockShuffle.util.SkipManager;
import org.lukeeirl.blockShuffle.util.StatsManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


public class StatsCommand implements CommandExecutor, TabCompleter {
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private final StatsManager stats;
    private final SkipManager skipManager;

    public StatsCommand(StatsManager stats, SkipManager skipManager) {
        this.stats = stats;
        this.skipManager = skipManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!sender.hasPermission("blockshuffle.command.stats")) {
            sender.sendMessage(mm.deserialize("<red>You don’t have permission.</red>"));
            return true;
        }

        OfflinePlayer targetPlayer;
        if (args.length > 0) {
            targetPlayer = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(args[0]))
                    .findFirst().orElse(null);
            if (targetPlayer == null) {
                sender.sendMessage(mm.deserialize("<red>Player '<white>" + args[0] + "<red>' has never joined.</red>"));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(mm.deserialize("<red>Usage: /stats <player></red>"));
                return true;
            }
            targetPlayer = (Player) sender;
        }

        UUID uuid = targetPlayer.getUniqueId();
        PlayerStats ps = stats.get(uuid);
        int remainingSkips = skipManager.getPurchasedSkips(uuid);
        String name = targetPlayer.getName();

        assert name != null;
        String raw = """
        <dark_gray>╔═════════════╗</dark_gray>
        <gradient:#ADFAFF:#80A8FF><bold>  Block Shuffle Stats  </bold></gradient>
        <dark_gray>╚═════════════╝</dark_gray>
        <gradient:#ADFAFF:#80A8FF><bold>%name%</bold></gradient><white>:</white>
        <dark_gray>»</dark_gray> <white>Games Played: </white><green>%played%</green>
        <dark_gray>»</dark_gray> <white>Games Won: </white><green>%won%</green>
        <dark_gray>»</dark_gray> <white>Blocks Found: </white><green>%blocks%</green>
        <dark_gray>»</dark_gray> <white>Skips Bought: </white><green>%bought%</green>
        <dark_gray>»</dark_gray> <white>Skips Remaining: </white><green>%remaining%</green>
        """;
        raw = raw
                .replace("%name%",      name)
                .replace("%played%",    String.valueOf(ps.gamesPlayed()))
                .replace("%won%",       String.valueOf(ps.gamesWon()))
                .replace("%blocks%",    String.valueOf(ps.blocksSteppedOn()))
                .replace("%bought%",    String.valueOf(ps.skipsBought()))
                .replace("%remaining%", String.valueOf(remainingSkips));

        Component statsMsg = mm.deserialize(raw);

        sender.sendMessage(statsMsg);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

