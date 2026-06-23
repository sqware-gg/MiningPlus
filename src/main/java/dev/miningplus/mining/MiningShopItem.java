package dev.miningplus.mining;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record MiningShopItem(
        String id,
        boolean enabled,
        String displayName,
        Material icon,
        List<String> lore,
        long cost,
        int unlockLevel,
        List<String> requiredChapters,
        List<ItemStack> rewardItems,
        double xpReward,
        double pickaxeXpReward,
        double moneyReward,
        long pointsReward,
        int perkPointsReward,
        List<String> toolUpgradeRewards,
        List<String> commands
) {
}
