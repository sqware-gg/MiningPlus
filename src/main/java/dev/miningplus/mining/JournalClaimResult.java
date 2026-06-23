package dev.miningplus.mining;

public record JournalClaimResult(
        boolean success,
        String messageKey,
        JournalChapter chapter,
        String detail,
        double xp,
        double money,
        long points,
        long shards,
        int perkPoints,
        int itemStacks
) {
    public static JournalClaimResult failure(String messageKey, JournalChapter chapter, String detail) {
        return new JournalClaimResult(false, messageKey, chapter, detail, 0.0D, 0.0D, 0L, 0L, 0, 0);
    }

    public static JournalClaimResult success(JournalChapter chapter, double xp, double money,
                                             long points, long shards, int perkPoints, int itemStacks) {
        return new JournalClaimResult(true, "journal-claimed", chapter, "", xp, money, points, shards,
                perkPoints, itemStacks);
    }
}
