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
import org.joml.Matrix4f;

import java.util.Locale;

/**
 * Draws the IV card: header (IVs tag, name, perfect count, average), a hexagon chart in the
 * layout of Cobblemon's summary IV tab, a colour legend and a total bar.
 *
 * Colouring rule:
 *  - Nothing changed yet: one yellow shape showing the IVs.
 *  - Something changed by a cap or candy: the original base IVs become a faint grey shape and the
 *    final IVs are drawn in translucent yellow over it.
 */
final class IvHudRenderer {

    // Card layout, in GUI pixels before the config scale is applied
    private static final int W = 130;
    private static final int H = 166;
    private static final float CX = W / 2f;
    private static final float CY = 73f;
    private static final float R = 32f;
    private static final float CORNER = 5f;
    private static final int PAD = 4;
    private static final int DIVIDER_Y = 17;
    private static final int LEGEND_Y = 130;
    private static final int FOOTER_Y = 143;
    private static final int FOOTER_H = 16;

    /** Cobblemon's party overlay: 6 slots of 30px, centred on the screen, then raised by 10px. */
    /**
     * Depth the card is drawn at. Vanilla chat sits at z 50, so the card has to be in front of it or
     * long chat logs cover the card's text. Kept below 400, where tooltips are drawn.
     */
    private static final float CARD_Z = 200f;

    private static final int PARTY_HALF_HEIGHT = 6 * 30 / 2;
    private static final int PARTY_LIFT = 10;
    private static final int PARTY_SLOT_GAP = 4;
    /** Cobblemon's party slots are 62px wide at the left edge; the card never goes left of this. */
    private static final int PARTY_RIGHT_EDGE = 62 + 8;
    /** Default gap between the card and the crosshair when anchored beside it. */
    private static final int CROSSHAIR_GAP = 65;
    /** How far the card's bottom edge sits below the last party slot. */
    private static final int BELOW_PARTY = 20;

    // Colours (ARGB)
    private static final int PANEL_BG = 0xE6141920;
    private static final int PANEL_BORDER = 0xFF3B4552;
    private static final int TAG_BG = 0xFF3B4552;
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

    /** Text size relative to the normal Minecraft font, from the config. */
    private float ts = 2f / 3f;
    /** Size of the stat names around the chart, from the config. */
    private float nameScale = 0.56f;

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
        // Stay visible while chatting; hide behind any other screen (inventory, summary, PC...)
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;

        ShidConfig config = ShidConfig.get();
        if (!config.enabled) return;
        if (!TargetFinder.isHoldingTriggerItem(player)) return;

        PokemonEntity entity = TargetFinder.lookedAtEntity(mc);
        if (entity == null) return;

        TargetFinder.Target target = TargetFinder.resolve(entity, player);
        if (target == null) return;

        ts = config.textScale;
        nameScale = config.statNameScale;

        if (target.ownedButUnsynced()) {
            drawUnsyncedNotice(g, mc, config);
            return;
        }

        Pokemon pokemon = target.pokemon();
        // Read straight from the client's party copy every frame: Cobblemon updates it as soon
        // as the server changes the IVs, so the card is always current.
        IvSnapshot snap = new IvSnapshot(pokemon, OriginalIvStore.originalFor(pokemon));

        g.pose().pushPose();
        float[] pos = panelPosition(mc, config, W, H);
        g.pose().translate(pos[0], pos[1], CARD_Z);
        g.pose().scale(config.scale, config.scale, 1f);
        drawCard(g, mc.font, pokemon, snap);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ card

