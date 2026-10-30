package dev.tiertags.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.tiertags.data.TierCache;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.UUID;

/** Adds the two best tiers around the player's name (used by the nametag renderer). */
@Mixin(Player.class)
public abstract class MixinPlayer {
    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
    private Component tiertags$decorateName(Component original) {
        UUID uuid = ((Player) (Object) this).getUUID();
        if (!TierCache.isRealPlayer(uuid)) return original;
        return TierCache.get(uuid).decorate(original);
    }
}
