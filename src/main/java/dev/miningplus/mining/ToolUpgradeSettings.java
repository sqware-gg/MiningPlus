package dev.miningplus.mining;

import java.util.List;

public record ToolUpgradeSettings(
        boolean enabled,
        boolean requireForFeatures,
        boolean allowMultiplePerEnchant,
        double baseChance,
        double chancePerLevel,
        double maxChance,
        int pityEnchants,
        int pityMinimumEnchantLevel,
        List<ToolUpgradeDefinition> definitions
) {
    public ToolUpgradeDefinition definition(String id) {
        for (ToolUpgradeDefinition definition : definitions) {
            if (definition.id().equalsIgnoreCase(id)) {
                return definition;
            }
        }
        return null;
    }
}
