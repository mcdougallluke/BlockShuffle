package org.lukeeirl.blockShuffle;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.lukeeirl.blockShuffle.commands.*;
import org.lukeeirl.blockShuffle.events.PlayerListener;
import org.lukeeirl.blockShuffle.game.GameManager;
import org.lukeeirl.blockShuffle.game.PlayerTracker;
import org.lukeeirl.blockShuffle.ui.SettingsGUI;
import org.lukeeirl.blockShuffle.util.CreeperManager;
import org.lukeeirl.blockShuffle.util.SkipManager;
import org.lukeeirl.blockShuffle.util.StatsManager;

import java.io.File;
import java.util.Objects;
import java.util.logging.Logger;

public final class BlockShuffle extends JavaPlugin {
    private File settingsFile;
    private File skipsFile;
    private File creeperFile;

    public static Logger logger;

    @Override
    public void onEnable() {
        logger = this.getLogger();

        this.settingsFile = this.getDataFolder().toPath().resolve("settings.yml").toFile();
        this.createSettingsFile();
        this.skipsFile = this.getDataFolder().toPath().resolve("skips.yml").toFile();
        this.createSkipsFile();
        this.creeperFile = this.getDataFolder().toPath().resolve("creeper.yml").toFile();
        this.createCreeperFile();

        YamlConfiguration settings = YamlConfiguration.loadConfiguration(this.settingsFile);
        YamlConfiguration skipsConfig = YamlConfiguration.loadConfiguration(this.skipsFile);
        YamlConfiguration creeperConfig = YamlConfiguration.loadConfiguration(this.creeperFile);
        PlayerTracker playerTracker = new PlayerTracker();
        SettingsGUI settingsGUI = new SettingsGUI(this, this.settingsFile, settings);
        StatsManager statsManager = new StatsManager(this);
        SkipManager skipManager = new SkipManager(skipsFile, skipsConfig, statsManager);
        CreeperManager creeperManager = new CreeperManager(creeperFile, creeperConfig);
        GameManager gameManager = new GameManager(playerTracker, this, settings, settingsGUI, skipManager, statsManager, creeperManager);
        PlayerListener playerListener = new PlayerListener(this, playerTracker, gameManager);
        Objects.requireNonNull(this.getCommand("blockshuffle")).setExecutor(new BlockShuffleCommand(playerTracker, gameManager, settingsGUI));
        Objects.requireNonNull(this.getCommand("skipblock")).setExecutor(new SkipBlockCommand(gameManager, playerTracker));
        Objects.requireNonNull(this.getCommand("lobby")).setExecutor(new LobbyCommand(gameManager));
        Objects.requireNonNull(this.getCommand("testmsg")).setExecutor(new TestMessageCommand());
        Objects.requireNonNull(this.getCommand("giveskips")).setExecutor(new GiveSkipsCommand(skipManager));
        Objects.requireNonNull(this.getCommand("stats")).setExecutor(new StatsCommand(statsManager, skipManager));

        CreeperCommand creeperCommand = new CreeperCommand(creeperManager);
        Objects.requireNonNull(this.getCommand("creeper")).setExecutor(creeperCommand);
        Objects.requireNonNull(this.getCommand("creeper")).setTabCompleter(creeperCommand);

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

    private void createCreeperFile() {
        if (!this.creeperFile.exists()) {
            this.saveResource("creeper.yml", false);
        }
    }
}
