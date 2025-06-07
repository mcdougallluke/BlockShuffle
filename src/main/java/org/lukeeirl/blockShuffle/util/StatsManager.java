package org.lukeeirl.blockShuffle.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.lukeeirl.blockShuffle.BlockShuffle;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatsManager {

    private final File statsFile;
    private final YamlConfiguration config;
    private final Map<UUID, PlayerStats> cache = new HashMap<>();

    public StatsManager(Plugin plugin) {
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        this.config = YamlConfiguration.loadConfiguration(statsFile);
        loadAllStats();
    }

    private void loadAllStats() {
        for (String key : config.getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            ConfigurationSection sec = config.getConfigurationSection(key);
            if (sec != null) {
                cache.put(uuid, new PlayerStats(
                        sec.getInt("gamesPlayed", 0),
                        sec.getInt("gamesWon", 0),
                        sec.getInt("skipsBought", 0),
                        sec.getInt("blocksSteppedOn", 0)
                ));
            }
        }
    }

    public void recordPlayed(UUID uuid) {
        PlayerStats old = get(uuid);
        PlayerStats updated = old.incrementPlayed();
        cache.put(uuid, updated);
        save(uuid);
    }

    public void recordWin(UUID uuid) {
        PlayerStats old = get(uuid);
        PlayerStats updated = old.incrementWon();
        cache.put(uuid, updated);
        save(uuid);
    }

    public void recordSkips(UUID uuid, int n) {
        PlayerStats old = get(uuid);
        PlayerStats updated = old.addSkips(n);
        cache.put(uuid, updated);
        save(uuid);
    }

    public void recordBlockSteppedOn(UUID uuid) {
        PlayerStats old = get(uuid);
        PlayerStats updated = old.incrementBlocksSteppedOn();
        cache.put(uuid, updated);
        save(uuid);
    }

    public PlayerStats get(UUID uuid) {
        return cache.computeIfAbsent(uuid, id -> new PlayerStats());
    }

    public void save(UUID uuid) {
        PlayerStats s = cache.get(uuid);
        config.set(uuid + ".gamesPlayed", s.gamesPlayed());
        config.set(uuid + ".gamesWon",    s.gamesWon());
        config.set(uuid + ".skipsBought", s.skipsBought());
        config.set(uuid + ".blocksSteppedOn", s.blocksSteppedOn());
        try { config.save(statsFile); }
        catch (IOException e) { BlockShuffle.logger.severe("Could not save stats.yml"); }
    }

    public void saveAll() {
        cache.keySet().forEach(this::save);
    }
}