package dev.tiertags.config;

import dev.tiertags.data.Modes;
import dev.tiertags.data.PlayerTiers;
import dev.tiertags.data.TierCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Settings screen, opened from Mod Menu or with /tiertags. Plain vanilla widgets, no extra library needed. */
public class TierTagsConfigScreen extends Screen {
    private enum Page {
        GENERAL("General"), SOURCES("Sources"), DISPLAY("Display"), TIER_COLORS("Tier colors"), REGION_COLORS("Regions");
        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private record ColorField(int x, int y, String label, Supplier<String> value) {
    }

    private static final int TOP = 56;
    private static final int ROW = 22;
    private static final int COL_W = 150;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;

    private static Page lastPage = Page.GENERAL;

    @Nullable
    private final Screen parent;
    private Page page = lastPage;
    private final List<ColorField> colorFields = new ArrayList<>();
    private final PlayerTiers preview = PlayerTiers.preview();

    public TierTagsConfigScreen(@Nullable Screen parent) {
        super(Component.literal("TierTags Settings"));
        this.parent = parent;
    }

    private static TierTagsConfig cfg() {
        return TierTagsConfig.get();
    }

    private int colX(int col) {
        return this.width / 2 - COL_W - 5 + col * (COL_W + 10);
    }

    private static int rowY(int row) {
        return TOP + row * ROW;
    }

    @Override
    protected void init() {
        colorFields.clear();

        // Page tabs
        Page[] pages = Page.values();
        int gap = 4;
        int tabW = Math.min(80, (this.width - 20) / pages.length - gap);
        int x = this.width / 2 - (pages.length * (tabW + gap) - gap) / 2;
        for (Page p : pages) {
            Button tab = Button.builder(Component.literal(p.title), b -> {
                this.page = p;
                lastPage = p;
                this.rebuildWidgets();
            }).bounds(x, 28, tabW, 20).build();
            tab.active = p != this.page;
            this.addRenderableWidget(tab);
            x += tabW + gap;
        }

        switch (page) {
            case GENERAL -> {
                toggle(0, 0, "Enabled", () -> cfg().enabled, v -> cfg().enabled = v, null);
                toggle(1, 0, "Nametags", () -> cfg().nametags, v -> cfg().nametags = v, null);
                toggle(0, 1, "Tab list", () -> cfg().tablist, v -> cfg().tablist = v, null);
                toggle(1, 1, "Server nametags", () -> cfg().serverNametags, v -> cfg().serverNametags = v,
                        "Adds tiers to custom nametags that servers draw with text displays");
                toggle(0, 2, "Cracked lookup", () -> cfg().crackedLookup, v -> cfg().crackedLookup = v,
                        "On offline-mode (cracked) servers, look up the real account by name to find its tiers");
                this.addRenderableWidget(Button.builder(Component.literal("Refresh all tiers"), b -> TierCache.clear())
                        .bounds(colX(1), rowY(2), COL_W, 20)
                        .tooltip(Tooltip.create(Component.literal("Forget cached tiers and download them again")))
                        .build());
            }
            case SOURCES -> {
                toggle(0, 0, "MCTiers", () -> cfg().mctiers, v -> cfg().mctiers = v, "mctiers.net");
                toggle(1, 0, "PvPTiers", () -> cfg().pvptiers, v -> cfg().pvptiers = v, "pvptiers.com");
                toggle(0, 1, "SubTiers", () -> cfg().subtiers, v -> cfg().subtiers = v, "subtiers.net");
                toggle(1, 1, "GoatTierList", () -> cfg().goattierlist, v -> cfg().goattierlist = v,
                        "goattierlist.zenvex.cloud (matched by player name)");
            }
            case DISPLAY -> {
                cycle(0, 0, "Tags", () -> String.valueOf(cfg().tagCount),
                        () -> cfg().tagCount = cfg().tagCount == 1 ? 2 : 1, "How many tiers to show around the name");
                cycle(1, 0, "Gamemode", () -> cfg().mode.isEmpty() ? "Best" : Modes.get(cfg().mode).title(),
                        this::nextMode, "Always show one gamemode instead of the best ones");
                toggle(0, 1, "Icons", () -> cfg().icons, v -> cfg().icons = v, null);
                cycle(1, 1, "Region", () -> cfg().region.label, () -> cfg().region = next(cfg().region),
                        "Show the player's region (NA, EU, ...) next to their tiers");
                cycle(0, 2, "Brackets", () -> cfg().brackets.label, () -> cfg().brackets = next(cfg().brackets), null);
                toggle(1, 2, "Bold", () -> cfg().bold, v -> cfg().bold = v, null);
                toggle(0, 3, "Retired tiers", () -> cfg().showRetired, v -> cfg().showRetired = v, null);
                toggle(1, 3, "Source letter", () -> cfg().sourceLetter, v -> cfg().sourceLetter = v,
                        "Prefix tiers with the list they came from: M, P, S or G");
            }
            case TIER_COLORS -> {
                String[] keys = TierTagsConfig.TIER_KEYS;
                for (int i = 0; i < keys.length; i++) {
                    String key = keys[i];
                    String label = key.equals("RETIRED") ? "Retired" : key;
                    colorField(i % 2, i / 2, label, () -> cfg().tierColors.get(key), v -> cfg().tierColors.put(key, v));
                }
                int i = keys.length;
                colorField(i % 2, i / 2, "Brackets", () -> cfg().bracketColor, v -> cfg().bracketColor = v);
            }
            case REGION_COLORS -> {
                String[] keys = TierTagsConfig.REGION_KEYS;
                for (int i = 0; i < keys.length; i++) {
                    String key = keys[i];
                    colorField(i % 2, i / 2, key, () -> cfg().regionColors.get(key), v -> cfg().regionColors.put(key, v));
                }
            }
        }

        boolean colors = page == Page.TIER_COLORS || page == Page.REGION_COLORS;
        if (colors) {
            this.addRenderableWidget(Button.builder(Component.literal("Reset colors"), b -> {
                if (page == Page.TIER_COLORS) cfg().resetTierColors();
                else cfg().resetRegionColors();
                TierTagsConfig.changed();
                this.rebuildWidgets();
            }).bounds(this.width / 2 - 155, this.height - 27, 150, 20).build());
            this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                    .bounds(this.width / 2 + 5, this.height - 27, 150, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                    .bounds(this.width / 2 - 100, this.height - 27, 200, 20).build());
        }
    }

