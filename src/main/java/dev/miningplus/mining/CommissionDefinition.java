package dev.miningplus.mining;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record CommissionDefinition(
        String id,
        boolean enabled,
        String displayName,
        Material icon,
        List<String> lore,
        int unlockLevel,
        List<String> requiredChapters,
        List<JournalObjective> objectives,
        double xpReward,
        double pickaxeXpReward,
        double moneyReward,
        long pointsReward,
        long shardsReward,
        int perkPointsReward,
        List<ItemStack> itemRewards,
        List<String> toolUpgradeRewards,
        List<String> commands
) {
}
