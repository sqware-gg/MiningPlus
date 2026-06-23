package dev.miningplus.mining;

import org.bukkit.inventory.ItemStack;

public record DropEntry(
        String id,
        String displayName,
        ItemStack item,
        int minAmount,
        int maxAmount,
        double chance,
        boolean affectedByFortune
) {
    public ItemStack toItemStack(int amount) {
        ItemStack clone = item.clone();
        clone.setAmount(Math.max(1, Math.min(item.getType().getMaxStackSize(), amount)));
        return clone;
    }
}
