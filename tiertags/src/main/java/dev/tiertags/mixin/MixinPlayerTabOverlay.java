package dev.tiertags.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.tiertags.config.TierTagsConfig;
import dev.tiertags.data.TierCache;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Adds tiers to names in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class MixinPlayerTabOverlay {
    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component tiertags$decorateTabName(Component original, PlayerInfo info) {
        if (!TierTagsConfig.get().tablist) return original;
        return TierCache.decorate(info.getProfile().id(), info.getProfile().name(), original);
    }
}
