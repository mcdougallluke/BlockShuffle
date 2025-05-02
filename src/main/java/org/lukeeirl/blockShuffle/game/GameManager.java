package org.lukeeirl.blockShuffle.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.ui.SettingsGUI;
import org.lukeeirl.blockShuffle.util.SkipManager;

import java.util.*;

import static org.lukeeirl.blockShuffle.util.PlayerUtils.prefixedMessage;

public class GameManager {
    private final World lobbyWorld;;
    private final SettingsGUI settingsGUI;
    private final PlayerTracker tracker;
    private final ClassicBlockShuffle classicMode;
    private final ContinuousBlockShuffle continuousMode;
    private final SkipManager skipManager;

    private BSGameMode activeMode;

    public GameManager(PlayerTracker tracker, BlockShuffle plugin, YamlConfiguration settings, SettingsGUI settingsGUI, SkipManager skipManager) {
        this.settingsGUI = settingsGUI;
        this.tracker = tracker;
        WorldService worldService = new WorldService();
        this.lobbyWorld = Bukkit.getWorlds().getFirst();
        this.skipManager = skipManager;

        this.classicMode = new ClassicBlockShuffle(tracker, plugin, settings, settingsGUI, worldService, lobbyWorld, skipManager);
        this.continuousMode = new ContinuousBlockShuffle(tracker, plugin, settings, settingsGUI, worldService, lobbyWorld);
    }

    public void startGame() {
        if (settingsGUI.isContinuousMode()) {
            this.activeMode = continuousMode;
        } else {
            this.activeMode = classicMode;
        }
        activeMode.startGame();
    }

    public void resetGame() {
        if (activeMode != null) {
            activeMode.resetGame();
        }
    }

    public void playerStandingOnBlock(Player player) {
        if (activeMode != null) {
            activeMode.playerStandingOnBlock(player);
        }
    }

    public void playerJoined(Player player) {
        if (activeMode != null) {
            activeMode.playerJoined(player);
        }
    }

    public void sendPlayerToLobby(Player player) {
        if (activeMode != null) {
            activeMode.sendPlayerToLobby(player);
        }
    }

    public boolean trySkip(UUID uuid) {
        if (activeMode != null) {
            return activeMode.trySkip(uuid);
        }
        return false;
    }

    public void readyAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!tracker.getReadyPlayers().contains(uuid)) {
                tracker.getReadyPlayers().add(uuid);
                Bukkit.broadcast(prefixedMessage(
                        Component.text(player.getName() + " ", NamedTextColor.WHITE)
                                .append(Component.text("is now ready (forced)", NamedTextColor.GREEN))));
            }
        }
        BlockShuffle.logger.info("[ReadyAll] All online players have been marked as ready.");
    }

    public World getCurrentGameWorld() {
        if (activeMode != null) {
            return activeMode.getCurrentGameWorld();
        }
        return null;
    }

    public World getLobbyWorld() {
        return this.lobbyWorld;
    }

    public boolean isInProgress() {
        if (activeMode != null) {
            return activeMode.isInProgress();
        }
        return false;
    }

    public boolean isPvpEnabled() {
        return settingsGUI.isPvpEnabled();
    }
}
