package dev.miningplus.mining;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record JournalChapter(
        String id,
        String displayName,
        Material icon,
        List<String> lore,
        List<String> requiredChapters,
        List<JournalObjective> objectives,
        double xpReward,
        double moneyReward,
        long pointsReward,
        long shardsReward,
        int perkPointsReward,
        List<ItemStack> itemRewards,
        List<String> commands
) {
}
