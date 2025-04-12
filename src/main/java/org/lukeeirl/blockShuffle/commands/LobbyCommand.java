package org.lukeeirl.blockShuffle.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.game.GameManager;
import org.lukeeirl.blockShuffle.game.PlayerTracker;

import java.util.UUID;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.resetPlayerState;

public class LobbyCommand implements CommandExecutor {
    private final BlockShuffle plugin;
    private final PlayerTracker tracker;
    private final GameManager gameManager;

    public LobbyCommand(BlockShuffle plugin, PlayerTracker tracker, GameManager gameManager) {
        this.plugin = plugin;
        this.tracker = tracker;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (tracker.getUsersInGame().contains(uuid)) {
            gameManager.announceElimination(uuid);

            // Remove the player from all relevant sets
            tracker.getUsersInGame().remove(uuid);
            tracker.getUserMaterialMap().remove(uuid);
            tracker.getCompletedUsers().remove(uuid);
            tracker.getSkippedPlayers().remove(uuid);

            // Check if only one player is left
            if (tracker.getUsersInGame().size() == 1) {
                UUID winnerUUID = tracker.getUsersInGame().iterator().next();
                tracker.getCompletedUsers().add(winnerUUID);
                gameManager.announceWinnersAndReset();
                gameManager.endGameEarly();
            } else {
                gameManager.checkIfAllPlayersDone();
            }
        }

        tracker.getSpectators().remove(uuid);

        World lobbyWorld = plugin.getServer().getWorlds().getFirst();
        resetPlayerState(player, GameMode.ADVENTURE);
        player.teleport(lobbyWorld.getSpawnLocation());

        player.sendMessage(Component.text("You have returned to the lobby.", NamedTextColor.GREEN));
        return true;
    }
}

