package dev.miningplus.economy;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Soft integration with the standalone PointsPlus plugin via reflection, so MiningPlus can be
 * built and run without PointsPlus present on the classpath. Mirrors the static
 * {@code dev.pointsplus.api.PointsPlusApi} surface.
 */
public final class PointsHook {
    private static final String API_CLASS = "dev.pointsplus.api.PointsPlusApi";

    private final JavaPlugin plugin;
    private boolean available;
    private Method giveMethod;
    private Method takeMethod;
    private Method balanceOrZeroMethod;

    public PointsHook(JavaPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        available = false;
        Plugin pointsPlugin = plugin.getServer().getPluginManager().getPlugin("PointsPlus");
        if (pointsPlugin == null || !pointsPlugin.isEnabled()) {
            return;
        }
        try {
            Class<?> api = Class.forName(API_CLASS);
            giveMethod = api.getMethod("give", Player.class, long.class);
            takeMethod = api.getMethod("take", Player.class, long.class);
            balanceOrZeroMethod = api.getMethod("balanceOrZero", UUID.class);
            available = true;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("PointsPlus found but its API could not be hooked: " + e.getMessage());
            available = false;
        }
    }

    public boolean available() {
        return available;
    }

    public boolean give(Player player, long amount) {
        if (!available || player == null || amount <= 0L) {
            return false;
        }
        try {
            return (boolean) giveMethod.invoke(null, player, amount);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to give points: " + e.getMessage());
            return false;
        }
    }

    public boolean take(Player player, long amount) {
        if (!available || player == null || amount <= 0L) {
            return false;
        }
        try {
            return (boolean) takeMethod.invoke(null, player, amount);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Failed to take points: " + e.getMessage());
            return false;
        }
    }

    public long balance(Player player) {
        if (!available || player == null) {
            return 0L;
        }
        try {
            return (long) balanceOrZeroMethod.invoke(null, player.getUniqueId());
        } catch (ReflectiveOperationException e) {
            return 0L;
        }
    }
}
