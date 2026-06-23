package dev.miningplus.mining;

import org.bukkit.Particle;
import org.bukkit.Sound;

public record FeedbackSettings(
        boolean enabled,
        int miningCooldownTicks,
        Effect mining,
        Effect artifact,
        Effect sell,
        Effect refine,
        Effect journal,
        Effect shop,
        Effect levelUp
) {
    public record Effect(
            boolean enabled,
            String actionBar,
            boolean titleEnabled,
            String title,
            String subtitle,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks,
            boolean soundEnabled,
            Sound sound,
            float volume,
            float pitch,
            boolean particlesEnabled,
            Particle particle,
            int particleCount,
            double offset,
            double speed
    ) {
    }
}
