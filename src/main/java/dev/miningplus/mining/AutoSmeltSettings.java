package dev.miningplus.mining;

import java.util.Map;
import org.bukkit.Material;

public record AutoSmeltSettings(boolean enabled, Map<Material, Material> recipes) {
    public Material smelt(Material input) {
        return recipes.getOrDefault(input, input);
    }

    public boolean canSmelt(Material input) {
        return recipes.containsKey(input);
    }
}
