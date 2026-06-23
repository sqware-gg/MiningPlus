package dev.miningplus.mining;

public record PerkPurchaseResult(Status status, PerkDefinition definition, int level, int cost) {
    public enum Status {
        SUCCESS,
        DISABLED,
        INVALID,
        NO_PERMISSION,
        LOCKED,
        MAXED,
        INSUFFICIENT_POINTS
    }
}
