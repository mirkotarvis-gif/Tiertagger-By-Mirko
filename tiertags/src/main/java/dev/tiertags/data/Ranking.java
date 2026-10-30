package dev.tiertags.data;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * One gamemode ranking. {@code pos} is 0 for High and 1 for Low.
 */
public record Ranking(String mode, int tier, int pos, @Nullable Integer peakTier, @Nullable Integer peakPos,
                      long attained, boolean retired) {

    /** Retired players are shown with their peak tier (e.g. RLT1), like the websites do. */
    public int shownTier() {
        return retired && peakTier != null ? peakTier : tier;
    }

    public int shownPos() {
        return retired && peakPos != null ? peakPos : pos;
    }

    /** e.g. "HT1", "LT3", "RLT2". */
    public String label() {
        return (retired ? "R" : "") + (shownPos() == 0 ? "H" : "L") + "T" + shownTier();
    }

    public boolean hasHigherPeak() {
        if (retired || peakTier == null || peakPos == null) return false;
        return peakTier * 2 + peakPos < tier * 2 + pos;
    }

    public String peakLabel() {
        if (peakTier == null || peakPos == null) return label();
        return (peakPos == 0 ? "H" : "L") + "T" + peakTier;
    }

    /** Points each tier is worth on the tierlist websites. */
    public int points() {
        int t = shownTier();
        boolean high = shownPos() == 0;
        return switch (t) {
            case 1 -> high ? 60 : 45;
            case 2 -> high ? 30 : 20;
            case 3 -> high ? 10 : 6;
            case 4 -> high ? 4 : 3;
            case 5 -> high ? 2 : 1;
            default -> 0;
        };
    }

    public int color() {
        return TierColors.of(this);
    }

    /** Active rankings first, then best tier first (HT1 > LT1 > HT2 ...). */
    public static final Comparator<Ranking> BEST_FIRST = Comparator
            .comparing(Ranking::retired)
            .thenComparingInt(r -> r.shownTier() * 2 + r.shownPos());
}
