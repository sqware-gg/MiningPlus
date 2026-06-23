package dev.miningplus.mining;

import java.util.List;

public record ToolUpgradeDefinition(
        String id,
        boolean enabled,
        String displayName,
        List<String> lore,
        String rarity,
        double weight
) {
    public boolean active() {
        return enabled && weight > 0.0D;
    }
}
