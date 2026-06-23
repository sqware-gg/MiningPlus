package dev.miningplus.mining;

public record AbilitySettings(
        Ability explosive,
        Ability haste,
        Ability bonusXp,
        Ability oreSense,
        Ability stoneguard
) {
    /**
     * A single mining ability. Procs randomly while mining when the player has the
     * permission and has reached the unlock level.
     *
     * @param magnitude ability-specific value (explosive radius, haste/resistance amplifier,
     *                  xp multiplier, or ore-sense radius).
     * @param duration  ability-specific duration in ticks when applicable.
     */
    public record Ability(
            boolean enabled,
            double chance,
            int unlockLevel,
            String permission,
            double magnitude,
            int duration
    ) {
        public boolean active() {
            return enabled && chance > 0.0D;
        }
    }
}