    private void drawCard(GuiGraphics g, Font font, Pokemon pokemon, IvSnapshot snap) {
        // Text pieces whose widths the shapes depend on
        Component tag = Component.translatable("cobblemon_shid.hud.title");
        int tagW = Math.round(width(font, tag)) + 4;
        int lineH = Math.round(9 * ts);

        // ---- all shapes in one pass, so text drawn afterwards sits on top ----
        g.flush();
        Matrix4f m = g.pose().last().pose();
        beginShapes();

        float[][] card = roundedRect(0, 0, W, H, CORNER);
        fillConvex(m, card, W / 2f, H / 2f, PANEL_BG);
        outlineClosed(m, card, 1f, PANEL_BORDER);

        float[][] tagBox = roundedRect(PAD, PAD, tagW, lineH + 4, 2f);
        fillConvex(m, tagBox, PAD + tagW / 2f, PAD + (lineH + 4) / 2f, TAG_BG);
        rect(m, PAD, DIVIDER_Y, W - PAD, DIVIDER_Y + 0.5f, 0x30FFFFFF);

        // Chart background, grid rings and spokes
        float[][] outer = vertices(new int[]{31, 31, 31, 31, 31, 31});
        fillPolygon(m, outer, CHART_BG);
        for (int ring = 1; ring <= 2; ring++) {
            outlineClosed(m, scaled(outer, ring / 3f), 0.5f, GRID);
        }
        for (float[] v : outer) line(m, CX, CY, v[0], v[1], 0.5f, GRID);

        // IV shapes: faint grey original underneath, yellow final on top
        float[][] fin = vertices(snap.fin);
        if (snap.anyChanged) {
            float[][] base = vertices(snap.base);
            fillPolygon(m, base, BASE_FILL);
            outlineClosed(m, base, 0.75f, BASE_EDGE);
        }
        fillPolygon(m, fin, FINAL_FILL);
        outlineClosed(m, fin, 1f, FINAL_EDGE);
        outlineClosed(m, outer, 0.75f, CHART_EDGE);

        // Legend squares
        float[] legendX = legendLayout(font, snap);
        if (snap.anyChanged) {
            legendSquare(m, legendX[0], BASE_FILL | 0xFF000000);
            legendSquare(m, legendX[1], 0xFFFFE135);
        } else {
            legendSquare(m, legendX[0], 0xFFFFE135);
        }

        // Footer box and total bar
        float[][] footer = roundedRect(PAD, FOOTER_Y, W - 2 * PAD, FOOTER_H, 3f);
        fillConvex(m, footer, W / 2f, FOOTER_Y + FOOTER_H / 2f, FOOTER_BG);
        outlineClosed(m, footer, 0.75f, PANEL_BORDER);
        float[] bar = totalBarBounds(font, snap);
        float barY = FOOTER_Y + FOOTER_H / 2f - 1.5f;
        rect(m, bar[0], barY, bar[1], barY + 3, BAR_TRACK);
        rect(m, bar[0], barY, bar[0] + (bar[1] - bar[0]) * snap.finalTotal() / 186f, barY + 3, BAR_FILL);

        endShapes();

        // ---- text ----
        drawHeaderText(g, font, pokemon, snap, tag, tagW);
        for (int i = 0; i < 6; i++) drawStatLabel(g, font, i, snap);
        drawLegendText(g, font, snap, legendX);
        drawTotalText(g, font, snap);
    }

    /** "[IVs] Name ...... 1/6 · AVG 25.0" */
    private void drawHeaderText(GuiGraphics g, Font font, Pokemon pokemon, IvSnapshot snap, Component tag, int tagW) {
        float textY = PAD + 2 + 0.5f;
        text(g, font, tag, PAD + 2, textY, TEXT, false);

        MutableComponent right = Component.literal(snap.perfectCount() + "/6").withColor(TEXT)
            .append(Component.literal(" · ").withColor(TEXT_DIM))
            .append(Component.translatable("cobblemon_shid.hud.average").withColor(TEXT_DIM))
            .append(Component.literal(" " + String.format(Locale.ROOT, "%.1f", snap.finalAverage())).withColor(TEXT));
        float rightW = width(font, right);
        text(g, font, right, W - PAD - rightW, textY, TEXT, false);

        float nameX = PAD + tagW + 3;
        float nameMax = W - PAD - rightW - 3 - nameX;
        String name = pokemon.getDisplayName(false).getString();
        int maxRaw = (int) (nameMax / ts);
        if (font.width(name) > maxRaw) {
            name = font.plainSubstrByWidth(name, Math.max(0, maxRaw - font.width("…"))) + "…";
        }
        text(g, font, Component.literal(name), nameX, textY, TEXT, false);
    }

    /** Stat name on one line; value below it as "base (final)" when a cap or candy changed it. */
    private void drawStatLabel(GuiGraphics g, Font font, int i, IvSnapshot snap) {
        double angle = Math.toRadians(-90 + 60 * i);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float lx = CX + (R + 4f) * cos;
        float ly = CY + (R + 4f) * sin;

        Component statName = Component.translatable("cobblemon_shid.stat." + STAT_KEYS[i]);
        MutableComponent value;
        if (snap.isChanged(i)) {
            value = Component.literal(String.valueOf(snap.base[i])).withColor(TEXT_BASE)
                .append(Component.literal(" (").withColor(TEXT_BASE))
                .append(Component.literal(String.valueOf(snap.fin[i])).withColor(valueColor(snap.fin[i])))
                .append(Component.literal(")").withColor(TEXT_BASE));
        } else {
            value = Component.literal(String.valueOf(snap.base[i])).withColor(valueColor(snap.base[i]));
        }

        float nameH = 9 * nameScale;
        float lineH = 9 * ts;
        float block = nameH + lineH + 1;
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
        float x = (W - total) / 2f;
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
        return Component.literal(String.valueOf(snap.finalTotal())).withColor(TEXT)
            .append(Component.literal("/186").withColor(TEXT_DIM));
    }

