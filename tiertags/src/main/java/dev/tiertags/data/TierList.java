package dev.tiertags.data;

import dev.tiertags.config.TierTagsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The tierlist websites this mod reads from. */
public enum TierList {
    MCTIERS("MCTiers", "M", 0xFF5D5D, '\uea20') {
        @Override
        public String profileUrl(UUID uuid) {
            return "https://mctiers.net/api/v2/profile/" + uuid;
        }

        @Override
        public boolean enabled() {
            return TierTagsConfig.get().mctiers;
        }
    },
    PVPTIERS("PvPTiers", "P", 0x5DA9FF, '\uea21') {
        @Override
        public String profileUrl(UUID uuid) {
            return "https://pvptiers.com/api/profile/" + uuid.toString().replace("-", "");
        }

        @Override
        public boolean enabled() {
            return TierTagsConfig.get().pvptiers;
        }
    },
    SUBTIERS("SubTiers", "S", 0x7CFF5D, '\uea22') {
        @Override
        public String profileUrl(UUID uuid) {
            return "https://subtiers.net/api/v2/profile/" + uuid;
        }

        @Override
        public boolean enabled() {
            return TierTagsConfig.get().subtiers;
        }
    },
    /** mcpvp.com: whole leaderboard downloaded and matched by UUID, see {@link McpvpList}. */
    MCPVP("MCPVP", "MP", 0x55FFFF, '\uea23') {
        @Override
        public String profileUrl(UUID uuid) {
            return McpvpList.BASE + "0";
        }

        @Override
        public boolean bulk() {
            return true;
        }

        @Override
        public boolean enabled() {
            return TierTagsConfig.get().mcpvp;
        }
    },
    PVPHQ("PvPHQ", "HQ", 0xFF55FF, '\uea24') {
        @Override
        public String profileUrl(UUID uuid) {
            return PvpHq.PROFILE + uuid;
        }

        @Override
        @Nullable
        public Profile parse(String body) {
            return PvpHq.parse(body);
        }

        @Override
        public boolean enabled() {
            return TierTagsConfig.get().pvphq;
        }
    },
    /** goattierlist.zenvex.cloud: one big list keyed by player name, see {@link GoatTierList}. */
    GOATTIERLIST("GoatTierList", "G", 0xFFB347, '\uea25') {
        @Override
        public String profileUrl(UUID uuid) {
            return GoatTierList.URL;
        }

        @Override
        public boolean byName() {
            return true;
        }

        @Override
        public boolean enabled() {
            return TierTagsConfig.get().goattierlist;
        }
    };

    private final String displayName;
    private final String letter;
    private final int color;
    /** The site's logo, a glyph from assets/minecraft/font/default.json. */
    private final char logo;

    TierList(String displayName, String letter, int color, char logo) {
        this.displayName = displayName;
        this.letter = letter;
        this.color = color;
        this.logo = logo;
    }

    public abstract String profileUrl(UUID uuid);

    public abstract boolean enabled();

    /** Parses a profile response. Null means the player isn't on this list. */
    @Nullable
    public Profile parse(String body) {
        return Profile.parse(body);
    }

    /** True if this list is one big download matched by UUID instead of a per-player request. */
    public boolean bulk() {
        return false;
    }

    /** True if this list is looked up by player name instead of UUID. */
    public boolean byName() {
        return false;
    }

    public String displayName() {
        return displayName;
    }

    public String letter() {
        return letter;
    }

    public int color() {
        return color;
    }

    /** The site's logo as a component, kept white so the image isn't tinted. */
    public MutableComponent logo() {
        return Component.literal(String.valueOf(logo)).withStyle(ChatFormatting.WHITE);
    }
}
