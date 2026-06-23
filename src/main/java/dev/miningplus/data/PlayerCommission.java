package dev.miningplus.data;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record PlayerCommission(
        String id,
        long startedAt,
        Map<String, Long> baselines
) {
    public PlayerCommission {
        id = normalizeId(id);
        startedAt = Math.max(0L, startedAt);
        Map<String, Long> sanitized = new LinkedHashMap<>();
        if (baselines != null) {
            for (Map.Entry<String, Long> entry : baselines.entrySet()) {
                String objectiveId = normalizeId(entry.getKey());
                if (!objectiveId.isBlank() && entry.getValue() != null && entry.getValue() > 0L) {
                    sanitized.put(objectiveId, entry.getValue());
                }
            }
        }
        baselines = sanitized;
    }

    public long baseline(String objectiveId) {
        return baselines.getOrDefault(normalizeId(objectiveId), 0L);
    }

    private static String normalizeId(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
