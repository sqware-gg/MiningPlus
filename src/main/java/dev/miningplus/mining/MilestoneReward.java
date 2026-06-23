package dev.miningplus.mining;

import java.util.List;

public record MilestoneReward(
        int level,
        double money,
        List<String> commands,
        String message
) {
}
