package org.lukeeirl.blockShuffle.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.bukkit.Bukkit.broadcast;
import static org.lukeeirl.blockShuffle.util.PlayerUtils.prefixedMessage;

public class SettingsGUI implements Listener {

    private final Plugin plugin;
    private final File settingsFile;
    private final YamlConfiguration settings;
    private final int[] roundTimeOptions = {30, 60, 180, 300};

    private int roundTimeSeconds;
    private int timeOptionIndex;
    private boolean pvpEnabled;
    private boolean decreaseTime;
    private String gameMode;
    private int blocksToWin;
    private final int[] blocksToWinOptions = {3, 5, 7, 10};
    private int blocksToWinIndex;

    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    public SettingsGUI(Plugin plugin, File settingsFile, YamlConfiguration settings) {
        this.plugin = plugin;
        this.settingsFile = settingsFile;
        this.settings = settings;

        this.roundTimeSeconds = settings.getInt("roundTimeSeconds", 300);
        this.timeOptionIndex = findTimeIndex(roundTimeSeconds);
        this.pvpEnabled = settings.getBoolean("pvpEnabled", false);
        this.decreaseTime = settings.getBoolean("decreaseTime", false);
        this.gameMode = settings.getString("gameMode", "Classic");
        this.blocksToWin = settings.getInt("blocksToWin", 5);
        this.blocksToWinIndex = findBlocksToWinIndex(blocksToWin);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }


    public void openSettingsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, InventoryType.HOPPER, Component.text("Block Shuffle Settings", NamedTextColor.DARK_GRAY, net.kyori.adventure.text.format.TextDecoration.BOLD));

        // Slot 0: Game Mode (always shown)
        gui.setItem(0, createOption(Material.COMPASS, "Mode", gameMode, NamedTextColor.LIGHT_PURPLE));

        // Slot 1: Round Time (Classic/Continuous only)
        if (!gameMode.equals("FirstTo")) {
            String timeDisplay = switch (roundTimeSeconds) {
                case 30 -> "30 sec";
                case 60 -> "1 min";
                case 180 -> "3 min";
                case 300 -> "5 min";
                default -> (roundTimeSeconds / 60) + " min";
            };
            gui.setItem(1, createOption(Material.CLOCK, "Round Time", timeDisplay, NamedTextColor.GOLD));
        }

        // Slot 2: PvP (all modes)
        gui.setItem(2, createOption(Material.IRON_SWORD, "PvP", pvpEnabled ? "ON" : "OFF", pvpEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));

        // Slot 3: Decrease Time (Classic only)
        if (gameMode.equals("Classic")) {
            gui.setItem(3, createOption(Material.REPEATER, "Decrease Time", decreaseTime ? "ON" : "OFF", decreaseTime ? NamedTextColor.GREEN : NamedTextColor.RED));
        }

        // Slot 4: Blocks to Win (FirstTo only)
        if (gameMode.equals("FirstTo")) {
            gui.setItem(4, createOption(Material.DIAMOND, "Blocks to Win", String.valueOf(blocksToWin), NamedTextColor.AQUA));
        }

        openGUIs.put(player.getUniqueId(), gui);
        player.openInventory(gui);
    }

    private ItemStack createOption(Material material, String label, String value, NamedTextColor valueColor) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        Component name = Component.text()
                .append(Component.text(label + ": ", NamedTextColor.GRAY))
                .append(Component.text(value, valueColor).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
                .build();

        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String rawTitle = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!rawTitle.equals("Block Shuffle Settings")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        switch (clicked.getType()) {
            case CLOCK -> {
                timeOptionIndex = (timeOptionIndex + 1) % roundTimeOptions.length;
                roundTimeSeconds = roundTimeOptions[timeOptionIndex];

                String timeStr = switch (roundTimeSeconds) {
                    case 30 -> "30 seconds";
                    case 60 -> "1 minute";
                    case 180 -> "3 minutes";
                    case 300 -> "5 minutes";
                    default -> (roundTimeSeconds / 60) + " minutes";
                };

                broadcast(prefixedMessage(Component.text("Round Time set to " + timeStr, NamedTextColor.AQUA)));
            }
            case IRON_SWORD -> {
                pvpEnabled = !pvpEnabled;
                broadcast(prefixedMessage(Component.text("PvP set to " + (pvpEnabled ? "ON" : "OFF"), NamedTextColor.AQUA)));
            }
            case REPEATER -> {
                decreaseTime = !decreaseTime;
                broadcast(prefixedMessage(Component.text("Decrease Time set to " + (decreaseTime ? "ON" : "OFF"), NamedTextColor.AQUA)));
            }
            case COMPASS -> {
                gameMode = switch (gameMode) {
                    case "Classic" -> "Continuous";
                    case "Continuous" -> "FirstTo";
                    case "FirstTo" -> "Classic";
                    default -> "Classic";
                };
                broadcast(prefixedMessage(Component.text("Game Mode set to " + gameMode, NamedTextColor.AQUA)));
            }
            case DIAMOND -> {
                blocksToWinIndex = (blocksToWinIndex + 1) % blocksToWinOptions.length;
                blocksToWin = blocksToWinOptions[blocksToWinIndex];
                broadcast(prefixedMessage(Component.text("Blocks to Win set to " + blocksToWin, NamedTextColor.AQUA)));
            }
        }

        settings.set("roundTimeSeconds", roundTimeSeconds);
        settings.set("pvpEnabled", pvpEnabled);
        settings.set("decreaseTime", decreaseTime);
        settings.set("gameMode", gameMode);
        settings.set("blocksToWin", blocksToWin);

        try {
            settings.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save settings.yml: " + e.getMessage());
        }


        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAllOpenMenus, 1L);
    }

    private int findTimeIndex(int time) {
        for (int i = 0; i < roundTimeOptions.length; i++) {
            if (roundTimeOptions[i] == time) return i;
        }
        return 3;
    }

    private int findBlocksToWinIndex(int blocks) {
        for (int i = 0; i < blocksToWinOptions.length; i++) {
            if (blocksToWinOptions[i] == blocks) return i;
        }
        return 1; // Default to 5
    }

    private void refreshAllOpenMenus() {
        for (UUID uuid : openGUIs.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                String title = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
                if (title.equals("Block Shuffle Settings")) {
                    openSettingsMenu(player);
                }
            }
        }
    }

    public boolean isContinuousMode() { return gameMode.equalsIgnoreCase("Continuous");}

    public boolean isFirstToMode() { return gameMode.equalsIgnoreCase("FirstTo"); }

    public int getRoundTimeTicks() { return roundTimeSeconds * 20;}

    public boolean isPvpEnabled() { return pvpEnabled; }

    public boolean isDecreaseTime() { return decreaseTime; }

    public int getBlocksToWin() { return blocksToWin; }
}
