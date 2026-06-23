package dev.miningplus.mining;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

public record MiningEventSettings(
        List<TreasureEvent> treasures,
        List<HazardEvent> hazards
) {
    public record Trigger(
            String id,
            boolean enabled,
            double chance,
            int unlockLevel,
            String permission,
            Set<Material> blocks
    ) {
        public boolean activeFor(Material material) {
            return enabled && chance > 0.0D && (blocks.isEmpty() || blocks.contains(material));
        }
    }

    public record EventEffects(
            Particle particle,
            int particleCount,
            Sound sound,
            float volume,
            float pitch
    ) {
    }

    public record TreasureEvent(
            Trigger trigger,
            MoneyReward money,
            PointsReward points,
            PointsReward shards,
            List<DropEntry> drops,
            List<String> commands,
            String message,
            EventEffects effects,
            List<EventAction> actions
    ) {
    }

    public record HazardEvent(
            Trigger trigger,
            double damage,
            int fireTicks,
            double exhaustion,
            PotionEffectType effect,
            int effectDurationTicks,
            int effectAmplifier,
            String message,
            EventEffects effects,
            List<EventAction> actions
    ) {
    }

    public enum EventActionType {
        SCAN_ORES,
        TIMED_CHALLENGE,
        SPAWN_ENCOUNTER;

        public static EventActionType from(String raw) {
            if (raw == null || raw.isBlank()) {
                return SCAN_ORES;
            }
            String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            try {
                return EventActionType.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                return SCAN_ORES;
            }
        }
    }

    public record EventAction(
            String id,
            EventActionType type,
            String message,
            String failMessage,
            String completeMessage,
            int radius,
            int markerCount,
            int durationTicks,
            int requiredBlocks,
            Set<Material> blocks,
            Set<String> groups,
            EntityType entityType,
            String entityName,
            int entityCount,
            double entityHealth,
            double xpReward,
            double pickaxeXpReward,
            MoneyReward money,
            PointsReward points,
            PointsReward shards,
            List<ItemStack> rewardItems,
            List<DropEntry> drops,
            List<String> commands,
            EventEffects effects
    ) {
    }
}
