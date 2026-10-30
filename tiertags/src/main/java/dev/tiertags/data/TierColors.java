package dev.tiertags.data;

import java.util.Locale;
import java.util.Map;

/** Colors matching the tierlist websites. */
public final class TierColors {
    private static final int RETIRED = 0xA2D6FF;

    private static final Map<String, Integer> TIERS = Map.of(
            "HT1", 0xE8BA3A,
            "LT1", 0xD5B355,
            "HT2", 0xC4D3E7,
            "LT2", 0xA0A7B2,
            "HT3", 0xF89F5A,
            "LT3", 0xC67B42,
            "HT4", 0x81749A,
            "LT4", 0x655B79,
            "HT5", 0x8F82A8,
            "LT5", 0x655B79
    );

    private static final Map<String, Integer> REGIONS = Map.of(
            "NA", 0xFF6A6E,
            "EU", 0x6AFF6E,
            "SA", 0xFF9900,
            "AU", 0xF6B26B,
            "OC", 0xF6B26B,
            "ME", 0xFFD966,
            "AS", 0xC27BA0,
            "AF", 0x674EA7
    );

    public static int of(Ranking ranking) {
        if (ranking.retired()) return RETIRED;
        return TIERS.getOrDefault(ranking.label(), 0xD3D3D3);
    }

    public static int region(String region) {
        if (region == null) return 0xFFFFFF;
        return REGIONS.getOrDefault(region.toUpperCase(Locale.ROOT), 0xFFFFFF);
    }

    public static int globalRank(int rank) {
        return switch (rank) {
            case 1 -> 0xE5BA43;
            case 2 -> 0xB8C4D4;
            case 3 -> 0xB56326;
            default -> 0xAAAAAA;
        };
    }

    private TierColors() {
    }
}
