package dev.tiertags.data;

import dev.tiertags.config.TierTagsConfig;

/** Tier and region colors. Defaults match the websites; all of them can be changed in the config. */
public final class TierColors {

    public static int of(Ranking ranking) {
        TierTagsConfig cfg = TierTagsConfig.get();
        if (ranking.retired()) return cfg.tierColor("RETIRED");
        return cfg.tierColor((ranking.shownPos() == 0 ? "H" : "L") + "T" + ranking.shownTier());
    }

    public static int region(String region) {
        return TierTagsConfig.get().regionColor(region);
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
