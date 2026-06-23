package dev.miningplus.mining;

public record PointsReward(long min, long max, double chance) {
    public static final PointsReward NONE = new PointsReward(0L, 0L, 0.0D);

    public boolean active() {
        return max > 0L && chance > 0.0D;
    }
}
