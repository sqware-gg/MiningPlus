package dev.miningplus.mining;

public record PickaxeProgressionSettings(
        boolean enabled,
        String permission,
        LevelCurve curve,
        double xpPerRewardBlock,
        double xpFromMiningXp,
        double miningXpMultiplierPerLevel,
        double artifactChancePerLevel,
        boolean refineEnabled,
        double refineMoneyBase,
        double refineMoneyPerLevel,
        long refineShardBase,
        long refineShardPerLevel,
        double refineXp
) {
    public boolean active() {
        return enabled;
    }
}
