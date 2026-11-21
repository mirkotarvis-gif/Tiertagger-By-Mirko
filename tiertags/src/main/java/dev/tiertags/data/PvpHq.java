package dev.tiertags.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * pvphq.com: {@code /api/v1/players/<uuid>} returns {@code ranked: [{gametype, tier: "HT2", peakTier,
 * unranked, inactive}, ...]}. Tiers come from a rating, so "inactive" ones are shown like retired tiers.
 */
public final class PvpHq {
    public static final String PROFILE = "https://pvphq.com/api/v1/players/";

    /** Rough region from pvphq's closest server, e.g. "LOS_ANGELES" -> "NA". */
    private static final Map<String, String> REGIONS = Map.ofEntries(
            Map.entry("LOS_ANGELES", "NA"), Map.entry("DALLAS", "NA"), Map.entry("CHICAGO", "NA"),
            Map.entry("NEW_YORK", "NA"), Map.entry("MIAMI", "NA"), Map.entry("SEATTLE", "NA"),
            Map.entry("TORONTO", "NA"), Map.entry("ATLANTA", "NA"), Map.entry("VIRGINIA", "NA"),
            Map.entry("LONDON", "EU"), Map.entry("FRANKFURT", "EU"), Map.entry("AMSTERDAM", "EU"),
            Map.entry("PARIS", "EU"), Map.entry("STOCKHOLM", "EU"), Map.entry("WARSAW", "EU"),
            Map.entry("MADRID", "EU"), Map.entry("MILAN", "EU"),
            Map.entry("SAO_PAULO", "SA"), Map.entry("SANTIAGO", "SA"),
            Map.entry("SINGAPORE", "AS"), Map.entry("TOKYO", "AS"), Map.entry("MUMBAI", "AS"),
            Map.entry("HONG_KONG", "AS"), Map.entry("SEOUL", "AS"),
            Map.entry("SYDNEY", "AU"), Map.entry("DUBAI", "ME"), Map.entry("JOHANNESBURG", "AF")
    );

    /** Null if the player has no ranked gamemodes. */
    @Nullable
    public static Profile parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return null;
        JsonObject obj = root.getAsJsonObject();
        if (!obj.has("ranked") || !obj.get("ranked").isJsonArray()) return null;

        List<Ranking> rankings = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("ranked")) {
            if (!el.isJsonObject()) continue;
            JsonObject r = el.getAsJsonObject();
            if (bool(r, "unranked") || bool(r, "recalibrating")) continue;
            int[] now = Ranking.parseLabel(str(r, "tier"));
            String mode = str(r, "gametype");
            if (now == null || mode == null) continue;
            int[] peak = Ranking.parseLabel(str(r, "peakTier"));
            // pvphq's peak can be below the current tier; only keep it when it's actually better
            if (peak != null && Ranking.order(peak[0], peak[1]) > Ranking.order(now[0], now[1])) peak = now;
            long attained = r.has("lastPlayedAt") && r.get("lastPlayedAt").isJsonPrimitive()
                    ? r.get("lastPlayedAt").getAsLong() / 1000L : 0L;
            rankings.add(new Ranking(Modes.normalize(mode), now[0], now[1],
                    peak == null ? null : peak[0], peak == null ? null : peak[1], attained, bool(r, "inactive")));
        }
        if (rankings.isEmpty()) return null;
        rankings.sort(Ranking.BEST_FIRST);

        int points = rankings.stream().filter(r -> !r.retired()).mapToInt(Ranking::points).sum();
        JsonElement overall = obj.get("overall");
        if (overall != null && overall.isJsonObject() && overall.getAsJsonObject().has("points")
                && overall.getAsJsonObject().get("points").isJsonPrimitive()) {
            points = (int) Math.round(overall.getAsJsonObject().get("points").getAsDouble());
        }
        String closest = str(obj, "closestRegion");
        String region = closest == null ? "Unknown" : REGIONS.getOrDefault(closest.toUpperCase(Locale.ROOT), "Unknown");
        return new Profile(str(obj, "name") == null ? "?" : str(obj, "name"), region, points, 0, List.copyOf(rankings));
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private PvpHq() {
    }
}
