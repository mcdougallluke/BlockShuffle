package org.lukeeirl.blockShuffle.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.game.GameManager;
import org.lukeeirl.blockShuffle.game.PlayerTracker;
import org.lukeeirl.blockShuffle.ui.SettingsGUI;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.formatStatusMessage;
import static org.lukeeirl.blockShuffle.util.PlayerUtils.prefixedMessage;

public class BlockShuffleCommand implements CommandExecutor, TabCompleter {

    private final PlayerTracker playerTracker;
    private final GameManager gameManager;
    private final SettingsGUI settingsGUI;

    public BlockShuffleCommand(
            PlayerTracker playerTracker,
            GameManager gameManager,
            SettingsGUI settingsGUI
    ) {
        this.playerTracker = playerTracker;
        this.gameManager = gameManager;
        this.settingsGUI = settingsGUI;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /blockshuffle <ready|start|stop|spectate>", NamedTextColor.YELLOW));
            return true;
        }

        if (!sender.hasPermission("blockshuffle.command.base")) {
            sender.sendMessage(Component.text("You don’t have permission.", NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("broadcast")) {
            if (!sender.hasPermission("blockshuffle.admin.broadcast")) {
                sender.sendMessage(Component.text("You do not have permission to broadcast messages.", NamedTextColor.RED));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /blockshuffle broadcast <message>", NamedTextColor.YELLOW));
                return true;
            }

            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                messageBuilder.append(args[i]).append(" ");
            }

            String rawMessage = messageBuilder.toString().trim();

            MiniMessage miniMessage = MiniMessage.miniMessage();
            Component formattedMessage = miniMessage.deserialize(rawMessage);

            Bukkit.broadcast(prefixedMessage(formattedMessage));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "ready":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                    return true;
                }
                UUID uuid = player.getUniqueId();
                if (playerTracker.isReady(uuid)) {
                    playerTracker.setNotReady(uuid);
                    Bukkit.broadcast(formatStatusMessage(player.getName(), "is no longer ready", NamedTextColor.RED));
                } else {
                    playerTracker.setReady(uuid);
                    Bukkit.broadcast(formatStatusMessage(player.getName(), "is now ready", NamedTextColor.GREEN));
                }
                break;

            case "start":
                if (gameManager.isInProgress()) {
                    sender.sendMessage(Component.text("A game is already in progress.", NamedTextColor.RED));
                    return true;
                }

                if (playerTracker.getReadyPlayers().size() <= 1) {
                    sender.sendMessage(Component.text("Not enough players are ready.", NamedTextColor.RED));
                    return true;
                }

                BlockShuffle.logger.info("[Game State] Admin started game — setInProgress(true) from /blockshuffle start");
                Bukkit.broadcast(prefixedMessage(Component.text("The game is starting...", NamedTextColor.GREEN)));
                gameManager.startGame();
                break;

            case "settings":
                if (!sender.hasPermission("blockshuffle.admin.settings")) {
                    sender.sendMessage(Component.text("You do not have permission to access settings.", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                    return true;
                }
                settingsGUI.openSettingsMenu(player);
                break;

            case "stop":
                if (!sender.hasPermission("blockshuffle.admin.stop")) {
                    sender.sendMessage(Component.text("You do not have permission to stop the game.", NamedTextColor.RED));
                    return true;
                }

                if (!gameManager.isInProgress()) {
                    sender.sendMessage(Component.text("No game is currently running.", NamedTextColor.RED));
                    return true;
                }

                gameManager.resetGame();
                Bukkit.broadcast(prefixedMessage(Component.text("The game has been stopped", NamedTextColor.RED)));
                break;

            case "spectate":
                sender.sendMessage(Component.text("Command currently disabled", NamedTextColor.GRAY));
                break;

            case "readyall":
                if (!sender.hasPermission("blockshuffle.admin.readyall")) {
                    sender.sendMessage(Component.text("You do not have permission to ready all players.", NamedTextColor.RED));
                    return true;
                }

                gameManager.readyAllPlayers();
                sender.sendMessage(Component.text("All players have been marked as ready.", NamedTextColor.GREEN));
                break;

            default:
                sender.sendMessage(Component.text("Unknown subcommand. Try: /blockshuffle <ready|start|stop|spectate|readyall>", NamedTextColor.YELLOW));
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            String[] args
    ) {
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("ready", "settings", "start", "stop", "spectate", "readyall", "broadcast");

            return subcommands.stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        return Collections.emptyList();
    }
}
