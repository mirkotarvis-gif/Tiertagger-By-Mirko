package dev.tiertags.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tiertags.TierTags;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches and caches tiers. Requests run on a small thread pool so big servers don't spam the APIs. */
public final class TierCache {
    private static final String USER_AGENT = "TierTags/1.0 (Fabric mod for Minecraft 1.21.11)";
    private static final long REFRESH_AFTER_MS = 15 * 60 * 1000L; // re-fetch found/not-found data after 15 min
    private static final long RETRY_ERROR_MS = 60 * 1000L;        // retry failed requests after 1 min

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "TierTags-fetch");
        t.setDaemon(true);
        return t;
    });

    private static final Map<UUID, PlayerTiers> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    /** Returns the cached tiers for a player and starts any fetches that are missing or stale. */
    public static PlayerTiers get(UUID uuid) {
        PlayerTiers tiers = CACHE.computeIfAbsent(uuid, u -> new PlayerTiers());
        long now = System.currentTimeMillis();

        for (TierList list : TierList.values()) {
            PlayerTiers.Result r = tiers.get(list);
            boolean stale = switch (r.status()) {
                case LOADING -> r.fetchedAt() == 0;
                case ERROR -> now - r.fetchedAt() > RETRY_ERROR_MS;
                case FOUND, NOT_FOUND -> now - r.fetchedAt() > REFRESH_AFTER_MS;
            };
            if (stale) fetch(uuid, list, tiers);
        }
        return tiers;
    }

    /** Only real (online-mode) players have v4 UUIDs; NPCs and offline players are skipped. */
    public static boolean isRealPlayer(UUID uuid) {
        return uuid.version() == 4;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static void fetch(UUID uuid, TierList list, PlayerTiers tiers) {
        String key = uuid + "/" + list;
        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) return;

        POOL.execute(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(list.profileUrl(uuid)))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                long now = System.currentTimeMillis();

                if (res.statusCode() == 404) {
                    tiers.set(list, new PlayerTiers.Result(PlayerTiers.Status.NOT_FOUND, null, now));
                } else if (res.statusCode() == 200) {
                    Profile profile = Profile.parse(res.body());
                    PlayerTiers.Status status = profile == null ? PlayerTiers.Status.NOT_FOUND : PlayerTiers.Status.FOUND;
                    tiers.set(list, new PlayerTiers.Result(status, profile, now));
                } else {
                    TierTags.LOGGER.warn("{} returned HTTP {} for {}", list.displayName(), res.statusCode(), uuid);
                    tiers.set(list, new PlayerTiers.Result(PlayerTiers.Status.ERROR, null, now));
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                TierTags.LOGGER.warn("Failed to fetch {} tiers for {}: {}", list.displayName(), uuid, e.toString());
                tiers.set(list, new PlayerTiers.Result(PlayerTiers.Status.ERROR, null, System.currentTimeMillis()));
            } finally {
                IN_FLIGHT.remove(key);
            }
        });
    }

    /** Looks up a username's UUID through Mojang's API. Empty if the account doesn't exist. */
    public static CompletableFuture<Optional<UUID>> lookupUuid(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8);
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();
                HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) return Optional.empty();

                JsonElement root = JsonParser.parseString(res.body());
                if (!root.isJsonObject()) return Optional.empty();
                JsonObject obj = root.getAsJsonObject();
                if (!obj.has("id")) return Optional.empty();
                return Optional.of(parseUuid(obj.get("id").getAsString()));
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                TierTags.LOGGER.warn("UUID lookup failed for {}: {}", name, e.toString());
                throw new RuntimeException(e);
            }
        }); // default pool, so a lookup never waits behind nametag fetches
    }

    private static UUID parseUuid(String s) {
        if (s.contains("-")) return UUID.fromString(s);
        return new UUID(Long.parseUnsignedLong(s.substring(0, 16), 16), Long.parseUnsignedLong(s.substring(16), 16));
    }

    private TierCache() {
    }
}
