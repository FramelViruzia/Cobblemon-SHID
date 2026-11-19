package dev.cobblemonshid;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Settings screen, opened from Mod Menu. Changes are kept in a working copy and only applied on
 * Save & Quit. When the screen is wide enough a live preview of the card sits on the right;
 * otherwise "Show Preview" pops the card up in the middle of the screen.
 */
final class ShidConfigScreen extends Screen {

    private static final int WIDGET_W = 120;
    private static final int RESET_W = 40;
    private static final int BUTTON_H = 20;
    private static final int GAP = 4;
    private static final int LABEL_GAP = 12;
    private static final int PANEL_MIN_W = 150;
    private static final int TOP = 32;
    private static final int ROWS = 7;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A0A0;
    private static final int LINE = 0x80FFFFFF;

    /** Preview Pokemon: base 20 Atk hyper-trained down to 18, so both shapes and the legend show. */
    private static final String SAMPLE_NAME = "Lugia";
    private static final IvSnapshot SAMPLE = new IvSnapshot(
        new int[]{21, 20, 10, 22, 4, 22},
        new int[]{21, 18, 10, 22, 4, 22});

    private final Screen parent;
    /** Settings as they were when the screen opened, to tell whether anything changed. */
    private final ShidConfig original;
    private final ShidConfig working;
    private final IvHudRenderer previewRenderer = new IvHudRenderer();

    /** Not saved: decided the first time the screen is laid out (on when the side panel fits). */
    private Boolean showPreview;

    // Layout, set in init()
    private boolean sidePanel;
    private float maxScale;
    private int labelX;
    private int rowH;
    private int footerY;
    private int dividerX;
    private Component[] labels;

    private CycleButton<ShidConfig.HudStyle> styleButton;
    private CycleButton<Boolean> positionButton;
    private StepSlider sizeSlider;
    private StepSlider fontSlider;
    private StepSlider opacitySlider;
    private Button resetStyle;
    private Button resetPosition;
    private Button resetSize;
    private Button resetFont;
    private Button resetOpacity;
    private Button saveButton;

    ShidConfigScreen(Screen parent) {
        super(Component.translatable("cobblemon_shid.config.title"));
        this.parent = parent;
        this.original = ShidConfig.get().copy();
        this.working = original.copy();
    }

