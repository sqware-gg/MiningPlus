package dev.miningplus.mining;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record ArtifactDefinition(
        String id,
        boolean enabled,
        String displayName,
        ItemStack item,
        int minAmount,
        int maxAmount,
        double chance,
        boolean affectedByFortune,
        int unlockLevel,
        String permission,
        String setId,
        long fragmentValue,
        Set<Material> blocks,
        Set<String> groups,
        MoneyReward money,
        PointsReward points,
        PointsReward shards,
        List<String> commands
) {
    public boolean active() {
        return enabled && chance > 0.0D && maxAmount > 0;
    }

    public ItemStack toItemStack(int amount) {
        ItemStack clone = item.clone();
        clone.setAmount(Math.max(1, Math.min(item.getType().getMaxStackSize(), amount)));
        return clone;
    }
}
