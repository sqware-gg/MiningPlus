package dev.miningplus.mining;

import java.util.Locale;

public enum JournalObjectiveType {
    MINE_BLOCKS,
    MINE_BLOCK,
    MINE_GROUP,
    REACH_LEVEL,
    SELL_ITEMS,
    EARN_POINTS,
    EARN_SHARDS,
    EARN_MONEY,
    SPEND_SHARDS,
    SPEND_ARTIFACT_FRAGMENTS,
    BUY_PERKS,
    FIND_TREASURES,
    FIND_ARTIFACTS,
    REACH_PICKAXE_LEVEL,
    REFINE_PICKAXE,
    FIND_TOOL_UPGRADES,
    FIND_TOOL_UPGRADE,
    SURVIVE_HAZARDS,
    COMPLETE_EVENT_CHALLENGES,
    DEFEAT_ENCOUNTERS;

    public static JournalObjectiveType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MINE_BLOCKS;
        }
        String normalized = raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        try {
            return JournalObjectiveType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return MINE_BLOCKS;
        }
    }
}
