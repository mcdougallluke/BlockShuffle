package org.lukeeirl.blockShuffle.util;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class SkipManager {
    private final File skipsFile;
    private final YamlConfiguration config;
    private final StatsManager stats;

    public SkipManager(File skipsFile, YamlConfiguration config, StatsManager stats) {
        this.skipsFile = skipsFile;
        this.config = config;
        this.stats = stats;
    }

    public int getPurchasedSkips(UUID uuid) {
        return config.getInt(uuid.toString(), 0);
    }

    public void addSkips(UUID uuid, int amount) {
        int current = getPurchasedSkips(uuid);
        config.set(uuid.toString(), Math.max(current + amount, 0));
        stats.recordSkips(uuid, amount);
        stats.save(uuid);
        save();
    }

    public void consumeSkip(UUID uuid) {
        int current = getPurchasedSkips(uuid);
        if (current > 0) {
            config.set(uuid.toString(), current - 1);
            save();
        }
    }

    private void save() {
        try {
            config.save(skipsFile);
        } catch (IOException e) {
            Bukkit.getLogger().severe("Failed to save skips.yml");
        }
    }
}

