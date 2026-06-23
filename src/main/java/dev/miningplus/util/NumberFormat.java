package dev.miningplus.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class NumberFormat {
    private static final DecimalFormat DECIMAL =
            new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat COMPACT =
            new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.US));

    private NumberFormat() {
    }

    public static String decimal(double value) {
        return DECIMAL.format(value);
    }

    public static String integer(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    public static String compact(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000_000.0D) {
            return COMPACT.format(value / 1_000_000_000_000.0D) + "T";
        }
        if (abs >= 1_000_000_000.0D) {
            return COMPACT.format(value / 1_000_000_000.0D) + "B";
        }
        if (abs >= 1_000_000.0D) {
            return COMPACT.format(value / 1_000_000.0D) + "M";
        }
        if (abs >= 1_000.0D) {
            return COMPACT.format(value / 1_000.0D) + "K";
        }
        return DECIMAL.format(value);
    }
}
