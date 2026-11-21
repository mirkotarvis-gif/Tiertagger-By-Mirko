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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * goattierlist.zenvex.cloud has no per-player API: {@code /api/tiers} returns every mode with the
 * names in each tier, e.g. {@code {"modes":{"SWORD":{"HT1":[{"name":"x","note":""}], ...}}}}.
 * We download it once, index it by lowercase name and refresh it every few minutes.
 */
public final class GoatTierList {
    public static final String URL = "https://goattierlist.zenvex.cloud/api/tiers";
    private static final long REFRESH_MS = 5 * 60 * 1000L;
    private static final long RETRY_MS = 60 * 1000L;
    private static final Pattern TIER = Pattern.compile("([HL])T([1-5])");

    private static Map<String, Profile> players = null;
    private static long fetchedAt = 0;

    /** Blocking. Returns the cached list, downloading it first if it's missing or old. */
    public static synchronized Map<String, Profile> data(HttpClient http, String userAgent) throws Exception {
        long now = System.currentTimeMillis();
        if (players != null && now - fetchedAt < REFRESH_MS) return players;

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
            players = parse(res.body());
            fetchedAt = now;
            return players;
        } catch (Exception e) {
            if (players != null) {
                // keep showing the old data, try again in a minute
                fetchedAt = now - REFRESH_MS + RETRY_MS;
                return players;
            }
            throw e;
        }
    }

    static Map<String, Profile> parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("modes")) return Map.of();
        JsonObject modes = root.getAsJsonObject().getAsJsonObject("modes");

        Map<String, String> names = new HashMap<>();             // lowercase -> name as written
        Map<String, List<Ranking>> rankings = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> modeEntry : modes.entrySet()) {
            if (!modeEntry.getValue().isJsonObject()) continue;
            String rawMode = modeEntry.getKey().toLowerCase(Locale.ROOT);
            String mode = Modes.normalize(rawMode);

            for (Map.Entry<String, JsonElement> tierEntry : modeEntry.getValue().getAsJsonObject().entrySet()) {
                Matcher m = TIER.matcher(tierEntry.getKey().toUpperCase(Locale.ROOT));
                if (!m.matches() || !tierEntry.getValue().isJsonArray()) continue; // skips "LTM" etc.
                int pos = m.group(1).equals("H") ? Ranking.HIGH : Ranking.LOW;
                int tier = Integer.parseInt(m.group(2));

                for (JsonElement p : tierEntry.getValue().getAsJsonArray()) {
                    String name = p.isJsonObject() && p.getAsJsonObject().has("name")
                            ? p.getAsJsonObject().get("name").getAsString()
                            : p.isJsonPrimitive() ? p.getAsString() : null;
                    if (name == null || name.isBlank()) continue;
                    String key = name.trim().toLowerCase(Locale.ROOT);
                    names.putIfAbsent(key, name.trim());
                    List<Ranking> list = rankings.computeIfAbsent(key, k -> new ArrayList<>());
                    // the same player in two tiers of one mode: keep the better one
                    Ranking r = new Ranking(mode, tier, pos, null, null, 0L, false);
                    list.removeIf(old -> old.mode().equals(mode) && old.order() > Ranking.order(tier, pos));
                    if (list.stream().noneMatch(old -> old.mode().equals(mode))) list.add(r);
                }
            }
        }

        // Points like the website, then the overall leaderboard position
        List<Map.Entry<String, Integer>> byPoints = new ArrayList<>();
        for (Map.Entry<String, List<Ranking>> e : rankings.entrySet()) {
            byPoints.add(Map.entry(e.getKey(), e.getValue().stream().mapToInt(Ranking::points).sum()));
        }
        byPoints.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Map<String, Profile> out = new HashMap<>();
        for (int i = 0; i < byPoints.size(); i++) {
            String key = byPoints.get(i).getKey();
            List<Ranking> list = rankings.get(key);
            list.sort(Ranking.BEST_FIRST);
            out.put(key, new Profile(names.get(key), "Unknown", byPoints.get(i).getValue(), i + 1, List.copyOf(list)));
        }
        return out;
    }

    private GoatTierList() {
    }
}
