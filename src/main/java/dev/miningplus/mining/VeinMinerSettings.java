package dev.miningplus.mining;

import java.util.Set;
import org.bukkit.Material;

public record VeinMinerSettings(
        boolean enabled,
        int maxBlocks,
        boolean requireSneak,
        boolean damageTool,
        double hungerPerBlock,
        boolean includeDiagonals,
        boolean requireMatchingTool,
        Set<Material> tools,
        Set<Material> blocks
) {
    public boolean isVeinBlock(Material material) {
        return blocks.contains(material);
    }

    public boolean isAllowedTool(Material material) {
        return !requireMatchingTool || tools.isEmpty() || tools.contains(material);
    }
}
