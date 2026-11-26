package dev.tiertags.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.tiertags.TierTags;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** All the settings, saved to config/tiertags.json. Edited through Mod Menu or /tiertags. */
public final class TierTagsConfig {
    public enum RegionPos {
        OFF("Off"), LEFT("Left"), RIGHT("Right");
        public final String label;

        RegionPos(String label) {
            this.label = label;
        }
    }

    public enum Brackets {
        NONE("None", "", ""), SQUARE("[ ]", "[", "]"), ROUND("( )", "(", ")"), ANGLE("< >", "<", ">"), PIPE("| |", "|", "|");
        public final String label, open, close;

        Brackets(String label, String open, String close) {
            this.label = label;
            this.open = open;
            this.close = close;
        }
    }

    public static final String[] TIER_KEYS = {
            "HT1", "MT1", "LT1", "HT2", "MT2", "LT2", "HT3", "MT3", "LT3",
            "HT4", "MT4", "LT4", "HT5", "MT5", "LT5", "T6", "RETIRED"};
    public static final String[] REGION_KEYS = {"NA", "EU", "SA", "AS", "AU", "OC", "ME", "AF"};

    public static final Map<String, String> DEFAULT_TIER_COLORS = new LinkedHashMap<>();
    public static final Map<String, String> DEFAULT_REGION_COLORS = new LinkedHashMap<>();

    static {
        DEFAULT_TIER_COLORS.put("HT1", "#E8BA3A");
        DEFAULT_TIER_COLORS.put("MT1", "#DEB648");
        DEFAULT_TIER_COLORS.put("LT1", "#D5B355");
        DEFAULT_TIER_COLORS.put("HT2", "#C4D3E7");
        DEFAULT_TIER_COLORS.put("MT2", "#B2BDCC");
        DEFAULT_TIER_COLORS.put("LT2", "#A0A7B2");
        DEFAULT_TIER_COLORS.put("HT3", "#F89F5A");
        DEFAULT_TIER_COLORS.put("MT3", "#DF8D4E");
        DEFAULT_TIER_COLORS.put("LT3", "#C67B42");
        DEFAULT_TIER_COLORS.put("HT4", "#81749A");
        DEFAULT_TIER_COLORS.put("MT4", "#73688A");
        DEFAULT_TIER_COLORS.put("LT4", "#655B79");
        DEFAULT_TIER_COLORS.put("HT5", "#8F82A8");
        DEFAULT_TIER_COLORS.put("MT5", "#7A6E90");
        DEFAULT_TIER_COLORS.put("LT5", "#655B79");
        DEFAULT_TIER_COLORS.put("T6", "#5A5A5A");
        DEFAULT_TIER_COLORS.put("RETIRED", "#A2D6FF");

        DEFAULT_REGION_COLORS.put("NA", "#FF6A6E");
        DEFAULT_REGION_COLORS.put("EU", "#6AFF6E");
        DEFAULT_REGION_COLORS.put("SA", "#FF9900");
        DEFAULT_REGION_COLORS.put("AS", "#C27BA0");
        DEFAULT_REGION_COLORS.put("AU", "#F6B26B");
        DEFAULT_REGION_COLORS.put("OC", "#F6B26B");
        DEFAULT_REGION_COLORS.put("ME", "#FFD966");
        DEFAULT_REGION_COLORS.put("AF", "#674EA7");
    }

    // ---- General ----
    public boolean enabled = true;
    public boolean nametags = true;
    public boolean tablist = true;
    /** Servers that draw nametags with text display entities riding the player. */
    public boolean serverNametags = true;
    /** Look up the real account of players on cracked / offline-mode servers by their name. */
    public boolean crackedLookup = true;

    // ---- Sources ----
    public boolean mctiers = true;
    public boolean pvptiers = true;
    public boolean subtiers = true;
    public boolean goattierlist = true;
    public boolean mcpvp = true;
    public boolean pvphq = true;

