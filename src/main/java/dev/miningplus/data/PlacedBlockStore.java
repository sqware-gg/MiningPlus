package dev.miningplus.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Tracks player-placed blocks so they grant no mining rewards when broken.
 * This prevents place-and-mine farming of XP, drops, and money.
 */
public final class PlacedBlockStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Set<LocationKey> placed = ConcurrentHashMap.newKeySet();

    public PlacedBlockStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placed-blocks-data.yml");
        reload();
    }

    public void reload() {
        placed.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String entry : yaml.getStringList("placed")) {
            LocationKey key = parse(entry);
            if (key != null) {
                placed.add(key);
            }
        }
    }

    public void markPlaced(LocationKey key) {
        placed.add(key);
    }

    public boolean isPlaced(LocationKey key) {
        return placed.contains(key);
    }

    public void remove(LocationKey key) {
        placed.remove(key);
    }

    public int size() {
        return placed.size();
    }

    public boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("placed", placed.stream().map(this::serialize).toList());

        Path target = file.toPath();
        Path directory = target.getParent();
        Path temp = null;
        try {
            Files.createDirectories(directory);
            temp = Files.createTempFile(directory, file.getName(), ".tmp");
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            moveIntoPlace(temp, target);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save placed-blocks-data.yml: " + e.getMessage());
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
            return false;
        }
    }

    private String serialize(LocationKey key) {
        return key.world() + ";" + key.x() + ";" + key.y() + ";" + key.z();
    }

    private LocationKey parse(String value) {
        String[] parts = value.split(";");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new LocationKey(parts[0], Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
