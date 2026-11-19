package dev.cobblemonshid;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Draws the IV card. Full style: header (IVs tag, name, perfect count, average), a hexagon chart in
 * the layout of Cobblemon's summary IV tab, a colour legend and a total bar. Compact style: the
 * hexagon only, with the average in the bottom-right corner.
 *
 * Colouring rule:
 *  - Nothing changed yet: one yellow shape showing the IVs.
 *  - Something changed by a cap or candy: the original base IVs become a faint grey shape and the
 *    final IVs are drawn in translucent yellow over it.
 *
 * The same renderer draws the preview on the settings screen.
 */
final class IvHudRenderer {

    // Full card layout, in GUI pixels before HUD Size is applied
    private static final int FULL_W = 130;
    private static final int FULL_H = 166;
    private static final float FULL_CY = 73f;
    /** Compact is 3px narrower on each side. */
    private static final int COMPACT_W = FULL_W - 6;

    private static final float R = 32f;
    private static final float CORNER = 5f;
    private static final int PAD = 4;
    private static final int DIVIDER_Y = 17;
    private static final int LEGEND_Y = 130;
    private static final int FOOTER_Y = 143;
    private static final int FOOTER_H = 16;

    /**
     * Depth the card is drawn at. The vanilla HUD is drawn in layers, each pushed 200 further
     * forward, and the chat is one of the last ones (well over z 2000). The card has to be in front
     * of all of them or the chat covers it. The HUD's depth is cleared before screens are drawn, so
     * this never puts the card over a menu; the GUI depth range goes to about 10000.
     */
    private static final float CARD_Z = 5000f;

    /** Cobblemon's party overlay: 6 slots of 30px, centred on the screen, then raised by 10px. */
    private static final int PARTY_HALF_HEIGHT = 6 * 30 / 2;
    private static final int PARTY_LIFT = 10;
    private static final int PARTY_SLOT_GAP = 4;
    /** Cobblemon's party slots are 62px wide at the left edge; the default spot stays right of this. */
    private static final int PARTY_RIGHT_EDGE = 62 + 8;
    /** Default gap between the card and the crosshair. */
    private static final int CROSSHAIR_GAP = 65;
    /** How far the card's bottom edge sits below the last party slot. */
    private static final int BELOW_PARTY = 20;

    /** Base text sizes at Font Size 1.0 (the 1.1.x sizes). */
    private static final float BASE_TEXT = 0.67f;
    private static final float BASE_STAT_NAME = 0.56f;
    /** Below this Background Opacity all text gets a shadow so it stays readable. */
    private static final int SHADOW_BELOW_OPACITY = 10;

    private static final ResourceLocation UNSYNCED_ICON =
        ResourceLocation.fromNamespaceAndPath(ShidClient.MOD_ID, "textures/gui/unsynced.png");
    private static final int ICON_TEX = 16;

    // Colours (ARGB). Background parts are RGB only; their alpha comes from Background Opacity.
    private static final int PANEL_BG_RGB = 0x141920;
    private static final int PANEL_BORDER_RGB = 0x3B4552;
    private static final int DIVIDER_ALPHA = 0x30;
    private static final int PANEL_BORDER = 0xFF3B4552;
    private static final int CHART_BG = 0xB3232B36;
    private static final int CHART_EDGE = 0x66FFFFFF;
    private static final int GRID = 0x26FFFFFF;
    private static final int BASE_FILL = 0x4D9AA3AD;
    private static final int BASE_EDGE = 0x809AA3AD;
    private static final int FINAL_FILL = 0xA8FFE135;
    private static final int FINAL_EDGE = 0xFFFFD84A;
    private static final int FOOTER_BG = 0x40000000;
    private static final int BAR_TRACK = 0xFF2A313A;
    private static final int BAR_FILL = 0xFFE8EDF2;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF9AA3AD;
    private static final int TEXT_BASE = 0xFF8A9099;
    private static final int TEXT_YELLOW = 0xFFFFE14D;

