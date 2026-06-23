package dev.miningplus.mining;

public record MoneyReward(double min, double max, double chance) {
    public static final MoneyReward NONE = new MoneyReward(0.0D, 0.0D, 0.0D);

    public boolean active() {
        return max > 0.0D && chance > 0.0D;
    }
}
