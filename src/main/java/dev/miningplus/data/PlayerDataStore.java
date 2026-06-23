package dev.miningplus.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerDataStore {
    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentHashMap<UUID, PlayerData> players = new ConcurrentHashMap<>();

    public PlayerDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players-data.yml");
        reload();
    }

    public void reload() {
        players.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection playerSection = section.getConfigurationSection(key);
            if (playerSection == null) {
                continue;
            }
            UUID uuid = parseUuid(key);
            if (uuid == null) {
                continue;
            }
            players.put(uuid, new PlayerData(
                    uuid,
                    playerSection.getString("name", ""),
                    playerSection.getInt("level", 1),
                    playerSection.getDouble("xp", 0.0D),
                    playerSection.getLong("blocks-mined", 0L),
                    playerSection.getBoolean("vein-miner", true),
                    playerSection.getBoolean("notifications", true),
                    playerSection.getInt("perk-points", 0),
                    playerSection.getInt("perk-points-awarded-level", 1),
                    loadPerkLevels(playerSection.getConfigurationSection("perks")),
                    loadLongMap(playerSection.getConfigurationSection("blocks-by-type")),
                    playerSection.getLong("stats.items-sold", 0L),
                    playerSection.getLong("stats.points-earned", 0L),
                    playerSection.getDouble("stats.money-earned", 0.0D),
                    playerSection.getLong("stats.treasures-found", 0L),
                    playerSection.getLong("stats.hazards-survived", 0L),
                    playerSection.getLong("stats.artifacts-found", 0L),
                    playerSection.getLong("stats.perks-purchased", 0L),
                    playerSection.getLong("currency.shards", 0L),
                    playerSection.getLong("currency.shards-earned", 0L),
                    playerSection.getLong("currency.shards-spent", 0L),
                    playerSection.getLong("currency.artifact-fragments", 0L),
                    playerSection.getLong("currency.artifact-fragments-spent", 0L),
                    loadLongMap(playerSection.getConfigurationSection("artifacts-by-id")),
                    playerSection.getInt("pickaxe.best-level", 1),
                    playerSection.getLong("pickaxe.refines", 0L),
                    playerSection.getLong("pickaxe.tool-upgrades-found", 0L),
                    loadLongMap(playerSection.getConfigurationSection("pickaxe.tool-upgrades-by-id")),
                    playerSection.getLong("pity.blocks-since-artifact", 0L),
                    playerSection.getInt("pity.enchants-since-tool-upgrade", 0),
                    playerSection.getLong("commissions.completed", 0L),
                    playerSection.getLong("stats.event-challenges-completed", 0L),
                    playerSection.getLong("stats.encounters-defeated", 0L),
                    loadActiveCommissions(playerSection.getConfigurationSection("commissions.active")),
                    loadIdSet(playerSection, "journal.claimed")
            ));
        }
    }

    public PlayerData getOrCreate(UUID uuid, String name) {
        PlayerData data = players.computeIfAbsent(uuid, id -> PlayerData.fresh(id, name));
        data.name(name);
        return data;
    }

    public PlayerData get(UUID uuid) {
        return players.get(uuid);
    }

    public int playerCount() {
        return players.size();
    }

    public List<PlayerData> topByLevel(int limit) {
        List<PlayerData> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator.<PlayerData>comparingInt(PlayerData::level)
                .thenComparingDouble(PlayerData::xp)
                .reversed());
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    public boolean save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerData data : players.values()) {
            String path = "players." + data.uuid();
            yaml.set(path + ".name", data.name());
            yaml.set(path + ".level", data.level());
            yaml.set(path + ".xp", data.xp());
            yaml.set(path + ".blocks-mined", data.blocksMined());
            yaml.set(path + ".vein-miner", data.veinMinerEnabled());
            yaml.set(path + ".notifications", data.notificationsEnabled());
            yaml.set(path + ".perk-points", data.perkPoints());
            yaml.set(path + ".perk-points-awarded-level", data.perkPointsAwardedLevel());
            yaml.set(path + ".stats.items-sold", data.itemsSold());
            yaml.set(path + ".stats.points-earned", data.pointsEarned());
            yaml.set(path + ".stats.money-earned", data.moneyEarned());
            yaml.set(path + ".stats.treasures-found", data.treasuresFound());
            yaml.set(path + ".stats.hazards-survived", data.hazardsSurvived());
            yaml.set(path + ".stats.artifacts-found", data.artifactsFound());
            yaml.set(path + ".stats.perks-purchased", data.perksPurchased());
            yaml.set(path + ".currency.shards", data.shards());
            yaml.set(path + ".currency.shards-earned", data.shardsEarned());
            yaml.set(path + ".currency.shards-spent", data.shardsSpent());
            yaml.set(path + ".currency.artifact-fragments", data.artifactFragments());
            yaml.set(path + ".currency.artifact-fragments-spent", data.artifactFragmentsSpent());
            yaml.set(path + ".pickaxe.best-level", data.bestPickaxeLevel());
            yaml.set(path + ".pickaxe.refines", data.pickaxeRefines());
            yaml.set(path + ".pickaxe.tool-upgrades-found", data.toolUpgradesFound());
            yaml.set(path + ".pity.blocks-since-artifact", data.blocksSinceArtifact());
            yaml.set(path + ".pity.enchants-since-tool-upgrade", data.enchantsSinceToolUpgrade());
            yaml.set(path + ".commissions.completed", data.commissionsCompleted());
            yaml.set(path + ".stats.event-challenges-completed", data.eventChallengesCompleted());
            yaml.set(path + ".stats.encounters-defeated", data.encountersDefeated());
            yaml.set(path + ".journal.claimed", new ArrayList<>(data.claimedJournalChapters()));
            for (Map.Entry<String, Long> entry : data.artifactsById().entrySet()) {
                yaml.set(path + ".artifacts-by-id." + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Long> entry : data.toolUpgradesById().entrySet()) {
                yaml.set(path + ".pickaxe.tool-upgrades-by-id." + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Long> entry : data.blocksByType().entrySet()) {
                yaml.set(path + ".blocks-by-type." + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Integer> entry : data.perkLevels().entrySet()) {
                yaml.set(path + ".perks." + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, PlayerCommission> entry : data.activeCommissions().entrySet()) {
                String activePath = path + ".commissions.active." + entry.getKey();
                PlayerCommission commission = entry.getValue();
                yaml.set(activePath + ".started-at", commission.startedAt());
                for (Map.Entry<String, Long> baseline : commission.baselines().entrySet()) {
                    yaml.set(activePath + ".baselines." + baseline.getKey(), baseline.getValue());
                }
            }
        }

        Path target = file.toPath();
        Path directory = target.getParent();
        Path backup = target.resolveSibling(file.getName() + ".bak");
        Path temp = null;
        try {
            Files.createDirectories(directory);
            if (Files.exists(target)) {
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = Files.createTempFile(directory, file.getName(), ".tmp");
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            moveIntoPlace(temp, target);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save players-data.yml: " + e.getMessage());
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
            }
            return false;
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Ignoring invalid player UUID: " + value);
            return null;
        }
    }

    private Map<String, Integer> loadPerkLevels(ConfigurationSection section) {
        Map<String, Integer> perks = new LinkedHashMap<>();
        if (section == null) {
            return perks;
        }
        for (String key : section.getKeys(false)) {
            int level = section.getInt(key, 0);
            if (level > 0) {
                perks.put(key, level);
            }
        }
        return perks;
    }

    private Map<String, Long> loadLongMap(ConfigurationSection section) {
        Map<String, Long> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            long value = section.getLong(key, 0L);
            if (value > 0L) {
                values.put(key, value);
            }
        }
        return values;
    }

    private Map<String, PlayerCommission> loadActiveCommissions(ConfigurationSection section) {
        Map<String, PlayerCommission> commissions = new LinkedHashMap<>();
        if (section == null) {
            return commissions;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection commissionSection = section.getConfigurationSection(key);
            if (commissionSection == null) {
                continue;
            }
            commissions.put(key, new PlayerCommission(
                    key,
                    commissionSection.getLong("started-at", 0L),
                    loadLongMap(commissionSection.getConfigurationSection("baselines"))
            ));
        }
        return commissions;
    }

    private Set<String> loadIdSet(ConfigurationSection section, String path) {
        Set<String> ids = new LinkedHashSet<>();
        if (section == null) {
            return ids;
        }
        for (String value : section.getStringList(path)) {
            if (value != null && !value.isBlank()) {
                ids.add(value);
            }
        }
        return ids;
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
