package dev.miningplus.mining;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record ArtifactResearchDefinition(
        String id,
        boolean enabled,
        String displayName,
        Material icon,
        List<String> lore,
        int unlockLevel,
        Set<String> requiredSets,
        long fragmentCost,
        List<ItemStack> rewardItems,
        double xpReward,
        double pickaxeXpReward,
        double moneyReward,
        long pointsReward,
        long shardsReward,
        List<String> commands
) {
    public boolean active() {
        return enabled && fragmentCost > 0L;
    }
}
