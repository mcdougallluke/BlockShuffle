package org.lukeeirl.blockShuffle.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.bukkit.Bukkit.broadcast;
import static org.lukeeirl.blockShuffle.util.PlayerUtils.prefixedMessage;

public class SettingsGUI implements Listener {

    private final Plugin plugin;

    private int roundTimeMinutes = 5;
    private boolean pvpEnabled = false;
    private String gameMode = "Classic";

    private final Map<UUID, Inventory> openGUIs = new HashMap<>();

    public SettingsGUI(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openSettingsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, InventoryType.HOPPER, Component.text("Block Shuffle Settings", NamedTextColor.DARK_GRAY, net.kyori.adventure.text.format.TextDecoration.BOLD));

        gui.setItem(1, createOption(Material.CLOCK, "Round Time", roundTimeMinutes + " min", NamedTextColor.GOLD));
        gui.setItem(2, createOption(Material.IRON_SWORD, "PvP", pvpEnabled ? "ON" : "OFF", pvpEnabled ? NamedTextColor.GREEN : NamedTextColor.RED));
        gui.setItem(3, createOption(Material.COMPASS, "Mode", gameMode, NamedTextColor.LIGHT_PURPLE));

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
                roundTimeMinutes = (roundTimeMinutes == 5) ? 3 : 5;
                broadcast(prefixedMessage(Component.text("Round Time set to " + roundTimeMinutes + " minutes", NamedTextColor.AQUA)));
            }
            case IRON_SWORD -> {
                pvpEnabled = !pvpEnabled;
                broadcast(prefixedMessage(Component.text("PvP toggled to " + (pvpEnabled ? "ON" : "OFF"), NamedTextColor.AQUA)));
            }
            case COMPASS -> {
                gameMode = gameMode.equals("Classic") ? "Continuous" : "Classic";
                broadcast(prefixedMessage(Component.text("Game Mode set to " + gameMode, NamedTextColor.AQUA)));
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::refreshAllOpenMenus, 1L);
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


    public int getRoundTimeTicks() {
        return roundTimeMinutes * 60 * 20;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }
}
