package org.lukeeirl.blockShuffle;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.lukeeirl.blockShuffle.commands.*;
import org.lukeeirl.blockShuffle.events.PlayerListener;
import org.lukeeirl.blockShuffle.game.GameManager;
import org.lukeeirl.blockShuffle.game.PlayerTracker;
import org.lukeeirl.blockShuffle.ui.SettingsGUI;
import org.lukeeirl.blockShuffle.util.SkipManager;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

public final class BlockShuffle extends JavaPlugin {
    private File settingsFile;
    private File skipsFile;

    public static Logger logger;

    @Override
    public void onEnable() {
        logger = this.getLogger();

        this.settingsFile = this.getDataFolder().toPath().resolve("settings.yml").toFile();
        this.createSettingsFile();
        this.skipsFile = this.getDataFolder().toPath().resolve("skips.yml").toFile();
        this.createSkipsFile();

        YamlConfiguration settings = YamlConfiguration.loadConfiguration(this.settingsFile);
        YamlConfiguration skipsConfig = YamlConfiguration.loadConfiguration(this.skipsFile);
        PlayerTracker playerTracker = new PlayerTracker();
        SettingsGUI settingsGUI = new SettingsGUI(this, this.settingsFile, settings);
        SkipManager skipManager = new SkipManager(skipsFile, skipsConfig);
        GameManager gameManager = new GameManager(playerTracker, this, settings, settingsGUI, skipManager);
        PlayerListener playerListener = new PlayerListener(this, playerTracker, gameManager);
        Objects.requireNonNull(this.getCommand("blockshuffle")).setExecutor(new BlockShuffleCommand(playerTracker, gameManager, settingsGUI));
        Objects.requireNonNull(this.getCommand("skipblock")).setExecutor(new SkipBlockCommand(gameManager, playerTracker));
        Objects.requireNonNull(this.getCommand("lobby")).setExecutor(new LobbyCommand(gameManager));
        Objects.requireNonNull(this.getCommand("testmsg")).setExecutor(new TestMessageCommand());
        Objects.requireNonNull(getCommand("giveskips")).setExecutor(new GiveSkipsCommand(skipManager));

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

    private void createSkipsFile() {
        if (!this.skipsFile.exists()) {
            this.saveResource("skips.yml", false);
        }
    }
}