    private float[] totalBarBounds(Font font, IvSnapshot snap) {
        float x1 = PAD + 5 + width(font, totalLabel()) + 4;
        float x2 = W - PAD - 5 - width(font, totalAmount(snap)) - 4;
        return new float[]{x1, Math.max(x1, x2)};
    }

    private void drawTotalText(GuiGraphics g, Font font, IvSnapshot snap) {
        float y = FOOTER_Y + (FOOTER_H - 9 * ts) / 2f + 0.5f;
        text(g, font, totalLabel(), PAD + 5, y, TEXT_DIM, false);
        MutableComponent amount = totalAmount(snap);
        text(g, font, amount, W - PAD - 5 - width(font, amount), y, TEXT, false);
    }

    private void drawUnsyncedNotice(GuiGraphics g, Minecraft mc, ShidConfig config) {
        Font font = mc.font;
        Component line = Component.translatable("cobblemon_shid.hud.unsynced");
        int w = Math.round(width(font, line)) + 12;
        int h = Math.round(9 * ts) + 10;
        g.pose().pushPose();
        float[] pos = panelPosition(mc, config, w, h);
        g.pose().translate(pos[0], pos[1], CARD_Z);
        g.pose().scale(config.scale, config.scale, 1f);
        g.flush();
        Matrix4f m = g.pose().last().pose();
        beginShapes();
        float[][] box = roundedRect(0, 0, w, h, CORNER);
        fillConvex(m, box, w / 2f, h / 2f, PANEL_BG);
        outlineClosed(m, box, 1f, PANEL_BORDER);
        endShapes();
        text(g, font, line, 6, 5.5f, TEXT_DIM, false);
        g.pose().popPose();
    }

    private static float[] panelPosition(Minecraft mc, ShidConfig config, int w, int h) {
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float sw = w * config.scale;
        float sh = h * config.scale;
        float x;
        float y;
        switch (config.anchor) {
            case TOP_LEFT -> { x = 0; y = 0; }
            case CENTER_LEFT -> { x = 0; y = (screenH - sh) / 2f; }
            case BOTTOM_LEFT -> { x = 0; y = screenH - sh; }
            case TOP_RIGHT -> { x = screenW - sw; y = 0; }
            case CENTER_RIGHT -> { x = screenW - sw; y = (screenH - sh) / 2f; }
            case BOTTOM_RIGHT -> { x = screenW - sw; y = screenH - sh; }
            default -> {
                // BESIDE_CROSSHAIR: left of the crosshair, bottom edge a little below the last party slot
                x = screenW / 2f - CROSSHAIR_GAP - sw + config.offsetX;
                float partyBottom = screenH / 2f + PARTY_HALF_HEIGHT - PARTY_LIFT - PARTY_SLOT_GAP;
                y = partyBottom + BELOW_PARTY - sh + config.offsetY;
                // On narrow GUI-scaled screens (high GUI scale) keep clear of the party bar,
                // and never let the card leave the screen
                x = Math.max(x, PARTY_RIGHT_EDGE);
                x = Math.min(x, screenW - sw);
                y = Math.max(0, Math.min(y, screenH - sh));
                return new float[]{Math.round(x), Math.round(y)};
            }
        }
        return new float[]{Math.round(x + config.offsetX), Math.round(y + config.offsetY)};
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
        g.drawString(font, text, 0, 0, color, shadow);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ geometry

    /** Hexagon corner for each stat, at a distance proportional to value / 31. */
    private static float[][] vertices(int[] values) {
        float[][] out = new float[6][2];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(-90 + 60 * i);
            float r = R * values[i] / (float) IvSnapshot.MAX;
            out[i][0] = CX + r * (float) Math.cos(angle);
            out[i][1] = CY + r * (float) Math.sin(angle);
        }
        return out;
    }

    private static float[][] scaled(float[][] pts, float f) {
        float[][] out = new float[pts.length][2];
        for (int i = 0; i < pts.length; i++) {
            out[i][0] = CX + (pts[i][0] - CX) * f;
            out[i][1] = CY + (pts[i][1] - CY) * f;
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

    // ------------------------------------------------------------------ raw drawing

    private BufferBuilder buffer;

    private void beginShapes() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // Depth test on with depth writes: the card hides the chat even if the chat is drawn after it
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
    }

    /** Shapes are drawn in submission order inside one buffer, so later shapes blend over earlier ones. */
    private void endShapes() {
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        buffer = null;
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void tri(Matrix4f m, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
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
        fillConvex(m, pts, CX, CY, argb);
    }

    /** Fan from an interior point; exact for any shape fully visible from that point. */
    private void fillConvex(Matrix4f m, float[][] pts, float cx, float cy, int argb) {
        for (int i = 0; i < pts.length; i++) {
            float[] a = pts[i];
            float[] b = pts[(i + 1) % pts.length];
            tri(m, cx, cy, a[0], a[1], b[0], b[1], argb);
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
