package org.lukeeirl.blockShuffle;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.lukeeirl.blockShuffle.commands.BlockShuffleCommand;
import org.lukeeirl.blockShuffle.commands.LobbyCommand;
import org.lukeeirl.blockShuffle.commands.SkipBlockCommand;
import org.lukeeirl.blockShuffle.events.PlayerListener;
import org.lukeeirl.blockShuffle.game.GameManager;
import org.lukeeirl.blockShuffle.game.PlayerTracker;
import org.lukeeirl.blockShuffle.ui.SettingsGUI;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

public final class BlockShuffle extends JavaPlugin {
    private File settingsFile;

    public static Logger logger;

    @Override
    public void onEnable() {
        logger = this.getLogger();

        this.settingsFile = this.getDataFolder().toPath().resolve("settings.yml").toFile();
        this.createSettingsFile();

        YamlConfiguration settings = YamlConfiguration.loadConfiguration(this.settingsFile);
        PlayerTracker playerTracker = new PlayerTracker();
        SettingsGUI settingsGUI = new SettingsGUI(this);
        GameManager gameManager = new GameManager(playerTracker, this, settings, settingsGUI);
        PlayerListener playerListener = new PlayerListener(this, playerTracker, gameManager);
        Objects.requireNonNull(this.getCommand("blockshuffle")).setExecutor(new BlockShuffleCommand(playerTracker, gameManager, settingsGUI));
        Objects.requireNonNull(this.getCommand("skipblock")).setExecutor(new SkipBlockCommand(gameManager, playerTracker));
        Objects.requireNonNull(this.getCommand("lobby")).setExecutor(new LobbyCommand(playerTracker, gameManager));

        this.getServer().getPluginManager().registerEvents(playerListener, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void createSettingsFile() {
        if (!this.settingsFile.exists()) {
            this.saveResource("settings.yml", false);
        }
    }
}
