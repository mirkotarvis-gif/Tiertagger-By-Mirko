package dev.tiertags.data;

import java.util.UUID;

/** The three tierlist websites this mod reads from. */
public enum TierList {
    MCTIERS("MCTiers", 0xFF5D5D) {
        @Override
        public String profileUrl(UUID uuid) {
            return "https://mctiers.com/api/v2/profile/" + uuid;
        }
    },
    PVPTIERS("PvPTiers", 0x5DA9FF) {
        @Override
        public String profileUrl(UUID uuid) {
            return "https://pvptiers.com/api/profile/" + uuid.toString().replace("-", "");
        }
    },
    SUBTIERS("SubTiers", 0x7CFF5D) {
        @Override
        public String profileUrl(UUID uuid) {
            return "https://subtiers.net/api/v2/profile/" + uuid;
        }
    };

    private final String displayName;
    private final int color;

    TierList(String displayName, int color) {
        this.displayName = displayName;
        this.color = color;
    }

    public abstract String profileUrl(UUID uuid);

    public String displayName() {
        return displayName;
    }

    public int color() {
        return color;
    }
}
