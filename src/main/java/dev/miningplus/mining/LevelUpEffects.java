package dev.miningplus.mining;

import org.bukkit.Particle;
import org.bukkit.Sound;

public record LevelUpEffects(
        boolean broadcast,
        boolean soundEnabled,
        Sound sound,
        float soundVolume,
        float soundPitch,
        boolean particleEnabled,
        Particle particle,
        int particleCount
) {
}