    /** Stat label colours, in hexagon order: HP, Atk, Def, Spe, SpD, SpA. */
    private static final int[] STAT_COLORS = {
        0xFFFF7070, 0xFFF5AC78, 0xFFFAE078, 0xFFFA92B2, 0xFFA7DB8D, 0xFF9DB7F5
    };
    private static final String[] STAT_KEYS = {"hp", "atk", "def", "spe", "spd", "spa"};

    // ---- where the in-game card was last drawn, for dragging (GUI-scaled pixels) ----
    private static float lastX, lastY, lastW, lastH, lastDefaultX, lastDefaultY;
    private static long lastDrawNanos = 0L;

    // ---- per-draw layout, set by setup() ----
    private int w = FULL_W;
    private int h = FULL_H;
    private float cx = FULL_W / 2f;
    private float cy = FULL_CY;
    private boolean compact;
    /** Text size relative to the normal Minecraft font. */
    private float ts = BASE_TEXT;
    /** Size of the stat names around the chart. */
    private float nameScale = BASE_STAT_NAME;
    /** Background Opacity, 0..1. */
    private float bgAlpha = 0.9f;
    private boolean forceShadow;

    void render(GuiGraphics g, DeltaTracker delta) {
        try {
            renderInternal(g);
        } catch (Throwable t) {
            ShidClient.LOGGER.debug("HUD render failed", t);
        }
    }

