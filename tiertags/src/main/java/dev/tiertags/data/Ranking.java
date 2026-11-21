package dev.tiertags.data;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;

/**
 * One gamemode ranking. {@code tier} is 1 (best) to 6 and {@code pos} is the position inside the tier:
 * {@link #HIGH} or {@link #LOW} (what MCTiers-style APIs send), or {@link #MID} / {@link #SUB} used by
 * MCPVP and PvPHQ. Order inside a tier: HT &gt; MT &gt; LT &gt; ST.
 */
public record Ranking(String mode, int tier, int pos, @Nullable Integer peakTier, @Nullable Integer peakPos,
                      long attained, boolean retired) {
    public static final int HIGH = 0;
    public static final int LOW = 1;
    public static final int MID = 2;
    public static final int SUB = 3;

    /** Retired players are shown with their peak tier (e.g. RLT1), like the websites do. */
    public int shownTier() {
        return retired && peakTier != null ? peakTier : tier;
    }

    public int shownPos() {
        return retired && peakPos != null ? peakPos : pos;
    }

    /** e.g. "HT1", "MT2", "LT3", "RLT2". */
    public String label() {
        return (retired ? "R" : "") + letter(shownPos()) + "T" + shownTier();
    }

    /** The config color key, e.g. "HT1", "MT3" or "T6". */
    public String colorKey() {
        int t = shownTier();
        if (t >= 6) return "T6";
        int p = shownPos();
        return (p == SUB ? "L" : letter(p)) + "T" + t;
    }

    public boolean hasHigherPeak() {
        if (retired || peakTier == null || peakPos == null) return false;
        return order(peakTier, peakPos) < order(tier, pos);
    }

    public String peakLabel() {
        if (peakTier == null || peakPos == null) return label();
        return letter(peakPos) + "T" + peakTier;
    }

    /** Points each tier is worth (MCTiers values; MT sits between HT and LT). */
    public int points() {
        int t = shownTier();
        int p = shownPos();
        int[] high = {60, 30, 10, 4, 2};
        int[] mid = {52, 25, 8, 3, 1};
        int[] low = {45, 20, 6, 3, 1};
        if (t < 1 || t > 5) return 0;
        return switch (p) {
            case HIGH -> high[t - 1];
            case MID -> mid[t - 1];
            default -> low[t - 1];
        };
    }

    public int color() {
        return TierColors.of(this);
    }

    /** Lower is better: HT1, MT1, LT1, ST1, HT2, ... */
    public static int order(int tier, int pos) {
        int inTier = switch (pos) {
            case HIGH -> 0;
            case MID -> 1;
            case LOW -> 2;
            default -> 3;
        };
        return tier * 4 + inTier;
    }

    public int order() {
        return order(shownTier(), shownPos());
    }

    public static String letter(int pos) {
        return switch (pos) {
            case HIGH -> "H";
            case MID -> "M";
            case LOW -> "L";
            default -> "S";
        };
    }

    /** Parses "HT1", "MT3", "LT5", "ST6" into {tier, pos}. Null for anything else ("???", ""). */
    @Nullable
    public static int[] parseLabel(@Nullable String label) {
        if (label == null) return null;
        String s = label.trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("R") && s.length() == 4) s = s.substring(1); // "RHT1"
        if (s.length() != 3 || s.charAt(1) != 'T') return null;
        int pos = switch (s.charAt(0)) {
            case 'H' -> HIGH;
            case 'M' -> MID;
            case 'L' -> LOW;
            case 'S' -> SUB;
            default -> -1;
        };
        int tier = s.charAt(2) - '0';
        if (pos < 0 || tier < 1 || tier > 6) return null;
        return new int[]{tier, pos};
    }

    /** Active rankings first, then best tier first (HT1 > MT1 > LT1 > HT2 ...). */
    public static final Comparator<Ranking> BEST_FIRST = Comparator
            .comparing(Ranking::retired)
            .thenComparingInt(Ranking::order);
}
