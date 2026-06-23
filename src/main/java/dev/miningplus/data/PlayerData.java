package dev.miningplus.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerData {
    private final UUID uuid;
    private String name;
    private int level;
    private double xp;
    private long blocksMined;
    private boolean veinMinerEnabled;
    private boolean notificationsEnabled;
    private int perkPoints;
    private int perkPointsAwardedLevel;
    private final Map<String, Integer> perkLevels;
    private final Map<String, Long> blocksByType;
    private final Map<String, Long> artifactsById;
    private final Map<String, Long> toolUpgradesById;
    private final Map<String, PlayerCommission> activeCommissions;
    private final Set<String> claimedJournalChapters;
    private long itemsSold;
    private long pointsEarned;
    private double moneyEarned;
    private long treasuresFound;
    private long hazardsSurvived;
    private long artifactsFound;
    private long blocksSinceArtifact;
    private long perksPurchased;
    private long shards;
    private long shardsEarned;
    private long shardsSpent;
    private long artifactFragments;
    private long artifactFragmentsSpent;
    private int bestPickaxeLevel;
    private long pickaxeRefines;
    private long toolUpgradesFound;
    private int enchantsSinceToolUpgrade;
    private long commissionsCompleted;
    private long eventChallengesCompleted;
    private long encountersDefeated;

    public PlayerData(UUID uuid, String name, int level, double xp, long blocksMined,
                      boolean veinMinerEnabled, boolean notificationsEnabled,
                      int perkPoints, int perkPointsAwardedLevel, Map<String, Integer> perkLevels,
                      Map<String, Long> blocksByType, long itemsSold, long pointsEarned, double moneyEarned,
                      long treasuresFound, long hazardsSurvived, long artifactsFound, long perksPurchased,
                      long shards, long shardsEarned, long shardsSpent,
                      long artifactFragments, long artifactFragmentsSpent, Map<String, Long> artifactsById,
                      int bestPickaxeLevel, long pickaxeRefines, long toolUpgradesFound,
                      Map<String, Long> toolUpgradesById, long blocksSinceArtifact,
                      int enchantsSinceToolUpgrade, long commissionsCompleted,
                      long eventChallengesCompleted, long encountersDefeated,
                      Map<String, PlayerCommission> activeCommissions, Set<String> claimedJournalChapters) {
        this.uuid = uuid;
        this.name = name;
        this.level = Math.max(1, level);
        this.xp = finiteNonNegative(xp);
        this.blocksMined = Math.max(0L, blocksMined);
        this.veinMinerEnabled = veinMinerEnabled;
        this.notificationsEnabled = notificationsEnabled;
        this.perkPoints = Math.max(0, perkPoints);
        this.perkPointsAwardedLevel = Math.max(1, perkPointsAwardedLevel);
        this.perkLevels = new LinkedHashMap<>();
        if (perkLevels != null) {
            for (Map.Entry<String, Integer> entry : perkLevels.entrySet()) {
                String id = normalizeId(entry.getKey());
                if (!id.isBlank() && entry.getValue() != null && entry.getValue() > 0) {
                    this.perkLevels.put(id, entry.getValue());
                }
            }
        }
        this.blocksByType = new LinkedHashMap<>();
        if (blocksByType != null) {
            for (Map.Entry<String, Long> entry : blocksByType.entrySet()) {
                String id = normalizeId(entry.getKey());
                if (!id.isBlank() && entry.getValue() != null && entry.getValue() > 0L) {
                    this.blocksByType.merge(id, entry.getValue(), PlayerData::safeAdd);
                }
            }
        }
        this.artifactsById = new LinkedHashMap<>();
        if (artifactsById != null) {
            for (Map.Entry<String, Long> entry : artifactsById.entrySet()) {
                String id = normalizeId(entry.getKey());
                if (!id.isBlank() && entry.getValue() != null && entry.getValue() > 0L) {
                    this.artifactsById.merge(id, entry.getValue(), PlayerData::safeAdd);
                }
            }
        }
        this.toolUpgradesById = new LinkedHashMap<>();
        if (toolUpgradesById != null) {
            for (Map.Entry<String, Long> entry : toolUpgradesById.entrySet()) {
                String id = normalizeId(entry.getKey());
                if (!id.isBlank() && entry.getValue() != null && entry.getValue() > 0L) {
                    this.toolUpgradesById.merge(id, entry.getValue(), PlayerData::safeAdd);
                }
            }
        }
        this.activeCommissions = new LinkedHashMap<>();
        if (activeCommissions != null) {
            for (Map.Entry<String, PlayerCommission> entry : activeCommissions.entrySet()) {
                String id = normalizeId(entry.getKey());
                PlayerCommission commission = entry.getValue();
                if (!id.isBlank() && commission != null) {
                    this.activeCommissions.put(id, commission);
                }
            }
        }
        this.itemsSold = Math.max(0L, itemsSold);
        this.pointsEarned = Math.max(0L, pointsEarned);
        this.moneyEarned = finiteNonNegative(moneyEarned);
        this.treasuresFound = Math.max(0L, treasuresFound);
        this.hazardsSurvived = Math.max(0L, hazardsSurvived);
        this.artifactsFound = Math.max(0L, artifactsFound);
        this.blocksSinceArtifact = Math.max(0L, blocksSinceArtifact);
        this.perksPurchased = Math.max(0L, perksPurchased);
        this.shards = Math.max(0L, shards);
        this.shardsEarned = Math.max(0L, shardsEarned);
        this.shardsSpent = Math.max(0L, shardsSpent);
        this.artifactFragments = Math.max(0L, artifactFragments);
        this.artifactFragmentsSpent = Math.max(0L, artifactFragmentsSpent);
        this.bestPickaxeLevel = Math.max(1, bestPickaxeLevel);
        this.pickaxeRefines = Math.max(0L, pickaxeRefines);
        this.toolUpgradesFound = Math.max(0L, toolUpgradesFound);
        this.enchantsSinceToolUpgrade = Math.max(0, enchantsSinceToolUpgrade);
        this.commissionsCompleted = Math.max(0L, commissionsCompleted);
        this.eventChallengesCompleted = Math.max(0L, eventChallengesCompleted);
        this.encountersDefeated = Math.max(0L, encountersDefeated);
        this.claimedJournalChapters = new LinkedHashSet<>();
        if (claimedJournalChapters != null) {
            for (String chapterId : claimedJournalChapters) {
                String id = normalizeId(chapterId);
                if (!id.isBlank()) {
                    this.claimedJournalChapters.add(id);
                }
            }
        }
    }

    public static PlayerData fresh(UUID uuid, String name) {
        return new PlayerData(uuid, name, 1, 0.0D, 0L, true, true, 0, 1, Map.of(),
                Map.of(), 0L, 0L, 0.0D, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, Map.of(), 1, 0L, 0L, Map.of(), 0L, 0, 0L, 0L, 0L, Map.of(), Set.of());
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public int level() {
        return level;
    }

    public void level(int level) {
        this.level = Math.max(1, level);
    }

    public double xp() {
        return xp;
    }

    public void xp(double xp) {
        this.xp = finiteNonNegative(xp);
    }

    public void addXp(double amount) {
        if (Double.isFinite(amount) && amount > 0.0D) {
            this.xp = safeAdd(this.xp, amount);
        }
    }

    public long blocksMined() {
        return blocksMined;
    }

    public void incrementBlocksMined() {
        incrementBlockMined("");
    }

    public void incrementBlockMined(String blockId) {
        this.blocksMined = safeAdd(this.blocksMined, 1L);
        String id = normalizeId(blockId);
        if (!id.isBlank()) {
            blocksByType.merge(id, 1L, PlayerData::safeAdd);
        }
    }

    public long minedBlock(String blockId) {
        return blocksByType.getOrDefault(normalizeId(blockId), 0L);
    }

    public Map<String, Long> blocksByType() {
        return new LinkedHashMap<>(blocksByType);
    }

    public boolean veinMinerEnabled() {
        return veinMinerEnabled;
    }

    public void veinMinerEnabled(boolean enabled) {
        this.veinMinerEnabled = enabled;
    }

    public boolean toggleVeinMiner() {
        this.veinMinerEnabled = !this.veinMinerEnabled;
        return this.veinMinerEnabled;
    }

    public boolean notificationsEnabled() {
        return notificationsEnabled;
    }

    public void notificationsEnabled(boolean enabled) {
        this.notificationsEnabled = enabled;
    }

    public boolean toggleNotifications() {
        this.notificationsEnabled = !this.notificationsEnabled;
        return this.notificationsEnabled;
    }

    public int perkPoints() {
        return perkPoints;
    }

    public void perkPoints(int points) {
        this.perkPoints = Math.max(0, points);
    }

    public void addPerkPoints(int points) {
        if (points > 0) {
            if (perkPoints > Integer.MAX_VALUE - points) {
                this.perkPoints = Integer.MAX_VALUE;
                return;
            }
            this.perkPoints += points;
        }
    }

    public boolean spendPerkPoints(int points) {
        if (points <= 0 || perkPoints < points) {
            return false;
        }
        perkPoints -= points;
        return true;
    }

    public int perkPointsAwardedLevel() {
        return perkPointsAwardedLevel;
    }

    public void perkPointsAwardedLevel(int level) {
        this.perkPointsAwardedLevel = Math.max(1, level);
    }

    public int perkLevel(String id) {
        return perkLevels.getOrDefault(normalizeId(id), 0);
    }

    public void perkLevel(String id, int level) {
        String normalized = normalizeId(id);
        if (normalized.isBlank()) {
            return;
        }
        if (level <= 0) {
            perkLevels.remove(normalized);
        } else {
            perkLevels.put(normalized, level);
        }
    }

    public Map<String, Integer> perkLevels() {
        return new LinkedHashMap<>(perkLevels);
    }

    public long itemsSold() {
        return itemsSold;
    }

    public void addItemsSold(long amount) {
        if (amount > 0L) {
            itemsSold = safeAdd(itemsSold, amount);
        }
    }

    public long pointsEarned() {
        return pointsEarned;
    }

    public void addPointsEarned(long amount) {
        if (amount > 0L) {
            pointsEarned = safeAdd(pointsEarned, amount);
        }
    }

    public double moneyEarned() {
        return moneyEarned;
    }

    public void addMoneyEarned(double amount) {
        if (Double.isFinite(amount) && amount > 0.0D) {
            moneyEarned = safeAdd(moneyEarned, amount);
        }
    }

    public long treasuresFound() {
        return treasuresFound;
    }

    public void addTreasuresFound(long amount) {
        if (amount > 0L) {
            treasuresFound = safeAdd(treasuresFound, amount);
        }
    }

    public long hazardsSurvived() {
        return hazardsSurvived;
    }

    public void addHazardsSurvived(long amount) {
        if (amount > 0L) {
            hazardsSurvived = safeAdd(hazardsSurvived, amount);
        }
    }

    public long artifactsFound() {
        return artifactsFound;
    }

    public long blocksSinceArtifact() {
        return blocksSinceArtifact;
    }

    public void incrementBlocksSinceArtifact() {
        blocksSinceArtifact = safeAdd(blocksSinceArtifact, 1L);
    }

    public void resetBlocksSinceArtifact() {
        blocksSinceArtifact = 0L;
    }

    public void addArtifactsFound(long amount) {
        if (amount > 0L) {
            artifactsFound = safeAdd(artifactsFound, amount);
        }
    }

    public void addArtifactFound(String artifactId, long amount) {
        if (amount <= 0L) {
            return;
        }
        addArtifactsFound(amount);
        String id = normalizeId(artifactId);
        if (!id.isBlank()) {
            artifactsById.merge(id, amount, PlayerData::safeAdd);
        }
    }

    public long artifactFound(String artifactId) {
        return artifactsById.getOrDefault(normalizeId(artifactId), 0L);
    }

    public Map<String, Long> artifactsById() {
        return new LinkedHashMap<>(artifactsById);
    }

    public int uniqueArtifactsFound() {
        return artifactsById.size();
    }

    public long toolUpgradesFound() {
        return toolUpgradesFound;
    }

    public int enchantsSinceToolUpgrade() {
        return enchantsSinceToolUpgrade;
    }

    public void addEnchantWithoutToolUpgrade() {
        if (enchantsSinceToolUpgrade < Integer.MAX_VALUE) {
            enchantsSinceToolUpgrade++;
        }
    }

    public void resetEnchantsSinceToolUpgrade() {
        enchantsSinceToolUpgrade = 0;
    }

    public void addToolUpgradeFound(String upgradeId, long amount) {
        if (amount <= 0L) {
            return;
        }
        toolUpgradesFound = safeAdd(toolUpgradesFound, amount);
        String id = normalizeId(upgradeId);
        if (!id.isBlank()) {
            toolUpgradesById.merge(id, amount, PlayerData::safeAdd);
        }
    }

    public long toolUpgradeFound(String upgradeId) {
        return toolUpgradesById.getOrDefault(normalizeId(upgradeId), 0L);
    }

    public Map<String, Long> toolUpgradesById() {
        return new LinkedHashMap<>(toolUpgradesById);
    }

    public long commissionsCompleted() {
        return commissionsCompleted;
    }

    public void addCommissionsCompleted(long amount) {
        if (amount > 0L) {
            commissionsCompleted = safeAdd(commissionsCompleted, amount);
        }
    }

    public long eventChallengesCompleted() {
        return eventChallengesCompleted;
    }

    public void addEventChallengesCompleted(long amount) {
        if (amount > 0L) {
            eventChallengesCompleted = safeAdd(eventChallengesCompleted, amount);
        }
    }

    public long encountersDefeated() {
        return encountersDefeated;
    }

    public void addEncountersDefeated(long amount) {
        if (amount > 0L) {
            encountersDefeated = safeAdd(encountersDefeated, amount);
        }
    }

    public Map<String, PlayerCommission> activeCommissions() {
        return new LinkedHashMap<>(activeCommissions);
    }

    public PlayerCommission activeCommission(String commissionId) {
        return activeCommissions.get(normalizeId(commissionId));
    }

    public boolean hasActiveCommission(String commissionId) {
        return activeCommissions.containsKey(normalizeId(commissionId));
    }

    public void startCommission(PlayerCommission commission) {
        if (commission == null || commission.id().isBlank()) {
            return;
        }
        activeCommissions.put(commission.id(), commission);
    }

    public boolean removeCommission(String commissionId) {
        return activeCommissions.remove(normalizeId(commissionId)) != null;
    }

    public long perksPurchased() {
        return perksPurchased;
    }

    public void addPerksPurchased(long amount) {
        if (amount > 0L) {
            perksPurchased = safeAdd(perksPurchased, amount);
        }
    }

    public long shards() {
        return shards;
    }

    public long shardsEarned() {
        return shardsEarned;
    }

    public long shardsSpent() {
        return shardsSpent;
    }

    public long artifactFragments() {
        return artifactFragments;
    }

    public long artifactFragmentsSpent() {
        return artifactFragmentsSpent;
    }

    public int bestPickaxeLevel() {
        return bestPickaxeLevel;
    }

    public void recordPickaxeLevel(int level) {
        if (level > bestPickaxeLevel) {
            bestPickaxeLevel = level;
        }
    }

    public long pickaxeRefines() {
        return pickaxeRefines;
    }

    public void addPickaxeRefines(long amount) {
        if (amount > 0L) {
            pickaxeRefines = safeAdd(pickaxeRefines, amount);
        }
    }

    public void addShards(long amount) {
        if (amount > 0L) {
            shards = safeAdd(shards, amount);
            shardsEarned = safeAdd(shardsEarned, amount);
        }
    }

    public boolean spendShards(long amount) {
        if (amount <= 0L || shards < amount) {
            return false;
        }
        shards -= amount;
        shardsSpent = safeAdd(shardsSpent, amount);
        return true;
    }

    public void addArtifactFragments(long amount) {
        if (amount > 0L) {
            artifactFragments = safeAdd(artifactFragments, amount);
        }
    }

    public boolean spendArtifactFragments(long amount) {
        if (amount <= 0L || artifactFragments < amount) {
            return false;
        }
        artifactFragments -= amount;
        artifactFragmentsSpent = safeAdd(artifactFragmentsSpent, amount);
        return true;
    }

    public Set<String> claimedJournalChapters() {
        return new LinkedHashSet<>(claimedJournalChapters);
    }

    public boolean hasClaimedJournalChapter(String chapterId) {
        return claimedJournalChapters.contains(normalizeId(chapterId));
    }

    public void claimJournalChapter(String chapterId) {
        String normalized = normalizeId(chapterId);
        if (!normalized.isBlank()) {
            claimedJournalChapters.add(normalized);
        }
    }

    private static String normalizeId(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static long safeAdd(long current, long amount) {
        if (amount <= 0L) {
            return current;
        }
        return current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
    }

    private static double safeAdd(double current, double amount) {
        if (!Double.isFinite(current) || current < 0.0D) {
            current = 0.0D;
        }
        double result = current + amount;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }
}
