package dev.miningplus.mining;

import org.bukkit.Material;

public record PerkDefinition(
        String id,
        boolean enabled,
        String displayName,
        Material icon,
        int maxLevel,
        int costPerLevel,
        int unlockLevel,
        String permission,
        double xpMultiplierPerLevel,
        double pickaxeXpMultiplierPerLevel,
        double moneyMultiplierPerLevel,
        double pointsMultiplierPerLevel,
        double shardMultiplierPerLevel,
        double treasureChancePerLevel,
        double artifactChancePerLevel,
        double hazardChanceReductionPerLevel,
        double hazardDamageReductionPerLevel
) {
    public boolean active() {
        return enabled && maxLevel > 0 && costPerLevel > 0;
    }

    public int costForNextLevel(int currentLevel) {
        return currentLevel >= maxLevel ? Integer.MAX_VALUE : costPerLevel;
    }
}
