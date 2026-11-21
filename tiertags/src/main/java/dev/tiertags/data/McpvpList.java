package dev.tiertags.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * mcpvp.com has no per-player tier API, only the leaderboard at {@code /tiers/data}
 * (100 players per page, every player has a rank for each kit). We page through the whole
 * leaderboard, index it by UUID and refresh it every 10 minutes.
 */
public final class McpvpList {
    public static final String BASE = "https://www.mcpvp.com/tiers/data?version=beta&kit=overall&include_retired=1&limit=100&offset=";
    private static final long REFRESH_MS = 10 * 60 * 1000L;
    private static final long RETRY_MS = 60 * 1000L;
    private static final int MAX_PAGES = 100; // 10 000 players, far more than the site has

    private static Map<UUID, Profile> players = null;
    private static long fetchedAt = 0;

    /** Blocking. Returns the cached leaderboard, downloading it first if it's missing or old. */
    public static synchronized Map<UUID, Profile> data(HttpClient http, String userAgent) throws Exception {
        long now = System.currentTimeMillis();
        if (players != null && now - fetchedAt < REFRESH_MS) return players;

        try {
            Map<UUID, Profile> out = new HashMap<>();
            int offset = 0;
            for (int page = 0; page < MAX_PAGES; page++) {
                HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + offset))
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());

                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                int added = parsePage(root, out);
                boolean more = root.has("hasMore") && root.get("hasMore").getAsBoolean();
                if (!more || added == 0) break;
                offset = root.has("nextOffset") && !root.get("nextOffset").isJsonNull()
                        ? root.get("nextOffset").getAsInt()
                        : offset + added;
            }
            players = out;
            fetchedAt = now;
            return players;
        } catch (Exception e) {
            if (players != null) {
                fetchedAt = now - REFRESH_MS + RETRY_MS; // keep the old data, retry in a minute
                return players;
            }
            throw e;
        }
    }

    /** Adds one leaderboard page to {@code out}, returns how many players it had. */
    static int parsePage(JsonObject root, Map<UUID, Profile> out) {
        if (!root.has("players") || !root.get("players").isJsonArray()) return 0;
        int count = 0;
        for (JsonElement el : root.getAsJsonArray("players")) {
            if (!el.isJsonObject()) continue;
            count++;
            JsonObject p = el.getAsJsonObject();
            UUID uuid = parseUuid(str(p, "uuid"));
            if (uuid == null) continue;

            JsonObject ranks = obj(p, "kitRanks");
            JsonObject peaks = obj(p, "kitPeakRanks");
            JsonObject retired = obj(p, "kitRetired");
            List<Ranking> rankings = new ArrayList<>();
            if (ranks != null) {
                for (Map.Entry<String, JsonElement> e : ranks.entrySet()) {
                    if (!e.getValue().isJsonPrimitive()) continue;
                    int[] now = Ranking.parseLabel(e.getValue().getAsString()); // "???" = untested, skipped
                    if (now == null) continue;
                    int[] peak = peaks != null && peaks.has(e.getKey()) && peaks.get(e.getKey()).isJsonPrimitive()
                            ? Ranking.parseLabel(peaks.get(e.getKey()).getAsString()) : null;
                    boolean ret = retired != null && retired.has(e.getKey()) && retired.get(e.getKey()).isJsonPrimitive()
                            && retired.get(e.getKey()).getAsBoolean();
                    rankings.add(new Ranking(Modes.normalize(e.getKey()), now[0], now[1],
                            peak == null ? null : peak[0], peak == null ? null : peak[1], 0L, ret));
                }
            }
            rankings.sort(Ranking.BEST_FIRST);

            int points = p.has("points") && p.get("points").isJsonPrimitive() ? (int) Math.round(p.get("points").getAsDouble()) : 0;
            int rank = p.has("rank") && p.get("rank").isJsonPrimitive() ? p.get("rank").getAsInt() : 0;
            String region = str(p, "region");
            out.put(uuid, new Profile(str(p, "name") == null ? "?" : str(p, "name"),
                    region == null ? "Unknown" : region, points, rank, List.copyOf(rankings)));
        }
        return count;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static JsonObject obj(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    static UUID parseUuid(String s) {
        if (s == null) return null;
        try {
            if (s.contains("-")) return UUID.fromString(s);
            if (s.length() != 32) return null;
            return new UUID(Long.parseUnsignedLong(s.substring(0, 16), 16), Long.parseUnsignedLong(s.substring(16), 16));
        } catch (Exception e) {
            return null;
        }
    }

    private McpvpList() {
    }
}
