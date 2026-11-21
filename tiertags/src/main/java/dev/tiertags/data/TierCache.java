package dev.tiertags.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tiertags.TierTags;
import dev.tiertags.config.TierTagsConfig;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** Fetches and caches tiers. Requests run on a small thread pool so big servers don't spam the APIs. */
public final class TierCache {
    private static final String USER_AGENT = "TierTags/1.2 (Fabric mod for Minecraft 1.21.11)";
    private static final long REFRESH_AFTER_MS = 15 * 60 * 1000L; // re-fetch found/not-found data after 15 min
    private static final long RETRY_ERROR_MS = 60 * 1000L;        // retry failed requests after 1 min
    private static final long RETRY_LOOKUP_MS = 5 * 60 * 1000L;   // retry failed Mojang lookups after 5 min
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

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

    /** Cracked / offline-mode name -> real account UUID (null uuid = no such account). */
    private record Resolved(@Nullable UUID uuid, boolean failed, long at) {
    }

    private static final Map<String, Resolved> RESOLVED = new ConcurrentHashMap<>();
    private static final Set<String> RESOLVING = ConcurrentHashMap.newKeySet();

    /**
     * Adds tiers to a name if the mod is enabled and this looks like a real player.
     * Used by the nametag, tab list and text display mixins.
     */
    public static Component decorate(UUID uuid, @Nullable String name, Component original) {
        if (original == null || uuid == null || !TierTagsConfig.get().enabled) return original;
        if (!shouldTag(uuid, name)) return original;
        return get(uuid, name).decorate(original);
    }

    /**
     * Online-mode players have v4 UUIDs. Offline-mode (cracked) servers give v3 UUIDs made from the name,
     * those still get name-based lists and, if enabled, a lookup of the real account. Other versions are NPCs.
     */
    public static boolean shouldTag(UUID uuid, @Nullable String name) {
        if (uuid.version() == 4) return true;
        return uuid.version() == 3 && name != null && VALID_NAME.matcher(name).matches();
    }

    public static PlayerTiers get(UUID uuid) {
        return get(uuid, null);
    }

    /** Returns the cached tiers for a player and starts any fetches that are missing or stale. */
    public static PlayerTiers get(UUID uuid, @Nullable String name) {
        PlayerTiers tiers = CACHE.computeIfAbsent(uuid, u -> new PlayerTiers());
        if (name != null && tiers.name == null) tiers.name = name;
        String knownName = tiers.name;
        long now = System.currentTimeMillis();

        // The UUID the UUID-based APIs know this player by. Null while a cracked lookup is still running.
        Optional<UUID> apiUuid = uuid.version() == 4 ? Optional.of(uuid) : resolveCracked(knownName);

        for (TierList list : TierList.values()) {
            if (!list.enabled()) continue;
            PlayerTiers.Result r = tiers.get(list);
            boolean stale = switch (r.status()) {
                case LOADING -> r.fetchedAt() == 0;
                case ERROR -> now - r.fetchedAt() > RETRY_ERROR_MS;
                case FOUND, NOT_FOUND -> now - r.fetchedAt() > REFRESH_AFTER_MS;
            };
            if (!stale) continue;

            if (list.byName()) {
                if (knownName != null) fetchByName(uuid, knownName, list, tiers);
            } else if (apiUuid == null) {
                // still resolving the cracked name, try again next frame
            } else if (apiUuid.isPresent() && list.bulk()) {
                fetchBulk(uuid, apiUuid.get(), list, tiers);
            } else if (apiUuid.isPresent()) {
                fetch(uuid, apiUuid.get(), list, tiers);
            } else {
                tiers.set(list, new PlayerTiers.Result(PlayerTiers.Status.NOT_FOUND, null, now));
            }
        }
        return tiers;
    }

    public static void clear() {
        CACHE.clear();
        RESOLVED.clear();
    }

    /** Null = still looking it up, empty = no real account (or lookups are disabled). */
    @Nullable
    private static Optional<UUID> resolveCracked(@Nullable String name) {
        if (!TierTagsConfig.get().crackedLookup || name == null || !VALID_NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        String key = name.toLowerCase(Locale.ROOT);
        Resolved r = RESOLVED.get(key);
        long now = System.currentTimeMillis();
        if (r != null) {
            if (!r.failed() || now - r.at() <= RETRY_LOOKUP_MS) return Optional.ofNullable(r.uuid());
            RESOLVED.remove(key); // failed a while ago, look it up again
        }

        if (RESOLVING.add(key)) {
            lookupUuid(name).whenComplete((result, error) -> {
                long at = System.currentTimeMillis();
                if (error != null) RESOLVED.put(key, new Resolved(null, true, at));
                else RESOLVED.put(key, new Resolved(result.orElse(null), false, at));
                RESOLVING.remove(key);
            });
        }
        return null;
    }

    private static void fetchByName(UUID cacheKey, String name, TierList list, PlayerTiers tiers) {
        String key = cacheKey + "/" + list;
        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) return;

        POOL.execute(() -> {
            try {
                Profile profile = GoatTierList.data(HTTP, USER_AGENT).get(name.toLowerCase(Locale.ROOT));
                PlayerTiers.Status status = profile == null ? PlayerTiers.Status.NOT_FOUND : PlayerTiers.Status.FOUND;
                tiers.set(list, new PlayerTiers.Result(status, profile, System.currentTimeMillis()));
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                TierTags.LOGGER.warn("Failed to fetch {} tiers for {}: {}", list.displayName(), name, e.toString());
                tiers.set(list, new PlayerTiers.Result(PlayerTiers.Status.ERROR, null, System.currentTimeMillis()));
            } finally {
                IN_FLIGHT.remove(key);
            }
        });
    }

    private static void fetchBulk(UUID cacheKey, UUID uuid, TierList list, PlayerTiers tiers) {
        String key = cacheKey + "/" + list;
        if (IN_FLIGHT.putIfAbsent(key, Boolean.TRUE) != null) return;

        POOL.execute(() -> {
            try {
                Profile profile = McpvpList.data(HTTP, USER_AGENT).get(uuid);
                PlayerTiers.Status status = profile == null ? PlayerTiers.Status.NOT_FOUND : PlayerTiers.Status.FOUND;
                tiers.set(list, new PlayerTiers.Result(status, profile, System.currentTimeMillis()));
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                TierTags.LOGGER.warn("Failed to fetch {} tiers for {}: {}", list.displayName(), uuid, e.toString());
                tiers.set(list, new PlayerTiers.Result(PlayerTiers.Status.ERROR, null, System.currentTimeMillis()));
            } finally {
                IN_FLIGHT.remove(key);
            }
        });
    }

    private static void fetch(UUID cacheKey, UUID uuid, TierList list, PlayerTiers tiers) {
        String key = cacheKey + "/" + list;
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
                    Profile profile = list.parse(res.body());
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

    /** Looks up a username's UUID through Mojang's API. Empty if the account doesn't exist, fails on other errors. */
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
                if (res.statusCode() == 404 || res.statusCode() == 204) return Optional.empty();
                if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());

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
