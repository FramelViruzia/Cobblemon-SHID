package dev.cobblemonshid;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ShidClient implements ClientModInitializer {
    public static final String MOD_ID = "cobblemon_shid";
    public static final Logger LOGGER = LoggerFactory.getLogger("Cobblemon SHID");

    /** The mod's id before it was renamed; its files are moved over once. */
    private static final String OLD_ID = "cobblemon_uehd";

    /** Hold to show the mouse and drag the card (when HUD Position is Draggable). */
    private static KeyMapping moveKey;

    @Override
    public void onInitializeClient() {
        migrateOldFiles();
        ShidConfig.load();

        IvHudRenderer renderer = new IvHudRenderer();
        HudRenderCallback.EVENT.register(renderer::render);

        moveKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.cobblemon_shid.move_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.cobblemon_shid"));
        ClientTickEvents.END_CLIENT_TICK.register(ShidClient::onClientTick);

        // Drag the card while the chat is open
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> !HudDrag.press(x, y, button));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, x, y, button) -> !HudDrag.release(button));
            ScreenEvents.afterRender(screen).register((s, g, x, y, delta) -> HudDrag.update(x, y));
            ScreenEvents.remove(screen).register(s -> HudDrag.cancel());
        });

        LOGGER.info("Cobblemon SHID loaded");
    }

    private static void onClientTick(Minecraft client) {
        if (client.screen != null || client.player == null) return;
        if (!moveKey.isDown()) return;
        ShidConfig config = ShidConfig.get();
        if (config.enabled && !config.lockPosition && IvHudRenderer.isVisible()) {
            client.setScreen(new MoveHudScreen());
        }
    }

    /**
     * Reads the move key straight from the keyboard. Opening a screen resets Minecraft's own key
     * state, so the move screen uses this to notice when the key is let go.
     */
    static boolean isMoveKeyHeld() {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(moveKey);
        if (key == null || key.equals(InputConstants.UNKNOWN)) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    /** Keeps settings and remembered base IVs from the old Cobblemon UEHD files. */
    private static void migrateOldFiles() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        for (String suffix : new String[]{".json", "_original_ivs.json"}) {
            Path from = dir.resolve(OLD_ID + suffix);
            Path to = dir.resolve(MOD_ID + suffix);
            if (Files.exists(from) && !Files.exists(to)) {
                try {
                    Files.move(from, to);
                    LOGGER.info("Moved {} to {}", from.getFileName(), to.getFileName());
                } catch (Exception e) {
                    LOGGER.warn("Could not move {}: {}", from, e.getMessage());
                }
            }
        }
    }
}
