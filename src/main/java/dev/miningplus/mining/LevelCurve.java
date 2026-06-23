package dev.miningplus.mining;

public record LevelCurve(double baseXp, double growth, int maxLevel) {

    public int maxLevel() {
        return Math.max(1, maxLevel);
    }

    public boolean atMaxLevel(int level) {
        return level >= maxLevel();
    }

    public double xpToAdvance(int currentLevel) {
        int level = Math.max(1, currentLevel);
        if (atMaxLevel(level)) {
            return Double.POSITIVE_INFINITY;
        }
        double required = baseXp * Math.pow(growth, level - 1);
        return Double.isFinite(required) ? Math.max(1.0D, required) : Double.MAX_VALUE;
    }
}