    @Override
    protected void init() {
        maxScale = ShidConfig.maxScaleForGui(minecraft.getWindow().getGuiScale());

        labels = new Component[]{
            Component.translatable("cobblemon_shid.config.show_hud"),
            Component.translatable("cobblemon_shid.config.hud_style"),
            Component.translatable("cobblemon_shid.config.hud_position"),
            Component.translatable("cobblemon_shid.config.hud_size"),
            Component.translatable("cobblemon_shid.config.font_size"),
            Component.translatable("cobblemon_shid.config.background_opacity"),
            Component.translatable("cobblemon_shid.config.show_preview")
        };
        int labelW = 0;
        for (Component label : labels) labelW = Math.max(labelW, font.width(label));
        int leftW = labelW + LABEL_GAP + WIDGET_W + GAP + RESET_W;

        sidePanel = width >= 16 + leftW + 16 + 1 + 16 + PANEL_MIN_W + 8;
        if (showPreview == null) showPreview = sidePanel;

        labelX = sidePanel ? 16 : (width - leftW) / 2;
        dividerX = labelX + leftW + 16;
        footerY = height - 28;
        rowH = Math.max(BUTTON_H + 2, Math.min(24, (footerY - 8 - TOP) / ROWS));
        int widgetX = labelX + labelW + LABEL_GAP;
        int resetX = widgetX + WIDGET_W + GAP;

        // Show HUD
        addRenderableWidget(CycleButton.booleanBuilder(yes(), no())
            .displayOnlyValue()
            .withInitialValue(working.enabled)
            .create(widgetX, rowY(0), WIDGET_W, BUTTON_H, labels[0], (b, v) -> working.enabled = v));

        // HUD Style
        styleButton = addRenderableWidget(CycleButton.<ShidConfig.HudStyle>builder(
                s -> Component.translatable("cobblemon_shid.config.hud_style." + s.name().toLowerCase(Locale.ROOT)))
            .withValues(ShidConfig.HudStyle.values())
            .withInitialValue(working.hudStyle)
            .displayOnlyValue()
            .create(widgetX, rowY(1), WIDGET_W, BUTTON_H, labels[1], (b, v) -> working.hudStyle = v));
        resetStyle = addRenderableWidget(resetButton(resetX, rowY(1), () -> {
            working.hudStyle = ShidConfig.DEFAULT_STYLE;
            styleButton.setValue(ShidConfig.DEFAULT_STYLE);
        }));

        // HUD Position: Locked / Draggable. Reset puts the card back at its default spot, locked.
        positionButton = addRenderableWidget(CycleButton.booleanBuilder(
                Component.translatable("cobblemon_shid.config.hud_position.locked"),
                Component.translatable("cobblemon_shid.config.hud_position.draggable"))
            .displayOnlyValue()
            .withInitialValue(working.lockPosition)
            .create(widgetX, rowY(2), WIDGET_W, BUTTON_H, labels[2], (b, v) -> working.lockPosition = v));
        resetPosition = addRenderableWidget(resetButton(resetX, rowY(2), () -> {
            working.offsetX = 0;
            working.offsetY = 0;
            working.lockPosition = true;
            positionButton.setValue(true);
        }));

        // HUD Size: 0.5x up to the GUI-scale limit. A saved value above the limit is shown at the
        // limit but only overwritten if the player moves the slider.
        sizeSlider = addRenderableWidget(new StepSlider(widgetX, rowY(3), WIDGET_W, BUTTON_H,
            ShidConfig.MIN_SCALE, maxScale, ShidConfig.STEP, Math.min(working.scale, maxScale),
            ShidConfigScreen::multiplier, v -> working.scale = (float) v));
        resetSize = addRenderableWidget(resetButton(resetX, rowY(3), () -> {
            working.scale = ShidConfig.DEFAULT_SCALE;
            sizeSlider.setExact(ShidConfig.DEFAULT_SCALE);
        }));

        // Font Size
        fontSlider = addRenderableWidget(new StepSlider(widgetX, rowY(4), WIDGET_W, BUTTON_H,
            ShidConfig.MIN_FONT, ShidConfig.MAX_FONT, ShidConfig.STEP, working.fontSize,
            ShidConfigScreen::multiplier, v -> working.fontSize = (float) v));
        resetFont = addRenderableWidget(resetButton(resetX, rowY(4), () -> {
            working.fontSize = ShidConfig.DEFAULT_FONT;
            fontSlider.setExact(ShidConfig.DEFAULT_FONT);
        }));

        // Background Opacity: Transparent, 1% .. 100%
        opacitySlider = addRenderableWidget(new StepSlider(widgetX, rowY(5), WIDGET_W, BUTTON_H,
            0, 100, 1, working.backgroundOpacity,
            v -> v < 0.5
                ? Component.translatable("cobblemon_shid.config.background_opacity.transparent")
                : Component.literal(Math.round(v) + "%"),
            v -> working.backgroundOpacity = (int) Math.round(v)));
        resetOpacity = addRenderableWidget(resetButton(resetX, rowY(5), () -> {
            working.backgroundOpacity = ShidConfig.DEFAULT_OPACITY;
            opacitySlider.setExact(ShidConfig.DEFAULT_OPACITY);
        }));

        // Show Preview (side panel when it fits, otherwise a pop-up in the middle)
        addRenderableWidget(CycleButton.booleanBuilder(yes(), no())
            .displayOnlyValue()
            .withInitialValue(showPreview)
            .create(widgetX, rowY(6), WIDGET_W, BUTTON_H, labels[6], (b, v) -> showPreview = v));

        // Footer
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> closeNow())
            .bounds(width / 2 - 154, footerY, 150, BUTTON_H).build());
        saveButton = addRenderableWidget(Button.builder(
                Component.translatable("cobblemon_shid.config.save_quit"), b -> saveAndClose())
            .bounds(width / 2 + 4, footerY, 150, BUTTON_H).build());
    }

    private int rowY(int row) {
        return TOP + row * rowH;
    }

    private static Component yes() {
        return CommonComponents.GUI_YES.copy().withStyle(ChatFormatting.GREEN);
    }

    private static Component no() {
        return CommonComponents.GUI_NO.copy().withStyle(ChatFormatting.RED);
    }

    private Button resetButton(int x, int y, Runnable action) {
        return Button.builder(Component.translatable("cobblemon_shid.config.reset"), b -> action.run())
            .bounds(x, y, RESET_W, BUTTON_H).build();
    }

    /** 1.0 -> "1.0x", 1.05 -> "1.05x", 1.1 -> "1.1x". */
    private static Component multiplier(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return Component.literal(s + "x");
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Reset buttons are only active when the value differs from its default
        resetStyle.active = working.hudStyle != ShidConfig.DEFAULT_STYLE;
        resetPosition.active = !working.lockPosition || working.offsetX != 0 || working.offsetY != 0;
        resetSize.active = Math.abs(working.scale - ShidConfig.DEFAULT_SCALE) > 0.001f;
        resetFont.active = Math.abs(working.fontSize - ShidConfig.DEFAULT_FONT) > 0.001f;
        resetOpacity.active = working.backgroundOpacity != ShidConfig.DEFAULT_OPACITY;
        saveButton.active = !working.sameSettings(original);

        super.render(g, mouseX, mouseY, delta);

        g.drawCenteredString(font, title, width / 2, 12, TEXT_WHITE);
        g.fill(0, TOP - 7, width, TOP - 6, LINE);
        g.fill(0, footerY - 7, width, footerY - 6, LINE);

        for (int i = 0; i < labels.length; i++) {
            g.drawString(font, labels[i], labelX, rowY(i) + (BUTTON_H - 8) / 2, TEXT_WHITE);
        }

        if (sidePanel) {
            g.fill(dividerX, TOP, dividerX + 1, footerY - 13, LINE);
            if (showPreview) drawSidePreview(g);
        } else if (showPreview) {
            drawPopupPreview(g);
        }
    }

    /** Card in the right-hand panel, shrunk if it doesn't fit; the real size is noted below. */
    private void drawSidePreview(GuiGraphics g) {
        int left = dividerX + 1 + 8;
        int right = width - 8;
        int top = TOP;
        int bottom = footerY - 13;
        int centerX = (left + right) / 2;

        g.drawCenteredString(font, Component.translatable("cobblemon_shid.config.preview_title"), centerX, top, TEXT_WHITE);

        float[] size = previewRenderer.cardSize(working);
        float scale = Math.min(working.scale, maxScale);
        int noteH = 12;
        float availW = right - left;
        float availH = bottom - (top + 14) - noteH;
        float fit = Math.min(scale, Math.min(availW / size[0], availH / size[1]));
        float cardW = size[0] * fit;
        float cardH = size[1] * fit;
        float x = centerX - cardW / 2f;
        float y = top + 14 + (availH - cardH) / 2f;
        previewRenderer.drawPreview(g, font, working, SAMPLE_NAME, SAMPLE, x, y, fit, 100f);

        if (fit < scale - 0.001f) {
            g.drawCenteredString(font,
                Component.translatable("cobblemon_shid.config.preview_actual", multiplier(scale)),
                centerX, Math.round(y + cardH + 4), TEXT_DIM);
        }
    }

    /** Narrow screens: the card at its real size (if it fits) against the right edge, over the options. */
    private void drawPopupPreview(GuiGraphics g) {
        float[] size = previewRenderer.cardSize(working);
        float scale = Math.min(working.scale, maxScale);
        float fit = Math.min(scale, Math.min((width - 16) / size[0], (height - 16) / size[1]));
        float x = width - 8 - size[0] * fit;
        float y = (height - size[1] * fit) / 2f;
        previewRenderer.drawPreview(g, font, working, SAMPLE_NAME, SAMPLE, x, y, fit, 300f);
    }

    // ------------------------------------------------------------------ closing

    private void saveAndClose() {
        // Start from the live settings so options not on this screen (trigger items) are kept
        ShidConfig next = ShidConfig.get().copy();
        next.enabled = working.enabled;
        next.hudStyle = working.hudStyle;
        next.lockPosition = working.lockPosition;
        next.offsetX = working.offsetX;
        next.offsetY = working.offsetY;
        next.scale = working.scale;
        next.fontSize = working.fontSize;
        next.backgroundOpacity = working.backgroundOpacity;
        ShidConfig.replace(next);
        closeNow();
    }

    /** Esc: ask before throwing away unsaved changes. */
    @Override
    public void onClose() {
        if (working.sameSettings(original)) {
            closeNow();
            return;
        }
        minecraft.setScreen(new ConfirmScreen(
            quit -> {
                if (quit) closeNow();
                else minecraft.setScreen(this);
            },
            Component.translatable("cobblemon_shid.config.unsaved_title"),
            Component.translatable("cobblemon_shid.config.unsaved_message"),
            Component.translatable("cobblemon_shid.config.unsaved_quit"),
            Component.translatable("cobblemon_shid.config.unsaved_keep")));
    }

    private void closeNow() {
        minecraft.setScreen(parent);
    }
}
