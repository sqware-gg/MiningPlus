package dev.miningplus.mining;

public record ShopPurchaseResult(
        Status status,
        MiningShopItem item,
        long cost
) {
    public enum Status {
        SUCCESS,
        DISABLED,
        INVALID,
        LOCKED_LEVEL,
        LOCKED_CHAPTER,
        INSUFFICIENT_SHARDS,
        NO_PICKAXE,
        PICKAXE_MAXED,
        UPGRADE_ALREADY_OWNED
    }
}
