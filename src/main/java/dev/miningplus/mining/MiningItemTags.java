package dev.miningplus.mining;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiningItemTags {
    private static final long XP_SCALE = 1000L;

    private MiningItemTags() {
    }

    public static void tagCustomItem(JavaPlugin plugin, ItemStack item, String kind, String id) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key(plugin, "item-kind"), PersistentDataType.STRING, normalize(kind));
        container.set(key(plugin, "item-id"), PersistentDataType.STRING, normalize(id));
        item.setItemMeta(meta);
    }

    public static String itemKind(JavaPlugin plugin, ItemStack item) {
        return stringTag(plugin, item, "item-kind");
    }

    public static String itemId(JavaPlugin plugin, ItemStack item) {
        return stringTag(plugin, item, "item-id");
    }

    public static int pickaxeLevel(JavaPlugin plugin, ItemStack item) {
        Integer value = integerTag(plugin, item, "pickaxe-level");
        return value == null ? 1 : Math.max(1, value);
    }

    public static double pickaxeXp(JavaPlugin plugin, ItemStack item) {
        Long value = longTag(plugin, item, "pickaxe-xp");
        return value == null ? 0.0D : Math.max(0.0D, value / (double) XP_SCALE);
    }

    public static boolean hasPickaxeData(JavaPlugin plugin, ItemStack item) {
        return hasPickaxeProgress(plugin, item) || !toolUpgrades(plugin, item).isEmpty();
    }

    public static boolean hasPickaxeProgress(JavaPlugin plugin, ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(key(plugin, "pickaxe-level"), PersistentDataType.INTEGER)
                || container.has(key(plugin, "pickaxe-xp"), PersistentDataType.LONG);
    }

    public static void setPickaxeProgress(JavaPlugin plugin, ItemStack item, int level, double xp) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key(plugin, "pickaxe-level"), PersistentDataType.INTEGER, Math.max(1, level));
        container.set(key(plugin, "pickaxe-xp"), PersistentDataType.LONG,
                Math.max(0L, Math.round(Math.max(0.0D, xp) * XP_SCALE)));
        item.setItemMeta(meta);
    }

    public static void setToolUpgrades(JavaPlugin plugin, ItemStack item, Set<String> upgradeIds) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        Set<String> normalized = new LinkedHashSet<>();
        if (upgradeIds != null) {
            for (String upgradeId : upgradeIds) {
                String value = normalize(upgradeId);
                if (!value.isBlank()) {
                    normalized.add(value);
                }
            }
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = key(plugin, "tool-upgrades");
        if (normalized.isEmpty()) {
            container.remove(key);
        } else {
            container.set(key, PersistentDataType.STRING, String.join(",", normalized));
        }
        item.setItemMeta(meta);
    }

    public static Set<String> toolUpgrades(JavaPlugin plugin, ItemStack item) {
        Set<String> upgrades = new LinkedHashSet<>();
        String raw = stringTag(plugin, item, "tool-upgrades");
        if (raw.isBlank()) {
            return upgrades;
        }
        for (String part : raw.split(",")) {
            String normalized = normalize(part);
            if (!normalized.isBlank()) {
                upgrades.add(normalized);
            }
        }
        return upgrades;
    }

    public static boolean hasToolUpgrade(JavaPlugin plugin, ItemStack item, String upgradeId) {
        return toolUpgrades(plugin, item).contains(normalize(upgradeId));
    }

    public static boolean addToolUpgrade(JavaPlugin plugin, ItemStack item, String upgradeId) {
        String normalized = normalize(upgradeId);
        if (item == null || item.getType().isAir() || normalized.isBlank()) {
            return false;
        }
        Set<String> upgrades = toolUpgrades(plugin, item);
        if (!upgrades.add(normalized)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().set(key(plugin, "tool-upgrades"),
                PersistentDataType.STRING, String.join(",", upgrades));
        item.setItemMeta(meta);
        return true;
    }

    private static String stringTag(JavaPlugin plugin, ItemStack item, String key) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return "";
        }
        String value = meta.getPersistentDataContainer().get(key(plugin, key), PersistentDataType.STRING);
        return value == null ? "" : normalize(value);
    }

    private static Integer integerTag(JavaPlugin plugin, ItemStack item, String key) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(key(plugin, key), PersistentDataType.INTEGER);
    }

    private static Long longTag(JavaPlugin plugin, ItemStack item, String key) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(key(plugin, key), PersistentDataType.LONG);
    }

    private static NamespacedKey key(JavaPlugin plugin, String id) {
        return new NamespacedKey(plugin, id);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-');
    }
}
