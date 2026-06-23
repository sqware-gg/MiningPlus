package dev.miningplus.mining;

import java.util.List;
import java.util.Set;

public record ArtifactSetDefinition(
        String id,
        boolean enabled,
        String displayName,
        List<String> lore,
        Set<String> artifactIds,
        double miningXpMultiplier,
        double pickaxeXpMultiplier,
        double moneyMultiplier,
        double pointsMultiplier,
        double shardMultiplier,
        double treasureChance,
        double artifactChance,
        double hazardChanceReduction,
        double hazardDamageReduction
) {
    public boolean active() {
        return enabled && !artifactIds.isEmpty();
    }
}
