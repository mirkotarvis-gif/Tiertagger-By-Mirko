package dev.tiertags.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A player's profile on one tierlist. All three sites use the same JSON shape. */
public record Profile(String name, String region, int points, int overall, List<Ranking> rankings) {

    public enum Title {
        COMBAT_GRANDMASTER("Combat Grandmaster", 0xE6C622, 0xFDE047),
        COMBAT_MASTER("Combat Master", 0xFBB03B, 0xFFD13A),
        COMBAT_ACE("Combat Ace", 0xCD285C, 0xD65474),
        COMBAT_SPECIALIST("Combat Specialist", 0xAD78D8, 0xC7A3E8),
        COMBAT_CADET("Combat Cadet", 0x9291D9, 0xADACE2),
        COMBAT_NOVICE("Combat Novice", 0x9291D9, 0xFFFFFF),
        ROOKIE("Rookie", 0x6C7178, 0x8B979C),
        UNRANKED("Unranked", 0xFFFFFF, 0xFFFFFF);

        public final String text;
        public final int color;
        public final int accent;

        Title(String text, int color, int accent) {
            this.text = text;
            this.color = color;
            this.accent = accent;
        }
    }

    public Title title() {
        if (points >= 400) return Title.COMBAT_GRANDMASTER;
        if (points >= 250) return Title.COMBAT_MASTER;
        if (points >= 100) return Title.COMBAT_ACE;
        if (points >= 50) return Title.COMBAT_SPECIALIST;
        if (points >= 20) return Title.COMBAT_CADET;
        if (points >= 10) return Title.COMBAT_NOVICE;
        if (points >= 1) return Title.ROOKIE;
        return Title.UNRANKED;
    }

    /** @return the parsed profile, or null if the JSON isn't a ranked profile. */
    @Nullable
    public static Profile parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return null;
        JsonObject obj = root.getAsJsonObject();
        if (!obj.has("rankings") || !obj.get("rankings").isJsonObject()) return null;

        List<Ranking> rankings = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("rankings").entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject r = e.getValue().getAsJsonObject();
            if (!has(r, "tier") || !has(r, "pos")) continue;
            rankings.add(new Ranking(
                    e.getKey(),
                    r.get("tier").getAsInt(),
                    r.get("pos").getAsInt(),
                    has(r, "peak_tier") ? r.get("peak_tier").getAsInt() : null,
                    has(r, "peak_pos") ? r.get("peak_pos").getAsInt() : null,
                    has(r, "attained") ? r.get("attained").getAsLong() : 0L,
                    has(r, "retired") && r.get("retired").getAsBoolean()
            ));
        }
        rankings.sort(Ranking.BEST_FIRST);

        return new Profile(
                has(obj, "name") ? obj.get("name").getAsString() : "?",
                has(obj, "region") ? obj.get("region").getAsString() : "Unknown",
                has(obj, "points") ? obj.get("points").getAsInt() : 0,
                has(obj, "overall") ? obj.get("overall").getAsInt() : 0,
                List.copyOf(rankings)
        );
    }

    private static boolean has(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull();
    }
}