    // ---- widget helpers ----

    private void toggle(int col, int row, String label, BooleanSupplier get, Consumer<Boolean> set, @Nullable String tooltip) {
        Button.Builder b = Button.builder(toggleText(label, get.getAsBoolean()), btn -> {
            set.accept(!get.getAsBoolean());
            TierTagsConfig.changed();
            btn.setMessage(toggleText(label, get.getAsBoolean()));
        }).bounds(colX(col), rowY(row), COL_W, 20);
        if (tooltip != null) b.tooltip(Tooltip.create(Component.literal(tooltip)));
        this.addRenderableWidget(b.build());
    }

    private void cycle(int col, int row, String label, Supplier<String> value, Runnable advance, @Nullable String tooltip) {
        Button.Builder b = Button.builder(cycleText(label, value.get()), btn -> {
            advance.run();
            TierTagsConfig.changed();
            btn.setMessage(cycleText(label, value.get()));
        }).bounds(colX(col), rowY(row), COL_W, 20);
        if (tooltip != null) b.tooltip(Tooltip.create(Component.literal(tooltip)));
        this.addRenderableWidget(b.build());
    }

    private void colorField(int col, int row, String label, Supplier<String> get, Consumer<String> set) {
        int x = colX(col);
        int y = rowY(row);
        EditBox box = new EditBox(this.font, x + 74, y, COL_W - 74, 20, Component.literal(label + " color"));
        box.setMaxLength(7);
        box.setValue(get.get() == null ? "" : get.get());
        box.setResponder(text -> {
            if (TierTagsConfig.parseColor(text) >= 0) {
                String hex = text.trim().toUpperCase(Locale.ROOT);
                set.accept(hex.startsWith("#") ? hex : "#" + hex);
                TierTagsConfig.changed();
            }
        });
        this.addRenderableWidget(box);
        colorFields.add(new ColorField(x, y, label, get));
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": ")
                .append(on ? Component.literal("ON").withStyle(ChatFormatting.GREEN) : Component.literal("OFF").withStyle(ChatFormatting.RED));
    }

    private static Component cycleText(String label, String value) {
        return Component.literal(label + ": ").append(Component.literal(value).withStyle(ChatFormatting.YELLOW));
    }

    private void nextMode() {
        List<String> ids = Modes.IDS;
        String cur = cfg().mode;
        if (cur.isEmpty()) {
            cfg().mode = ids.get(0);
        } else {
            int i = ids.indexOf(cur.toLowerCase(Locale.ROOT));
            cfg().mode = i < 0 || i + 1 >= ids.size() ? "" : ids.get(i + 1);
        }
    }

    private static <E extends Enum<E>> E next(E value) {
        E[] all = value.getDeclaringClass().getEnumConstants();
        return all[(value.ordinal() + 1) % all.length];
    }

    // ---- rendering ----

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, WHITE);

        for (ColorField f : colorFields) {
            graphics.drawString(this.font, f.label(), f.x(), f.y() + 6, WHITE);
            int color = TierTagsConfig.parseColor(f.value().get());
            int sx = f.x() + 56;
            int sy = f.y() + 3;
            graphics.fill(sx - 1, sy - 1, sx + 15, sy + 15, GRAY);
            graphics.fill(sx, sy, sx + 14, sy + 14, color < 0 ? 0xFF000000 : 0xFF000000 | color);
        }

        // Live preview of a nametag with the current settings
        MutableComponent name = Component.literal("Steve").withStyle(ChatFormatting.WHITE);
        Component sample = Component.literal("Preview: ").withStyle(ChatFormatting.GRAY).append(this.preview.decorate(name));
        graphics.drawCenteredString(this.font, sample, this.width / 2, this.height - 44, WHITE);
    }

    @Override
    public void onClose() {
        TierTagsConfig.save();
        TierTagsConfig.changed();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
