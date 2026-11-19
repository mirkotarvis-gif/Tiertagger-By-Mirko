package dev.tiertags.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.tiertags.config.TierTagsConfig;
import dev.tiertags.data.TierCache;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Adds tiers around the player's name (used by the vanilla nametag renderer). */
@Mixin(Player.class)
public abstract class MixinPlayer {
    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
    private Component tiertags$decorateName(Component original) {
        // Only client-side players: in singleplayer the integrated server calls this too (chat, death messages)
        if (!((Object) this instanceof AbstractClientPlayer player)) return original;
        if (!TierTagsConfig.get().nametags) return original;
        return TierCache.decorate(player.getUUID(), player.getScoreboardName(), original);
    }
}