    private void renderInternal(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        // Record originals for the whole party, whether or not the card is showing
        OriginalIvStore.tick();

        if (mc.options.hideGui) return;
        // Stay visible while chatting or moving the card; hide behind any other screen
        if (mc.screen != null && !(mc.screen instanceof ChatScreen) && !(mc.screen instanceof MoveHudScreen)) return;

        ShidConfig config = ShidConfig.get();
        if (!config.enabled) return;
        if (!TargetFinder.isHoldingTriggerItem(player)) return;

        PokemonEntity entity = TargetFinder.lookedAtEntity(mc);
        if (entity == null) return;

        TargetFinder.Target target = TargetFinder.resolve(entity, player);
        if (target == null) return;

        Pokemon pokemon = target.pokemon();
        String name = pokemon != null ? pokemon.getDisplayName(false).getString() : "";
        // Read straight from the client's party copy every frame: Cobblemon updates it as soon
        // as the server changes the IVs, so the card is always current.
        IvSnapshot snap = target.ownedButUnsynced()
            ? IvSnapshot.unknown()
            : new IvSnapshot(pokemon, OriginalIvStore.originalFor(pokemon));

        setup(config);
        float scale = effectiveScale(config);
        float[] pos = panelPosition(mc, config, w, h, scale);

        lastX = pos[0];
        lastY = pos[1];
        lastW = w * scale;
        lastH = h * scale;
        lastDefaultX = pos[2];
        lastDefaultY = pos[3];
        lastDrawNanos = System.nanoTime();

        g.pose().pushPose();
        g.pose().translate(pos[0], pos[1], CARD_Z);
        g.pose().scale(scale, scale, 1f);
        drawCard(g, mc.font, name, snap);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ public helpers

    /** HUD Size actually used: the setting, capped for the current GUI scale. */
    static float effectiveScale(ShidConfig config) {
        double gui = Minecraft.getInstance().getWindow().getGuiScale();
        return Math.min(config.scale, ShidConfig.maxScaleForGui(gui));
    }

    /** True while the in-game card is on screen. */
    static boolean isVisible() {
        return lastDrawNanos != 0L && System.nanoTime() - lastDrawNanos < 200_000_000L;
    }

    /** {x, y, width, height} of the in-game card, already scaled. */
    static float[] lastBounds() {
        return new float[]{lastX, lastY, lastW, lastH};
    }

    /** Top-left of the card with no offset, before it is kept on screen. */
    static float[] lastDefaultOrigin() {
        return new float[]{lastDefaultX, lastDefaultY};
    }

    /** Unscaled {width, height} of the card for these settings. */
    float[] cardSize(ShidConfig config) {
        setup(config);
        return new float[]{w, h};
    }

    /** Draws a card with sample data at (x, y), for the settings preview. */
    void drawPreview(GuiGraphics g, Font font, ShidConfig config, String name, IvSnapshot snap,
                     float x, float y, float scale, float z) {
        setup(config);
        g.pose().pushPose();
        g.pose().translate(x, y, z);
        g.pose().scale(scale, scale, 1f);
        drawCard(g, font, name, snap);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ layout

    private void setup(ShidConfig config) {
        ts = BASE_TEXT * config.fontSize;
        nameScale = BASE_STAT_NAME * config.fontSize;
        bgAlpha = config.backgroundOpacity / 100f;
        forceShadow = config.backgroundOpacity < SHADOW_BELOW_OPACITY;
        compact = config.hudStyle == ShidConfig.HudStyle.COMPACT;
        if (compact) {
            // Just tall enough for the HP label above the chart and the Speed label below it
            float block = labelBlockHeight();
            w = COMPACT_W;
            cx = w / 2f;
            cy = Math.round(PAD + block + 1 + R + 4);
            h = Math.round(cy + R + 4 + 1 + block + PAD);
        } else {
            w = FULL_W;
            h = FULL_H;
            cx = w / 2f;
            cy = FULL_CY;
        }
    }

    /** Height of a stat name plus its value line. */
    private float labelBlockHeight() {
        return 9 * nameScale + 9 * ts + 1;
    }

    /**
     * {x, y, defaultX, defaultY}. The default spot is left of the crosshair with the bottom edge a
     * little below the last party slot, so switching style or size keeps the bottom edge in place.
     */
    private static float[] panelPosition(Minecraft mc, ShidConfig config, int w, int h, float scale) {
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float sw = w * scale;
        float sh = h * scale;
        float defaultX = screenW / 2f - CROSSHAIR_GAP - sw;
        float partyBottom = screenH / 2f + PARTY_HALF_HEIGHT - PARTY_LIFT - PARTY_SLOT_GAP;
        float defaultY = partyBottom + BELOW_PARTY - sh;

        float x;
        float y = defaultY + config.offsetY;
        if (config.offsetX == 0 && config.offsetY == 0) {
            // Default spot: on narrow GUI-scaled screens keep clear of the party bar
            x = Math.max(defaultX, PARTY_RIGHT_EDGE);
        } else {
            // Moved by the player: they chose the spot, so it may cover the party bar
            x = defaultX + config.offsetX;
        }
        // Never let the card leave the screen
        x = Math.max(0, Math.min(x, screenW - sw));
        y = Math.max(0, Math.min(y, screenH - sh));
        return new float[]{Math.round(x), Math.round(y), defaultX, defaultY};
    }

    // ------------------------------------------------------------------ card

    private void drawCard(GuiGraphics g, Font font, String name, IvSnapshot snap) {
        Component tag = Component.translatable("cobblemon_shid.hud.title");
        int lineH = Math.round(9 * ts);
        int iconSize = lineH + 4;
        int tagW = snap.unknown ? iconSize : Math.round(width(font, tag)) + 4;

        // Never fully transparent: the background still has to be drawn (at 1/255, invisible) so it
        // writes depth and keeps the chat behind the card at every opacity. Alpha 0 is discarded
        // by the shader and would let the chat show through.
        int panelBg = withAlpha(PANEL_BG_RGB, Math.max(1, 255 * bgAlpha));
        int border = withAlpha(PANEL_BORDER_RGB, 255 * bgAlpha);
        int tagBg = withAlpha(PANEL_BORDER_RGB, 255 * bgAlpha);
        int divider = withAlpha(0xFFFFFF, DIVIDER_ALPHA * bgAlpha);

        // ---- all shapes in one pass, so text drawn afterwards sits on top ----
        g.flush();
        Matrix4f m = g.pose().last().pose();
        beginShapes();

        float[][] card = roundedRect(0, 0, w, h, CORNER);
        fillConvex(m, card, w / 2f, h / 2f, panelBg);
        outlineClosed(m, card, 1f, border);

        if (!compact) {
            if (!snap.unknown) {
                float[][] tagBox = roundedRect(PAD, PAD, tagW, lineH + 4, 2f);
                fillConvex(m, tagBox, PAD + tagW / 2f, PAD + (lineH + 4) / 2f, tagBg);
            }
            rect(m, PAD, DIVIDER_Y, w - PAD, DIVIDER_Y + 0.5f, divider);
        }

        // Chart background, grid rings and spokes (not affected by Background Opacity)
        float[][] outer = vertices(new int[]{31, 31, 31, 31, 31, 31});
        fillPolygon(m, outer, CHART_BG);
        for (int ring = 1; ring <= 2; ring++) {
            outlineClosed(m, scaled(outer, ring / 3f), 0.5f, GRID);
        }
        for (float[] v : outer) line(m, cx, cy, v[0], v[1], 0.5f, GRID);

        // IV shapes: faint grey original underneath, yellow final on top
        if (!snap.unknown) {
            float[][] fin = vertices(snap.fin);
            if (snap.anyChanged) {
                float[][] base = vertices(snap.base);
                fillPolygon(m, base, BASE_FILL);
                outlineClosed(m, base, 0.75f, BASE_EDGE);
            }
            fillPolygon(m, fin, FINAL_FILL);
            outlineClosed(m, fin, 1f, FINAL_EDGE);
        }
        outlineClosed(m, outer, 0.75f, CHART_EDGE);

        float[] legendX = null;
        if (!compact) {
            // Legend squares
            legendX = legendLayout(font, snap);
            if (snap.anyChanged) {
                legendSquare(m, legendX[0], BASE_FILL | 0xFF000000);
                legendSquare(m, legendX[1], 0xFFFFE135);
            } else {
                legendSquare(m, legendX[0], 0xFFFFE135);
            }

            // Footer box and total bar (not affected by Background Opacity)
            float[][] footer = roundedRect(PAD, FOOTER_Y, w - 2 * PAD, FOOTER_H, 3f);
            fillConvex(m, footer, w / 2f, FOOTER_Y + FOOTER_H / 2f, FOOTER_BG);
            outlineClosed(m, footer, 0.75f, PANEL_BORDER);
            float[] bar = totalBarBounds(font, snap);
            float barY = FOOTER_Y + FOOTER_H / 2f - 1.5f;
            rect(m, bar[0], barY, bar[1], barY + 3, BAR_TRACK);
            if (!snap.unknown) {
                rect(m, bar[0], barY, bar[0] + (bar[1] - bar[0]) * snap.finalTotal() / 186f, barY + 3, BAR_FILL);
            }
        }

        endShapes();

        if (snap.unknown) drawUnsyncedIcon(g, iconSize);

        // ---- text ----
        if (!compact) drawHeaderText(g, font, name, snap, tag, tagW);
        for (int i = 0; i < 6; i++) drawStatLabel(g, font, i, snap);
        if (compact) {
            drawCornerAverage(g, font, snap);
        } else {
            drawLegendText(g, font, snap, legendX);
            drawTotalText(g, font, snap);
        }
    }

    /** Red sync icon in the top-left corner: the client has no IVs for this Pokemon yet. */
    private void drawUnsyncedIcon(GuiGraphics g, int size) {
        g.pose().pushPose();
        g.pose().translate(PAD, PAD, 0f);
        g.pose().scale(size / (float) ICON_TEX, size / (float) ICON_TEX, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(UNSYNCED_ICON, 0, 0, 0f, 0f, ICON_TEX, ICON_TEX, ICON_TEX, ICON_TEX);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    private String averageText(IvSnapshot snap) {
        return snap.unknown ? "-" : String.format(Locale.ROOT, "%.1f", snap.finalAverage());
    }

    /** "[IVs] Name ...... 1/6 · AVG 25.0" */
    private void drawHeaderText(GuiGraphics g, Font font, String name, IvSnapshot snap, Component tag, int tagW) {
        float textY = PAD + 2 + 0.5f;
        if (!snap.unknown) text(g, font, tag, PAD + 2, textY, TEXT, false);

        MutableComponent right = Component.literal(snap.unknown ? "-/6" : snap.perfectCount() + "/6").withColor(TEXT)
            .append(Component.literal(" · ").withColor(TEXT_DIM))
            .append(Component.translatable("cobblemon_shid.hud.average").withColor(TEXT_DIM))
            .append(Component.literal(" " + averageText(snap)).withColor(TEXT));
        float rightW = width(font, right);
        text(g, font, right, w - PAD - rightW, textY, TEXT, false);

        float nameX = PAD + tagW + 3;
        float nameMax = w - PAD - rightW - 3 - nameX;
        int maxRaw = (int) (nameMax / ts);
        if (font.width(name) > maxRaw) {
            name = font.plainSubstrByWidth(name, Math.max(0, maxRaw - font.width("…"))) + "…";
        }
        text(g, font, Component.literal(name), nameX, textY, TEXT, false);
    }

    /** Compact style: "AVG" over the value, in the bottom-right corner. */
    private void drawCornerAverage(GuiGraphics g, Font font, IvSnapshot snap) {
        Component label = Component.translatable("cobblemon_shid.hud.average");
        Component value = Component.literal(averageText(snap));
        float lineH = 9 * ts;
        float valueY = h - PAD - lineH + 1;
        float labelY = valueY - lineH;
        text(g, font, label, w - PAD - width(font, label), labelY, TEXT_DIM, false);
        text(g, font, value, w - PAD - width(font, value), valueY, TEXT, false);
    }

    /** Stat name on one line; value below it as "base (final)" when a cap or candy changed it. */
    private void drawStatLabel(GuiGraphics g, Font font, int i, IvSnapshot snap) {
        double angle = Math.toRadians(-90 + 60 * i);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float lx = cx + (R + 4f) * cos;
        float ly = cy + (R + 4f) * sin;

        Component statName = Component.translatable("cobblemon_shid.stat." + STAT_KEYS[i]);
        MutableComponent value;
        if (snap.unknown) {
            value = Component.literal("-").withColor(TEXT_DIM);
        } else if (snap.isChanged(i)) {
            value = Component.literal(String.valueOf(snap.base[i])).withColor(TEXT_BASE)
                .append(Component.literal(" (").withColor(TEXT_BASE))
                .append(Component.literal(String.valueOf(snap.fin[i])).withColor(valueColor(snap.fin[i])))
                .append(Component.literal(")").withColor(TEXT_BASE));
        } else {
            value = Component.literal(String.valueOf(snap.base[i])).withColor(valueColor(snap.base[i]));
        }

        float nameH = 9 * nameScale;
        float block = labelBlockHeight();
        float top;
        if (i == 0) top = ly - block - 1;        // HP: above the top corner
        else if (i == 3) top = ly + 1;           // Speed: below the bottom corner
        else top = ly - block / 2f;              // sides: centred on the corner

        float nameW = font.width(statName) * nameScale;
        float valueW = width(font, value);
        float nameX;
        float valueX;
        if (cos > 0.3f) {            // right side: left-aligned
            nameX = lx;
            valueX = lx;
        } else if (cos < -0.3f) {    // left side: right-aligned
            nameX = lx - nameW;
            valueX = lx - valueW;
        } else {                     // top / bottom: centred
            nameX = lx - nameW / 2f;
            valueX = lx - valueW / 2f;
        }
        text(g, font, statName, nameX, top, STAT_COLORS[i], true, nameScale);
        text(g, font, value, valueX, top + nameH + 1, TEXT, true, ts);
    }

    private static int valueColor(int iv) {
        return iv >= IvSnapshot.MAX ? TEXT_YELLOW : TEXT;
    }

    // ---- legend: square + label pairs, centred under the chart ----

    private static final int LEGEND_SQUARE = 5;
    private static final int LEGEND_GAP = 3;
    private static final int LEGEND_SPACING = 8;

    private Component[] legendLabels(IvSnapshot snap) {
        if (snap.anyChanged) {
            return new Component[]{
                Component.translatable("cobblemon_shid.hud.legend_base"),
                Component.translatable("cobblemon_shid.hud.legend_hyper")
            };
        }
        return new Component[]{Component.translatable("cobblemon_shid.hud.legend_none")};
    }

    /** X position of each legend square. */
    private float[] legendLayout(Font font, IvSnapshot snap) {
        Component[] labels = legendLabels(snap);
        float total = 0;
        for (int i = 0; i < labels.length; i++) {
            total += LEGEND_SQUARE + LEGEND_GAP + width(font, labels[i]);
            if (i > 0) total += LEGEND_SPACING;
        }
        float[] xs = new float[labels.length];
        float x = (w - total) / 2f;
        for (int i = 0; i < labels.length; i++) {
            xs[i] = x;
            x += LEGEND_SQUARE + LEGEND_GAP + width(font, labels[i]) + LEGEND_SPACING;
        }
        return xs;
    }

    private void legendSquare(Matrix4f m, float x, int color) {
        float y = LEGEND_Y + (9 * ts - LEGEND_SQUARE) / 2f;
        rect(m, x, y, x + LEGEND_SQUARE, y + LEGEND_SQUARE, color);
    }

    private void drawLegendText(GuiGraphics g, Font font, IvSnapshot snap, float[] xs) {
        Component[] labels = legendLabels(snap);
        for (int i = 0; i < labels.length; i++) {
            text(g, font, labels[i], xs[i] + LEGEND_SQUARE + LEGEND_GAP, LEGEND_Y + 0.5f, TEXT_DIM, false);
        }
    }

    // ---- footer: "Total [=======    ] 159/186" ----

    private Component totalLabel() {
        return Component.translatable("cobblemon_shid.hud.total");
    }

    private MutableComponent totalAmount(IvSnapshot snap) {
        return Component.literal(snap.unknown ? "-" : String.valueOf(snap.finalTotal())).withColor(TEXT)
            .append(Component.literal("/186").withColor(TEXT_DIM));
    }

    private float[] totalBarBounds(Font font, IvSnapshot snap) {
        float x1 = PAD + 5 + width(font, totalLabel()) + 4;
        float x2 = w - PAD - 5 - width(font, totalAmount(snap)) - 4;
        return new float[]{x1, Math.max(x1, x2)};
    }

    private void drawTotalText(GuiGraphics g, Font font, IvSnapshot snap) {
        float y = FOOTER_Y + (FOOTER_H - 9 * ts) / 2f + 0.5f;
        text(g, font, totalLabel(), PAD + 5, y, TEXT_DIM, false);
        MutableComponent amount = totalAmount(snap);
        text(g, font, amount, w - PAD - 5 - width(font, amount), y, TEXT, false);
    }

    // ------------------------------------------------------------------ text

    private float width(Font font, Component text) {
        return font.width(text) * ts;
    }

    private void text(GuiGraphics g, Font font, Component text, float x, float y, int color, boolean shadow) {
        text(g, font, text, x, y, color, shadow, ts);
    }

    private void text(GuiGraphics g, Font font, Component text, float x, float y, int color, boolean shadow, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, color, shadow || forceShadow);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ geometry

    /** Hexagon corner for each stat, at a distance proportional to value / 31. */
    private float[][] vertices(int[] values) {
        float[][] out = new float[6][2];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(-90 + 60 * i);
            float r = R * values[i] / (float) IvSnapshot.MAX;
            out[i][0] = cx + r * (float) Math.cos(angle);
            out[i][1] = cy + r * (float) Math.sin(angle);
        }
        return out;
    }

    private float[][] scaled(float[][] pts, float f) {
        float[][] out = new float[pts.length][2];
        for (int i = 0; i < pts.length; i++) {
            out[i][0] = cx + (pts[i][0] - cx) * f;
            out[i][1] = cy + (pts[i][1] - cy) * f;
        }
        return out;
    }

    /** Outline points of a rectangle with rounded corners, clockwise from the top-left arc. */
    private static float[][] roundedRect(float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) / 2f);
        int seg = 5;
        float[][] centres = {
            {x + r, y + r}, {x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}
        };
        float[] startDeg = {180, 270, 0, 90};
        float[][] pts = new float[4 * (seg + 1)][2];
        int k = 0;
        for (int c = 0; c < 4; c++) {
            for (int s = 0; s <= seg; s++) {
                double a = Math.toRadians(startDeg[c] + 90.0 * s / seg);
                pts[k][0] = centres[c][0] + r * (float) Math.cos(a);
                pts[k][1] = centres[c][1] + r * (float) Math.sin(a);
                k++;
            }
        }
        return pts;
    }

    private static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ raw drawing

    private BufferBuilder buffer;

    private void beginShapes() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // Depth test on with depth writes: the card hides the chat even if the chat is drawn after it.
        // The card background covers the whole card, so this holds at any Background Opacity.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    /** Shapes are drawn in submission order inside one buffer, so later shapes blend over earlier ones. */
    private void endShapes() {
        // build() returns null when every shape was skipped as fully transparent
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        buffer = null;
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void tri(Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        if ((argb >>> 24) == 0) return;
        buffer.addVertex(m, x1, y1, 0f).setColor(argb);
        buffer.addVertex(m, x2, y2, 0f).setColor(argb);
        buffer.addVertex(m, x3, y3, 0f).setColor(argb);
    }

    private void rect(Matrix4f m, float x1, float y1, float x2, float y2, int argb) {
        tri(m, x1, y1, x2, y1, x2, y2, argb);
        tri(m, x1, y1, x2, y2, x1, y2, argb);
    }

    /** Fan from the chart centre: every radar shape is visible from the centre, so this is exact. */
    private void fillPolygon(Matrix4f m, float[][] pts, int argb) {
        fillConvex(m, pts, cx, cy, argb);
    }

    /** Fan from an interior point; exact for any shape fully visible from that point. */
    private void fillConvex(Matrix4f m, float[][] pts, float px, float py, int argb) {
        for (int i = 0; i < pts.length; i++) {
            float[] a = pts[i];
            float[] b = pts[(i + 1) % pts.length];
            tri(m, px, py, a[0], a[1], b[0], b[1], argb);
        }
    }

    private void outlineClosed(Matrix4f m, float[][] pts, float width, int argb) {
        for (int i = 0; i < pts.length; i++) {
            float[] a = pts[i];
            float[] b = pts[(i + 1) % pts.length];
            line(m, a[0], a[1], b[0], b[1], width, argb);
        }
    }

    private void line(Matrix4f m, float x1, float y1, float x2, float y2, float width, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.001f) return;
        float nx = -dy / len * width / 2f;
        float ny = dx / len * width / 2f;
        tri(m, x1 + nx, y1 + ny, x2 + nx, y2 + ny, x2 - nx, y2 - ny, argb);
        tri(m, x1 + nx, y1 + ny, x2 - nx, y2 - ny, x1 - nx, y1 - ny, argb);
    }
}
