package dev.miningplus.mining;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PerkSettings(
        int pointsPerLevel,
        int maxBankedPoints,
        List<PerkDefinition> definitions
) {
    public PerkDefinition definition(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (PerkDefinition definition : definitions) {
            if (definition.id().equalsIgnoreCase(id)) {
                return definition;
            }
        }
        return null;
    }

    public Map<String, PerkDefinition> definitionsById() {
        Map<String, PerkDefinition> byId = new LinkedHashMap<>();
        for (PerkDefinition definition : definitions) {
            byId.put(definition.id(), definition);
        }
        return byId;
    }
}
