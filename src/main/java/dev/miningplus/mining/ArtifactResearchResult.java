package dev.miningplus.mining;

public record ArtifactResearchResult(
        Status status,
        ArtifactResearchDefinition research,
        long cost,
        double xpAwarded,
        double pickaxeXpAwarded,
        double moneyAwarded,
        long pointsAwarded,
        long shardsAwarded,
        int itemStacksAwarded
) {
    public enum Status {
        SUCCESS,
        DISABLED,
        INVALID,
        LOCKED_LEVEL,
        LOCKED_SET,
        INSUFFICIENT_FRAGMENTS,
        NO_PICKAXE,
        PICKAXE_MAXED
    }
}
