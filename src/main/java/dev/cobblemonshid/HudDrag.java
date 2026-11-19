package dev.cobblemonshid;

import net.minecraft.client.Minecraft;

/**
 * Moves the in-game card with the mouse when HUD Position is Draggable. Works on any screen that
 * shows the mouse over the game: the chat, and the move screen opened by holding the move key.
 * The new spot is saved as offsetX/offsetY when the mouse button is released.
 */
final class HudDrag {

    private static boolean dragging;
    private static float grabX;
    private static float grabY;

    private HudDrag() {}

    /** Mouse pressed; returns true when it grabbed the card, so the screen should ignore the click. */
    static boolean press(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ShidConfig config = ShidConfig.get();
        if (!config.enabled || config.lockPosition || !IvHudRenderer.isVisible()) return false;
        float[] b = IvHudRenderer.lastBounds();
        if (mouseX < b[0] || mouseX > b[0] + b[2] || mouseY < b[1] || mouseY > b[1] + b[3]) return false;
        dragging = true;
        grabX = (float) mouseX - b[0];
        grabY = (float) mouseY - b[1];
        return true;
    }

    /** Called every frame while a screen is open; moves the card if it is being dragged. */
    static void update(double mouseX, double mouseY) {
        if (!dragging) return;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float[] b = IvHudRenderer.lastBounds();
        float[] origin = IvHudRenderer.lastDefaultOrigin();
        float x = clamp((float) mouseX - grabX, 0, screenW - b[2]);
        float y = clamp((float) mouseY - grabY, 0, screenH - b[3]);
        ShidConfig config = ShidConfig.get();
        config.offsetX = Math.round(x - origin[0]);
        config.offsetY = Math.round(y - origin[1]);
        // (0, 0) means "default spot", which is pushed clear of the party bar; nudge by a pixel
        // so a card dropped exactly there stays where the player put it
        if (config.offsetX == 0 && config.offsetY == 0) config.offsetY = -1;
    }

    /** Mouse released; returns true when it ended a drag. */
    static boolean release(int button) {
        if (!dragging || button != 0) return false;
        dragging = false;
        ShidConfig.saveCurrent();
        return true;
    }

    /** The screen closed in the middle of a drag: keep the card where it is. */
    static void cancel() {
        if (!dragging) return;
        dragging = false;
        ShidConfig.saveCurrent();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(v, Math.max(min, max)));
    }
}
