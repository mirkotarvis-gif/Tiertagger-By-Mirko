package dev.tiertags.data;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;
import java.util.Map;

/** Display name, icon glyph and color for every gamemode on the three tierlists. */
public final class Modes {
    public record Mode(String title, char icon, int color) {
    }

    // Icon glyphs are defined in assets/minecraft/font/default.json
    private static final Map<String, Mode> MODES = Map.ofEntries(
            // MCTiers / PvPTiers
            Map.entry("vanilla", new Mode("Vanilla", '', 0xFF55FF)),
            Map.entry("crystal", new Mode("Crystal", '', 0xFF55FF)),
            Map.entry("smp", new Mode("SMP", '', 0xECCB45)),
            Map.entry("sword", new Mode("Sword", '', 0xA4FDF0)),
            Map.entry("pot", new Mode("Pot", '', 0xFF0000)),
            Map.entry("uhc", new Mode("UHC", '', 0xFF5555)),
            Map.entry("axe", new Mode("Axe", '', 0x55FF55)),
            Map.entry("nethop", new Mode("Netherite OP", '', 0x7D4A40)),
            Map.entry("neth_pot", new Mode("Netherite Pot", '', 0x7D4A40)),
            Map.entry("mace", new Mode("Mace", '', 0xAAAAAA)),
            // SubTiers
            Map.entry("minecart", new Mode("Minecart", '', 0xAAAAAA)),
            Map.entry("dia_crystal", new Mode("Diamond Crystal", '', 0x55FFFF)),
            Map.entry("debuff", new Mode("Debuff", '', 0x555555)),
            Map.entry("elytra", new Mode("Elytra", '', 0x8D8DB1)),
            Map.entry("speed", new Mode("Speed", '', 0x43A9D1)),
            Map.entry("creeper", new Mode("Creeper", '', 0x55FF55)),
            Map.entry("manhunt", new Mode("Manhunt", '', 0xFF5555)),
            Map.entry("dia_smp", new Mode("Diamond SMP", '', 0x8C668B)),
            Map.entry("bow", new Mode("Bow", '', 0x663D10)),
            Map.entry("bed", new Mode("Bed", '', 0xFF0000)),
            Map.entry("og_vanilla", new Mode("OG Vanilla", '', 0xFFAA00)),
            Map.entry("trident", new Mode("Trident", '', 0x579B8C))
    );

    public static Mode get(String id) {
        Mode mode = MODES.get(id.toLowerCase(Locale.ROOT));
        if (mode != null) return mode;
        // Unknown gamemode (added to a site after this mod was made): show a dot and a prettified id
        String pretty = id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
        return new Mode(pretty, '•', 0xFFFFFF);
    }

    /** The icon as a component. Kept white so the bitmap isn't tinted. */
    public static MutableComponent icon(String id) {
        return Component.literal(String.valueOf(get(id).icon())).withStyle(ChatFormatting.WHITE);
    }

    private Modes() {
    }
}
