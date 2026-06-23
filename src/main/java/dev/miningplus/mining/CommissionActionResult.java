package dev.miningplus.mining;

public record CommissionActionResult(
        Status status,
        CommissionDefinition commission,
        String detail,
        double xp,
        double pickaxeXp,
        double money,
        long points,
        long shards,
        int perkPoints,
        int itemStacks,
        int toolUpgrades
) {
    public CommissionActionResult {
        detail = detail == null ? "" : detail;
    }

    public static CommissionActionResult simple(Status status, CommissionDefinition commission, String detail) {
        return new CommissionActionResult(status, commission, detail, 0.0D, 0.0D, 0.0D,
                0L, 0L, 0, 0, 0);
    }

    public static CommissionActionResult success(CommissionDefinition commission, double xp, double pickaxeXp,
                                                 double money, long points, long shards, int perkPoints,
                                                 int itemStacks, int toolUpgrades) {
        return new CommissionActionResult(Status.SUCCESS, commission, "", xp, pickaxeXp, money, points,
                shards, perkPoints, itemStacks, toolUpgrades);
    }

    public enum Status {
        SUCCESS,
        DISABLED,
        INVALID,
        LOCKED_LEVEL,
        LOCKED_CHAPTER,
        ACTIVE_LIMIT,
        ALREADY_ACTIVE,
        NOT_ACTIVE,
        INCOMPLETE,
        ABANDONED,
        NO_PICKAXE
    }
}
