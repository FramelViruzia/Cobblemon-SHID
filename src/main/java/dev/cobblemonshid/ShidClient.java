package dev.cobblemonshid;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ShidClient implements ClientModInitializer {
    public static final String MOD_ID = "cobblemon_shid";
    public static final Logger LOGGER = LoggerFactory.getLogger("Cobblemon SHID");

    /** The mod's id before it was renamed; its files are moved over once. */
    private static final String OLD_ID = "cobblemon_uehd";

    @Override
    public void onInitializeClient() {
        migrateOldFiles();
        ShidConfig.load();
        IvHudRenderer renderer = new IvHudRenderer();
        HudRenderCallback.EVENT.register(renderer::render);
        LOGGER.info("Cobblemon SHID loaded");
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
