package dev.tiertags.screen;

import com.mojang.authlib.GameProfile;
import dev.tiertags.TierTags;
import dev.tiertags.data.Modes;
import dev.tiertags.data.PlayerTiers;
import dev.tiertags.data.Profile;
import dev.tiertags.data.Ranking;
import dev.tiertags.data.TierCache;
import dev.tiertags.data.TierColors;
import dev.tiertags.data.TierList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.Services;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * /tiers &lt;player&gt;: skin on the left, profile on the right, with a tab for each tierlist.
 */
public class TiersScreen extends Screen {
    private static final int LINE = 11;
    private static final int INFO_HEIGHT = 56; // Region, Points, Global rank, "Rankings:" (+ spacing)
    private static final int TOP = 56;         // below the title and tab buttons
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;

    private final String playerName;
    private volatile UUID uuid;
    private volatile String lookupError = null;
    private volatile PlayerSkinWidget loadedSkin = null;

    private PlayerSkinWidget skin = null;
    private TierList tab = TierList.MCTIERS;
    private boolean started = false;
    private int shownVersion = -1;

    public TiersScreen(String playerName, @Nullable UUID uuid) {
        super(Component.literal(playerName + "'s tiers"));
        this.playerName = playerName;
        this.uuid = uuid;
    }

    private void startLoading() {
        Minecraft mc = Minecraft.getInstance();

        if (uuid == null) {
            TierCache.lookupUuid(playerName).whenComplete((result, error) -> {
                if (error != null) {
                    lookupError = "Couldn't look up " + playerName + " (Mojang API error)";
                } else if (result.isEmpty()) {
                    lookupError = "No Minecraft account called " + playerName;
                } else {
                    uuid = result.get();
                }
            });
        }

        Services services = mc.services();
        CompletableFuture.runAsync(() -> {
            GameProfile profile = services.profileResolver().fetchByName(playerName)
                    .orElseGet(() -> new GameProfile(uuid != null ? uuid : UUID.randomUUID(), playerName));
            Supplier<PlayerSkin> skinLookup = mc.getSkinManager().createLookup(profile, true);
            loadedSkin = new PlayerSkinWidget(60, 144, mc.getEntityModels(), skinLookup);
        }).exceptionally(t -> {
            TierTags.LOGGER.warn("Couldn't load skin for {}: {}", playerName, t.toString());
            return null;
        });
    }

