package dev.tiertags.mixin;

import dev.tiertags.config.TierTagsConfig;
import dev.tiertags.data.TierCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Lots of servers hide the vanilla nametag and draw their own with a text display riding the player.
 * This finds the line containing the player's name and adds the tiers to it.
 */
@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class MixinTextDisplayRenderer {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Display$TextDisplay;Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;F)V",
            at = @At("RETURN"), require = 0)
    private void tiertags$addTiers(Display.TextDisplay entity, TextDisplayEntityRenderState state, float partialTick, CallbackInfo ci) {
        TierTagsConfig cfg = TierTagsConfig.get();
        if (!cfg.enabled || !cfg.serverNametags) return;
        if (state.cachedInfo == null) return;
        if (!(entity.getVehicle() instanceof Player player)) return;

        String name = player.getScoreboardName();
        List<Display.TextDisplay.CachedLine> lines = state.cachedInfo.lines();
        for (int i = 0; i < lines.size(); i++) {
            Display.TextDisplay.CachedLine line = lines.get(i);
            Component text = tiertags$toComponent(line.contents());
            if (!text.getString().contains(name)) continue;

            Component decorated = TierCache.decorate(player.getUUID(), name, text);
            if (decorated == text) return; // nothing to add

            List<Display.TextDisplay.CachedLine> newLines = new ArrayList<>(lines);
            newLines.set(i, new Display.TextDisplay.CachedLine(decorated.getVisualOrderText(), Minecraft.getInstance().font.width(decorated)));
            int width = newLines.stream().mapToInt(Display.TextDisplay.CachedLine::width).max().orElse(state.cachedInfo.width());
            state.cachedInfo = new Display.TextDisplay.CachedInfo(newLines, width);
            return;
        }
    }

    /** Turns an already-laid-out line back into a styled component. */
    @Unique
    private static Component tiertags$toComponent(FormattedCharSequence seq) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        Style[] runStyle = {Style.EMPTY};
        seq.accept((index, style, codePoint) -> {
            if (!style.equals(runStyle[0]) && !run.isEmpty()) {
                out.append(Component.literal(run.toString()).setStyle(runStyle[0]));
                run.setLength(0);
            }
            runStyle[0] = style;
            run.appendCodePoint(codePoint);
            return true;
        });
        if (!run.isEmpty()) out.append(Component.literal(run.toString()).setStyle(runStyle[0]));
        return out;
    }
}
