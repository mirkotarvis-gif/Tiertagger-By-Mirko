package dev.tiertags.data;

import dev.tiertags.config.TierTagsConfig;
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

/** Everything we know about one player across the tierlists. */
public final class PlayerTiers {
    public enum Status {LOADING, FOUND, NOT_FOUND, ERROR}

    public record Result(Status status, @Nullable Profile profile, long fetchedAt) {
    }

    /** A ranking plus the list it came from. */
    public record Tagged(Ranking ranking, TierList list) {
    }

    private final Map<TierList, Result> results = new EnumMap<>(TierList.class);
    private final AtomicInteger version = new AtomicInteger();

    /** Player name, needed for name-based lists (GoatTierList) and cracked-server lookups. */
    volatile String name = null;
    /** The settings preview shows its fake tiers even if MCTiers is turned off. */
    private boolean ignoreSources = false;

    // Cached nametag parts, rebuilt when the data or the config changes
    private int builtVersion = -1;
    private int builtRevision = -1;
    private Component left = null;
    private Component right = null;

    public PlayerTiers() {
        for (TierList list : TierList.values()) {
            results.put(list, new Result(Status.LOADING, null, 0));
        }
    }

    /** Fake player for the settings preview: HT1 Sword + LT2 Mace on MCTiers, region EU. */
    public static PlayerTiers preview() {
        PlayerTiers t = new PlayerTiers();
        t.ignoreSources = true;
        Profile p = new Profile("Steve", "EU", 105, 1, List.of(
                new Ranking("sword", 1, 0, null, null, 0L, false),
                new Ranking("mace", 2, 1, null, null, 0L, false),
                new Ranking("axe", 3, 0, 2, 0, 0L, true)
        ));
        for (TierList list : TierList.values()) {
            t.results.put(list, list == TierList.MCTIERS
                    ? new Result(Status.FOUND, p, Long.MAX_VALUE)
                    : new Result(Status.NOT_FOUND, null, Long.MAX_VALUE));
        }
        return t;
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

    /** Best rankings from the enabled lists, filtered by the config. One per gamemode icon. */
    public synchronized List<Tagged> best(int max) {
        TierTagsConfig cfg = TierTagsConfig.get();
        List<Tagged> all = new ArrayList<>();
        for (Map.Entry<TierList, Result> e : results.entrySet()) {
            Result r = e.getValue();
            if (!(ignoreSources || e.getKey().enabled()) || r.status() != Status.FOUND || r.profile() == null) continue;
            for (Ranking ranking : r.profile().rankings()) {
                if (ranking.retired() && !cfg.showRetired) continue;
                if (!cfg.mode.isEmpty() && !cfg.mode.equalsIgnoreCase(ranking.mode())) continue;
                all.add(new Tagged(ranking, e.getKey()));
            }
        }
        all.sort((a, b) -> Ranking.BEST_FIRST.compare(a.ranking(), b.ranking()));

        List<Tagged> best = new ArrayList<>(max);
        Set<Character> seenIcons = new HashSet<>();
        for (Tagged t : all) {
            // Lists share gamemodes; don't show "HT1 sword ... HT1 sword"
            if (!seenIcons.add(Modes.get(t.ranking().mode()).icon())) continue;
            best.add(t);
            if (best.size() == max) break;
        }
        return best;
    }

    /** First known region (UUID lists have it, GoatTierList doesn't). */
    @Nullable
    public synchronized String region() {
        for (Map.Entry<TierList, Result> e : results.entrySet()) {
            Result r = e.getValue();
            if (!(ignoreSources || e.getKey().enabled()) || r.status() != Status.FOUND || r.profile() == null) continue;
            String region = r.profile().region();
            if (region != null && !region.isBlank() && !region.equalsIgnoreCase("Unknown")) return region;
        }
        return null;
    }

    /** Decorates a name: {@code HT1 [icon] Name LT2 [icon] EU}, depending on the config. */
    public Component decorate(Component name) {
        Component l, r;
        synchronized (this) {
            int v = version.get();
            int rev = TierTagsConfig.revision();
            if (v != builtVersion || rev != builtRevision) {
                build();
                builtVersion = v;
                builtRevision = rev;
            }
            l = left;
            r = right;
        }

        if (l == null && r == null) return name;
        MutableComponent out = Component.empty();
        if (l != null) out.append(l).append(" ");
        out.append(name);
        if (r != null) out.append(" ").append(r);
        return out;
    }

    private void build() {
        TierTagsConfig cfg = TierTagsConfig.get();
        List<Tagged> best = best(cfg.tagCount);
        String region = cfg.region == TierTagsConfig.RegionPos.OFF ? null : region();

        MutableComponent l = best.isEmpty() ? null : tag(best.get(0), cfg);
        MutableComponent r = best.size() < 2 ? null : tag(best.get(1), cfg);

        if (region != null) {
            Component reg = Component.literal(region).withStyle(s -> s.withColor(cfg.regionColor(region)).withBold(cfg.bold));
            if (cfg.region == TierTagsConfig.RegionPos.LEFT) {
                l = l == null ? Component.empty().append(reg) : Component.empty().append(reg).append(" ").append(l);
            } else {
                r = r == null ? Component.empty().append(reg) : r.append(" ").append(reg);
            }
        }
        left = l;
        right = r;
    }

    private static MutableComponent tag(Tagged t, TierTagsConfig cfg) {
        Ranking r = t.ranking();
        int bracket = cfg.bracketColor();
        MutableComponent out = Component.empty();
        if (!cfg.brackets.open.isEmpty()) {
            out.append(Component.literal(cfg.brackets.open).withStyle(s -> s.withColor(bracket).withBold(false)));
        }
        if (cfg.listLogo) {
            out.append(t.list().logo());
            out.append(Component.literal(" ").withStyle(st -> st.withBold(false)));
        }
        if (cfg.sourceLetter) {
            out.append(Component.literal(t.list().letter()).withStyle(s -> s.withColor(t.list().color()).withBold(cfg.bold)));
        }
        out.append(Component.literal(r.label()).withStyle(s -> s.withColor(r.color()).withBold(cfg.bold)));
        if (cfg.icons) {
            out.append(Component.literal(" ").withStyle(s -> s.withBold(false)));
            out.append(Modes.icon(r.mode()));
        }
        if (!cfg.brackets.close.isEmpty()) {
            out.append(Component.literal(cfg.brackets.close).withStyle(s -> s.withColor(bracket).withBold(false)));
        }
        return out;
    }
}
