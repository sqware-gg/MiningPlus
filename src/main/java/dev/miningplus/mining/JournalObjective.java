package dev.miningplus.mining;

public record JournalObjective(
        String id,
        JournalObjectiveType type,
        String target,
        long amount,
        String description
) {
}
