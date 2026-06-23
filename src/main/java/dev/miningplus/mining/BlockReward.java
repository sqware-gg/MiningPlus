package dev.miningplus.mining;

import java.util.List;
import org.bukkit.Material;

public record BlockReward(
        Material material,
        double xp,
        MoneyReward money,
        PointsReward points,
        PointsReward shards,
        List<DropEntry> drops,
        boolean overrideVanillaDrops
) {
    public boolean hasCustomDrops() {
        return !drops.isEmpty();
    }
}
