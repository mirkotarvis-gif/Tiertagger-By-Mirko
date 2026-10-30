package dev.tiertags.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Everything we know about one player across the three tierlists. */
public final class PlayerTiers {
    public enum Status {LOADING, FOUND, NOT_FOUND, ERROR}

    public record Result(Status status, @Nullable Profile profile, long fetchedAt) {
    }

    private final Map<TierList, Result> results = new EnumMap<>(TierList.class);
    private final AtomicInteger version = new AtomicInteger();

    // Cached nametag parts, rebuilt when the data changes
    private int builtVersion = -1;
    private Component left = null;
    private Component right = null;

    public PlayerTiers() {
        for (TierList list : TierList.values()) {
            results.put(list, new Result(Status.LOADING, null, 0));
        }
    }

    public synchronized Result get(TierList list) {
        return results.get(list);
    }

    synchronized void set(TierList list, Result result) {
        results.put(list, result);
        version.incrementAndGet();
    }

    public int version() {
        return version.get();
    }

    /** The two best rankings across all tierlists (one per gamemode icon). */
    public synchronized List<Ranking> bestTwo() {
        List<Ranking> all = new ArrayList<>();
        for (Result r : results.values()) {
            if (r.status() == Status.FOUND && r.profile() != null) all.addAll(r.profile().rankings());
        }
        all.sort(Ranking.BEST_FIRST);

        List<Ranking> best = new ArrayList<>(2);
        Set<Character> seenIcons = new HashSet<>();
        for (Ranking r : all) {
            // MCTiers and PvPTiers share gamemodes; don't show "HT1 sword ... HT1 sword"
            if (!seenIcons.add(Modes.get(r.mode()).icon())) continue;
            best.add(r);
            if (best.size() == 2) break;
        }
        return best;
    }

    /**
     * Decorates a nametag: {@code HT1 [icon] Name LT1 [icon]}.
     */
    public Component decorate(Component name) {
        synchronized (this) {
            int v = version.get();
            if (v != builtVersion) {
                List<Ranking> best = bestTwo();
                left = best.isEmpty() ? null : tag(best.get(0));
                right = best.size() < 2 ? null : tag(best.get(1));
                builtVersion = v;
            }
        }

        if (left == null) return name;

        MutableComponent out = Component.empty().append(left).append(" ").append(name);
        if (right != null) out.append(" ").append(right);
        return out;
    }

    private static Component tag(Ranking r) {
        return Component.empty()
                .append(Component.literal(r.label()).withStyle(s -> s.withColor(r.color()).withBold(false)))
                .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                .append(Modes.icon(r.mode()));
    }
}
