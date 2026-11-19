package dev.cobblemonshid;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Opened while the move key (Left Alt by default) is held over the card: shows the mouse so the
 * card can be dragged. No background, the game stays visible; closes when the key is let go.
 */
final class MoveHudScreen extends Screen {

    MoveHudScreen() {
        super(Component.translatable("cobblemon_shid.move.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // Nothing: keep the game (and the card) unblurred
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        HudDrag.update(mouseX, mouseY);
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, Component.translatable("cobblemon_shid.move.hint"), width / 2, 8, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (HudDrag.press(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (HudDrag.release(button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        ShidConfig config = ShidConfig.get();
        if (!ShidClient.isMoveKeyHeld() || config.lockPosition || !config.enabled) onClose();
    }

    @Override
    public void removed() {
        HudDrag.cancel();
    }
}
