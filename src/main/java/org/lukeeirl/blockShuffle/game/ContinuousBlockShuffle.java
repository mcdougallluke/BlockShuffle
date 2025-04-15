package org.lukeeirl.blockShuffle.game;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.lukeeirl.blockShuffle.BlockShuffle;
import org.lukeeirl.blockShuffle.ui.SettingsGUI;

import java.util.UUID;

public class ContinuousBlockShuffle implements BSGameMode {

    private final PlayerTracker tracker;
    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final SettingsGUI settingsGUI;
    private final WorldService worldService;
    private final World lobbyWorld;

    public ContinuousBlockShuffle(
            PlayerTracker tracker,
            BlockShuffle plugin,
            YamlConfiguration settings,
            SettingsGUI settingsGUI,
            WorldService worldService,
            World lobbyWorld
    ) {
        this.tracker = tracker;
        this.plugin = plugin;
        this.settings = settings;
        this.settingsGUI = settingsGUI;
        this.worldService = worldService;
        this.lobbyWorld = lobbyWorld;
    }

    @Override
    public void startGame() {

    }

    @Override
    public void resetGame() {

    }

    @Override
    public void playerStandingOnBlock(Player player) {

    }

    @Override
    public void playerJoined(Player player) {

    }

    @Override
    public void sendPlayerToLobby(Player player) {

    }

    @Override
    public boolean trySkip(UUID uuid) {
        return false;
    }

    @Override
    public void readyAllPlayers() {

    }

    @Override
    public boolean isInProgress() {
        return false;
    }

    @Override
    public void setInProgress(boolean value) {

    }

    @Override
    public World getCurrentGameWorld() {
        return null;
    }
}
