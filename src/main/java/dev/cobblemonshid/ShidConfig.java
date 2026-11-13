package dev.cobblemonshid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings stored in config/cobblemon_shid.json.
 * The file is re-read automatically when it changes, so the HUD can be moved while the game runs.
 */
public final class ShidConfig {

    /** Where the panel is pinned on screen before offsetX/offsetY are applied. */
    public enum Anchor {
        /** Left of the crosshair, bottom edge level with Cobblemon's last party slot. */
        BESIDE_CROSSHAIR,
        TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT, TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT
    }

    /** Bump when a default changes in a way existing config files should pick up. */
    private static final int CURRENT_VERSION = 3;

    // ---- values written to the JSON file ----
    /** Missing in files from 1.0.0, so Gson leaves it at 0 and load() can migrate them. */
    public int configVersion = 0;
    public boolean enabled = true;
    public Anchor anchor = Anchor.BESIDE_CROSSHAIR;
    /** Pixels (GUI-scaled) moved right from the anchor; negative moves left. */
    public int offsetX = 0;
    /** Pixels (GUI-scaled) moved down from the anchor; negative moves up. */
    public int offsetY = 0;
    /** Size multiplier for the whole panel. */
    public float scale = 1.0f;
    /** Text size relative to the normal Minecraft font (0.67 = 6px lines, 12 screen px at GUI scale 2). */
    public float textScale = 0.67f;
    /** Size of the stat names (HP, Atk...) relative to the normal font (0.56 = 10 screen px at GUI scale 2). */
    public float statNameScale = 0.56f;
    /** How long the +/- change markers stay visible after an IV changes. */
    public float changeHighlightSeconds = 4.0f;
    /** When false, only the items in extraItems trigger the HUD. */
    public boolean useDefaultItems = true;
    /** Additional item ids (e.g. "cobblemon_utility:cobblemax") that should also show the HUD. */
    public List<String> extraItems = new ArrayList<>();

    // ---- runtime state ----
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ShidConfig instance = new ShidConfig();
    // Filled by load(); building it here would run before DEFAULT_ITEMS (declared below) exists
    private static Set<String> triggerItems = Set.of();
    private static long lastModified = -1L;
    private static long lastCheckMs = 0L;

    private static final List<String> DEFAULT_ITEMS = List.of(
        // Cobblemon IV candies (raise or lower hyper-trained IVs)
        "cobblemon:health_candy", "cobblemon:mighty_candy", "cobblemon:tough_candy",
        "cobblemon:smart_candy", "cobblemon:courage_candy", "cobblemon:quick_candy",
        "cobblemon:sickly_candy", "cobblemon:weak_candy", "cobblemon:brittle_candy",
        "cobblemon:numb_candy", "cobblemon:coward_candy", "cobblemon:slow_candy",
        // Cobblemon Utility+ bottle caps
        "cobblemon_utility:goldencap", "cobblemon_utility:woodencap",
        "cobblemon_utility:hpsilvercap", "cobblemon_utility:atksilvercap", "cobblemon_utility:defsilvercap",
        "cobblemon_utility:spatksilvercap", "cobblemon_utility:spdefsilvercap", "cobblemon_utility:speedsilvercap",
        "cobblemon_utility:shininghpsilvercap", "cobblemon_utility:shiningatksilvercap",
        "cobblemon_utility:shiningdefsilvercap", "cobblemon_utility:shiningspatksilvercap",
        "cobblemon_utility:shiningspdefsilvercap", "cobblemon_utility:shiningspeedsilvercap",
        "cobblemon_utility:hpobsidiancap", "cobblemon_utility:atkobsidiancap", "cobblemon_utility:defobsidiancap",
        "cobblemon_utility:spatkobsidiancap", "cobblemon_utility:spdefobsidiancap", "cobblemon_utility:speedobsidiancap",
        "cobblemon_utility:hpvoidcap", "cobblemon_utility:atkvoidcap", "cobblemon_utility:defvoidcap",
        "cobblemon_utility:spatkvoidcap", "cobblemon_utility:spdefvoidcap", "cobblemon_utility:speedvoidcap"
    );

    public static ShidConfig get() {
        reloadIfChanged();
        return instance;
    }

    public static boolean isTriggerItem(String itemId) {
        return triggerItems.contains(itemId);
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(ShidClient.MOD_ID + ".json");
    }

    public static void load() {
        Path file = path();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                ShidConfig loaded = GSON.fromJson(reader, ShidConfig.class);
                if (loaded != null) {
                    loaded.migrate();
                    loaded.sanitize();
                    instance = loaded;
                }
            } catch (Exception e) {
                ShidClient.LOGGER.warn("Could not read {}, keeping previous settings: {}", file, e.getMessage());
            }
        } else {
            instance.configVersion = CURRENT_VERSION;
        }
        // Writing back adds any options introduced by a newer version of the mod
        save();
        triggerItems = buildTriggerItems(instance);
        try {
            lastModified = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ignored) {
            lastModified = -1L;
        }
    }

    private static void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            ShidClient.LOGGER.warn("Could not write {}: {}", file, e.getMessage());
        }
    }

    private static void reloadIfChanged() {
        long now = System.currentTimeMillis();
        if (now - lastCheckMs < 1000L) return;
        lastCheckMs = now;
        try {
            Path file = path();
            if (!Files.exists(file)) return;
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (modified != lastModified) {
                ShidClient.LOGGER.info("Config changed on disk, reloading");
                load();
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Older versions pinned the card to the left edge (offsetX 8 in 1.0.0, 70 in 1.1.0). If the
     * position is still one of those defaults, move it beside the crosshair; a position the
     * player set themselves is left alone.
     */
    private void migrate() {
        if (configVersion < 3 && anchor == Anchor.CENTER_LEFT && offsetY == 0
                && (offsetX == 8 || offsetX == 70)) {
            anchor = Anchor.BESIDE_CROSSHAIR;
            offsetX = 0;
        }
        configVersion = CURRENT_VERSION;
    }

    private void sanitize() {
        if (anchor == null) anchor = Anchor.BESIDE_CROSSHAIR;
        if (!(scale > 0.1f) || scale > 5f) scale = 1.0f;
        if (!(textScale >= 0.3f) || textScale > 2f) textScale = 0.67f;
        // Missing from older files: Gson leaves it at 0
        if (!(statNameScale >= 0.3f) || statNameScale > 2f) statNameScale = 0.56f;
        if (changeHighlightSeconds < 0f) changeHighlightSeconds = 0f;
        if (extraItems == null) extraItems = new ArrayList<>();
    }

    private static Set<String> buildTriggerItems(ShidConfig config) {
        Set<String> items = new HashSet<>();
        if (config.useDefaultItems) items.addAll(DEFAULT_ITEMS);
        for (String id : config.extraItems) {
            if (id != null && !id.isBlank()) items.add(id.trim());
        }
        return items;
    }
}
