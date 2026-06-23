package dev.miningplus.mining;

public record PickaxeRefineResult(
        Status status,
        int level,
        double xp,
        double moneyCost,
        long shardCost,
        double xpGained
) {
    public enum Status {
        SUCCESS,
        DISABLED,
        NO_PICKAXE,
        MAX_LEVEL,
        NO_ECONOMY,
        INSUFFICIENT_MONEY,
        INSUFFICIENT_SHARDS
    }
}
