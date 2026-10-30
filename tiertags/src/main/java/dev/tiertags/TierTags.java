package dev.tiertags;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.tiertags.screen.TiersScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TierTags implements ClientModInitializer {
    public static final String MOD_ID = "tiertags";
    public static final Logger LOGGER = LoggerFactory.getLogger("TierTags");

    /** Screens opened from a chat command must wait a tick, otherwise closing the chat closes them too. */
    private static volatile Screen pendingScreen = null;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("tiers")
                        .then(argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ctx.getSource().getOnlinePlayerNames(), builder))
                                .executes(ctx -> {
                                    openProfile(StringArgumentType.getString(ctx, "player"));
                                    return 1;
                                }))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Screen screen = pendingScreen;
            if (screen != null) {
                pendingScreen = null;
                client.setScreen(screen);
            }
        });

        LOGGER.info("TierTags loaded");
    }

    private static void openProfile(String name) {
        Minecraft mc = Minecraft.getInstance();
        UUID uuid = null;
        String shownName = name;

        // If the player is nearby we already know their UUID (and probably their tiers)
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (player.getScoreboardName().equalsIgnoreCase(name)) {
                    uuid = player.getUUID();
                    shownName = player.getScoreboardName();
                    break;
                }
            }
        }

        pendingScreen = new TiersScreen(shownName, uuid);
    }
}
