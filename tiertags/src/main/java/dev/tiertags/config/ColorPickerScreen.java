package dev.tiertags.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pick one color with sliders, a hex code or a preset. Changes apply live. */
public class ColorPickerScreen extends Screen {
    private static final int[] PRESETS = {
            0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55,
            0x00AA00, 0x55FFFF, 0x00AAAA, 0x5555FF, 0x0000AA, 0xAA00AA, 0xFF55FF, 0xE8BA3A
    };

    private record Swatch(int x, int y, int size, int color) {
    }

    @Nullable
    private final Screen parent;
    private final String label;
    private final String sample;
    private final Supplier<String> get;
    private final Consumer<String> set;
    private final String defaultHex;

    private int rgb;
    private boolean syncing = false;
    private final List<Channel> sliders = new ArrayList<>();
    private final List<Swatch> swatches = new ArrayList<>();
    private EditBox hexBox;

    public ColorPickerScreen(@Nullable Screen parent, String label, String sample, Supplier<String> get, Consumer<String> set, String defaultHex) {
        super(Component.literal("Color: " + label));
        this.parent = parent;
        this.label = label;
        this.sample = sample;
        this.get = get;
        this.set = set;
        this.defaultHex = defaultHex;
        int c = TierTagsConfig.parseColor(get.get());
        this.rgb = c < 0 ? Math.max(0, TierTagsConfig.parseColor(defaultHex)) : c;
    }

    @Override
    protected void init() {
        sliders.clear();
        swatches.clear();
        int cx = this.width / 2;

        // Sliders on the right
        String[] names = {"Red", "Green", "Blue"};
        int[] shifts = {16, 8, 0};
        for (int i = 0; i < 3; i++) {
            Channel s = new Channel(cx + 5, 40 + i * 24, 150, 20, names[i], shifts[i], ((rgb >> shifts[i]) & 0xFF) / 255.0);
            sliders.add(s);
            this.addRenderableWidget(s);
        }

        // Hex box under the preview
        hexBox = new EditBox(this.font, cx - 155, 112, 150, 20, Component.literal("Hex color"));
        hexBox.setMaxLength(7);
        hexBox.setValue(hex(rgb));
        hexBox.setResponder(text -> {
            if (syncing) return;
            int c = TierTagsConfig.parseColor(text);
            if (c >= 0) apply(c, false);
        });
        this.addRenderableWidget(hexBox);

        this.addRenderableWidget(Button.builder(Component.literal("Random"), b -> apply((int) (Math.random() * 0xFFFFFF), true))
                .bounds(cx + 5, 112, 150, 20)
                .build());

        // Presets: 2 rows of 8
        int size = 18;
        int gap = 2;
        int rowW = 8 * size + 7 * gap;
        for (int i = 0; i < PRESETS.length; i++) {
            int color = PRESETS[i];
            int x = cx - rowW / 2 + (i % 8) * (size + gap);
            int y = 140 + (i / 8) * (size + gap);
            this.addRenderableWidget(Button.builder(Component.empty(), b -> apply(color, true))
                    .bounds(x, y, size, size)
                    .tooltip(Tooltip.create(Component.literal(hex(color))))
                    .build());
            swatches.add(new Swatch(x, y, size, color));
        }

        this.addRenderableWidget(Button.builder(Component.literal("Default"), b -> {
            int c = TierTagsConfig.parseColor(defaultHex);
            if (c >= 0) apply(c, true);
        }).bounds(cx - 155, this.height - 27, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(cx + 5, this.height - 27, 150, 20).build());
    }

    /** Sets the color, saves it to the config and updates the other widgets. */
    private void apply(int color, boolean updateHexBox) {
        rgb = color & 0xFFFFFF;
        set.accept(hex(rgb));
        TierTagsConfig.changed();
        syncing = true;
        for (Channel s : sliders) s.sync();
        if (updateHexBox && hexBox != null) hexBox.setValue(hex(rgb));
        syncing = false;
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFFFF);

        // Big preview: the color as a block, and as text on a dark background
        int x0 = cx - 155;
        graphics.fill(x0 - 1, 39, x0 + 151, 105, 0xFF555555);
        graphics.fill(x0, 40, x0 + 75, 104, 0xFF000000 | rgb);
        graphics.fill(x0 + 75, 40, x0 + 150, 104, 0xFF1A1A1A);
        graphics.drawCenteredString(this.font, Component.literal(sample).withStyle(s -> s.withColor(rgb)), x0 + 112, 62, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(label).withStyle(s -> s.withColor(rgb)), x0 + 112, 76, 0xFFFFFFFF);

        for (Swatch s : swatches) {
            graphics.fill(s.x() + 3, s.y() + 3, s.x() + s.size() - 3, s.y() + s.size() - 3, 0xFF000000 | s.color());
        }
    }

    @Override
    public void onClose() {
        TierTagsConfig.save();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** One RGB channel slider (0-255). */
    private class Channel extends AbstractSliderButton {
        private final String name;
        private final int shift;

        Channel(int x, int y, int w, int h, String name, int shift, double start) {
            super(x, y, w, h, Component.empty(), start);
            this.name = name;
            this.shift = shift;
            updateMessage();
        }

        int channel() {
            return (int) Math.round(this.value * 255);
        }

        void sync() {
            this.value = ((rgb >> shift) & 0xFF) / 255.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(name + ": " + channel()));
        }

        @Override
        protected void applyValue() {
            if (syncing) return;
            int c = (rgb & ~(0xFF << shift)) | (channel() << shift);
            apply(c, true);
        }
    }
}