    // ---- Display ----
    /** 1 or 2 tags around the name. */
    public int tagCount = 2;
    /** "" = best gamemode, otherwise only show this gamemode id. */
    public String mode = "";
    public boolean icons = true;
    public RegionPos region = RegionPos.RIGHT;
    public Brackets brackets = Brackets.NONE;
    public boolean bold = false;
    public boolean showRetired = true;
    /** Prefix tiers with a short source letter, e.g. "M" for MCTiers. */
    public boolean sourceLetter = false;
    /** Show the logo of the tierlist the tier came from, next to the tier. */
    public boolean listLogo = false;

    // ---- Colors ----
    public Map<String, String> tierColors = new LinkedHashMap<>(DEFAULT_TIER_COLORS);
    public Map<String, String> regionColors = new LinkedHashMap<>(DEFAULT_REGION_COLORS);
    public String bracketColor = "#AAAAAA";

    // ---- storage ----
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static TierTagsConfig instance;
    private static volatile int revision = 0;

    public static TierTagsConfig get() {
        if (instance == null) load();
        return instance;
    }

    /** Bumped whenever a setting changes, so cached nametags get rebuilt. */
    public static int revision() {
        return revision;
    }

    public static void changed() {
        revision++;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("tiertags.json");
    }

    public static synchronized void load() {
        TierTagsConfig cfg = null;
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, TierTagsConfig.class);
            } catch (Exception e) {
                TierTags.LOGGER.warn("Couldn't read {}, using defaults: {}", p, e.toString());
            }
        }
        if (cfg == null) cfg = new TierTagsConfig();
        cfg.fixup();
        instance = cfg;
        changed();
        save();
    }

    public static synchronized void save() {
        if (instance == null) return;
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
                GSON.toJson(instance, w);
            }
        } catch (Exception e) {
            TierTags.LOGGER.warn("Couldn't save TierTags config: {}", e.toString());
        }
    }

    /** Fills anything missing from an old or hand-edited file. */
    private void fixup() {
        if (tierColors == null) tierColors = new LinkedHashMap<>();
        if (regionColors == null) regionColors = new LinkedHashMap<>();
        DEFAULT_TIER_COLORS.forEach(tierColors::putIfAbsent);
        DEFAULT_REGION_COLORS.forEach(regionColors::putIfAbsent);
        if (mode == null) mode = "";
        if (region == null) region = RegionPos.RIGHT;
        if (brackets == null) brackets = Brackets.NONE;
        if (bracketColor == null || parseColor(bracketColor) < 0) bracketColor = "#AAAAAA";
        if (tagCount < 1 || tagCount > 2) tagCount = 2;
    }

    public void resetTierColors() {
        tierColors = new LinkedHashMap<>(DEFAULT_TIER_COLORS);
        bracketColor = "#AAAAAA";
    }

    public static String defaultTierColor(String key) {
        return key.equals("BRACKETS") ? "#AAAAAA" : DEFAULT_TIER_COLORS.getOrDefault(key, "#FFFFFF");
    }

    public static String defaultRegionColor(String key) {
        return DEFAULT_REGION_COLORS.getOrDefault(key, "#FFFFFF");
    }

    public void resetRegionColors() {
        regionColors = new LinkedHashMap<>(DEFAULT_REGION_COLORS);
    }

    public int tierColor(String key) {
        int c = parseColor(tierColors.get(key));
        if (c < 0) c = parseColor(DEFAULT_TIER_COLORS.get(key));
        return c < 0 ? 0xD3D3D3 : c;
    }

    public int regionColor(String region) {
        if (region == null) return 0xFFFFFF;
        String key = region.toUpperCase(Locale.ROOT);
        int c = parseColor(regionColors.get(key));
        return c < 0 ? 0xFFFFFF : c;
    }

    public int bracketColor() {
        int c = parseColor(bracketColor);
        return c < 0 ? 0xAAAAAA : c;
    }

    /** "#RRGGBB" or "RRGGBB" to an int, or -1 if it isn't a valid color. */
    public static int parseColor(String s) {
        if (s == null) return -1;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return -1;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
