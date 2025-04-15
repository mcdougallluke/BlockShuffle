package org.lukeeirl.blockShuffle.game;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.ui.SettingsGUI;

import java.util.*;

public class GameManager {
    private final World lobbyWorld;;
    private final SettingsGUI settingsGUI;
    private final ClassicBlockShuffle classicMode;
    private final ContinuousBlockShuffle continuousMode;

    private BSGameMode activeMode;

    public GameManager(PlayerTracker tracker, BlockShuffle plugin, YamlConfiguration settings, SettingsGUI settingsGUI) {
        this.settingsGUI = settingsGUI;
        WorldService worldService = new WorldService();
        this.lobbyWorld = Bukkit.getWorlds().getFirst();

        this.classicMode = new ClassicBlockShuffle(tracker, plugin, settings, settingsGUI, worldService, lobbyWorld);
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
        if (activeMode != null) {
            activeMode.readyAllPlayers();
        }
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

    public void setInProgress(boolean inProgress) {
        if (activeMode != null) {
            activeMode.setInProgress(inProgress);
        }
    }

    public boolean isPvpEnabled() {
        return settingsGUI.isPvpEnabled();
    }
}