    @Override
    protected void init() {
        if (!started) {
            started = true;
            startLoading();
        }

        // Tierlist tabs
        int tabWidth = 80;
        int gap = 4;
        int tabsX = this.width / 2 - (TierList.values().length * (tabWidth + gap) - gap) / 2;
        for (TierList list : TierList.values()) {
            Button button = Button.builder(Component.literal(list.displayName()), b -> {
                        this.tab = list;
                        this.rebuildWidgets();
                    })
                    .bounds(tabsX, 28, tabWidth, 20)
                    .build();
            button.active = list != this.tab; // the selected tab is greyed out
            this.addRenderableWidget(button);
            tabsX += tabWidth + gap;
        }

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 27, 200, 20)
                .build());

        if (this.skin != null) {
            this.skin.setPosition(this.width / 2 - 65, Math.max(TOP, (this.height - 144) / 2));
            this.addRenderableWidget(this.skin);
        }

        // Ranking lines are widgets so they can show a tooltip on hover
        Profile profile = currentProfile();
        if (profile != null) {
            int y = contentY(profile) + INFO_HEIGHT;
            for (Ranking ranking : profile.rankings()) {
                StringWidget line = new StringWidget(rankingLine(ranking), this.font);
                line.setX(this.width / 2 + 5);
                line.setY(y);
                line.setTooltip(Tooltip.create(rankingTooltip(ranking)));
                this.addRenderableWidget(line);
                y += LINE;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.skin == null && this.loadedSkin != null) {
            this.skin = this.loadedSkin;
            this.rebuildWidgets();
        }

        UUID id = this.uuid;
        if (id != null) {
            int version = TierCache.get(id).version();
            if (version != this.shownVersion) {
                this.shownVersion = version;
                this.rebuildWidgets();
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.playerName + "'s profile", this.width / 2, 12, WHITE);

        int x = this.width / 2 + 5;
        int midY = Math.max(TOP, this.height / 2 - 5);

        if (this.lookupError != null) {
            graphics.drawString(this.font, Component.literal(this.lookupError).withStyle(ChatFormatting.RED), x, midY, WHITE);
            return;
        }

        UUID id = this.uuid;
        if (id == null) {
            graphics.drawString(this.font, "Looking up player...", x, midY, GRAY);
            return;
        }

        PlayerTiers.Result result = TierCache.get(id).get(this.tab);
        switch (result.status()) {
            case LOADING -> graphics.drawString(this.font, "Loading " + this.tab.displayName() + "...", x, midY, GRAY);
            case NOT_FOUND -> graphics.drawString(this.font, "Not ranked on " + this.tab.displayName(), x, midY, GRAY);
            case ERROR -> graphics.drawString(this.font,
                    Component.literal("Couldn't reach " + this.tab.displayName() + ", retrying soon").withStyle(ChatFormatting.RED), x, midY, WHITE);
            case FOUND -> {
                Profile profile = result.profile();
                if (profile == null) return;
                int y = contentY(profile);
                graphics.drawString(this.font, regionText(profile), x, y, WHITE);
                graphics.drawString(this.font, pointsText(profile), x, y + 15, WHITE);
                graphics.drawString(this.font, rankText(profile), x, y + 30, WHITE);
                graphics.drawString(this.font, profile.rankings().isEmpty() ? "Rankings: none" : "Rankings:", x, y + 45, WHITE);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- helpers ----

    @Nullable
    private Profile currentProfile() {
        UUID id = this.uuid;
        if (id == null) return null;
        PlayerTiers.Result result = TierCache.get(id).get(this.tab);
        return result.status() == PlayerTiers.Status.FOUND ? result.profile() : null;
    }

    /** Top of the info block, vertically centered like the reference layout. */
    private int contentY(Profile profile) {
        int total = INFO_HEIGHT + profile.rankings().size() * LINE;
        return Math.max(TOP, (this.height - total) / 2);
    }

    private static Component regionText(Profile p) {
        return Component.literal("Region: ")
                .append(Component.literal(p.region()).withStyle(s -> s.withColor(TierColors.region(p.region()))));
    }

    private static Component pointsText(Profile p) {
        Profile.Title title = p.title();
        return Component.literal("Points: ")
                .append(Component.literal(p.points() + " ").withStyle(s -> s.withColor(title.color)))
                .append(Component.literal("(" + title.text + ")").withStyle(s -> s.withColor(title.accent)));
    }

    private static Component rankText(Profile p) {
        String rank = p.overall() > 0 ? "#" + p.overall() : "-";
        return Component.literal("Global rank: ")
                .append(Component.literal(rank).withStyle(s -> s.withColor(TierColors.globalRank(p.overall()))));
    }

    /** e.g. "[icon] Vanilla: HT1" */
    private static Component rankingLine(Ranking r) {
        Modes.Mode mode = Modes.get(r.mode());
        return Component.empty()
                .append(Modes.icon(r.mode()))
                .append(Component.literal(" " + mode.title()).withStyle(s -> s.withColor(mode.color())))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(r.label()).withStyle(s -> s.withColor(r.color())));
    }

    private static Component rankingTooltip(Ranking r) {
        StringBuilder text = new StringBuilder();
        if (r.attained() > 0) {
            String date = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(r.attained()));
            text.append("Attained: ").append(date).append('\n');
        }
        text.append("Points: ").append(r.points());
        if (r.hasHigherPeak()) text.append("\nPeak: ").append(r.peakLabel());
        if (r.retired()) text.append("\nRetired");

        MutableComponent c = Component.literal(text.toString());
        return c.withStyle(ChatFormatting.GRAY);
    }
}
