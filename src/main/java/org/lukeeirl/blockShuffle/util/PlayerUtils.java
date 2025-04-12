package org.lukeeirl.blockShuffle.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class PlayerUtils {
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static void resetPlayerState(Player player, GameMode gameMode) {
        player.setGameMode(gameMode);
        player.getInventory().clear();
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue());
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setExhaustion(0f);
        player.setExp(0f);
        player.setLevel(0);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));

        // Remove all active potion effects
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        // Remove all advancements
        AdvancementProgress progress;
        for (@NotNull Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement advancement = it.next();
            progress = player.getAdvancementProgress(advancement);
            for (String criterion : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criterion);
            }
        }
    }

    public static String formatMaterialName(Material material) {
        return Arrays.stream(material.name().split("_"))
                .map(word -> word.toLowerCase(Locale.ROOT))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    public static void launchFireworkAt(Location location) {
        Firework firework = (Firework) location.getWorld().spawnEntity(location, org.bukkit.entity.EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = firework.getFireworkMeta();

        meta.addEffect(FireworkEffect.builder()
                .flicker(true)
                .trail(true)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.GREEN)
                .withFade(Color.LIME)
                .build());
        meta.setPower(1);

        firework.setFireworkMeta(meta);
    }

    public static Component prefixedMessage(Component message) {
        return mm.deserialize("<dark_gray>[<gradient:#ADFAFF:#80A8FF>Block Shuffle</gradient><dark_gray>] <white>»</white> ")
                .append(message);
    }

    public static Component formatStatusMessage(String playerName, String status, NamedTextColor statusColor) {
        return mm.deserialize("<dark_gray>[<gradient:#ADFAFF:#80A8FF>Block Shuffle</gradient><dark_gray>] <white>»</white> ")
                .append(Component.text(playerName + " ", NamedTextColor.WHITE))
                .append(Component.text(status, statusColor));
    }
}

