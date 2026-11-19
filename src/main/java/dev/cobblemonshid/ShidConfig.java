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
 * The file is re-read automatically when it changes, so it can still be edited while the game runs.
 */
public final class ShidConfig {

    /** Card layout. */
    public enum HudStyle {
        /** Header, hexagon, legend and total bar. */
        FULL,
        /** Hexagon only, with the average in the bottom-right corner. */
        COMPACT
    }

    /** Bump when a default changes in a way existing config files should pick up. */
    private static final int CURRENT_VERSION = 4;

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 3.0f;
    public static final float MIN_FONT = 0.5f;
    public static final float MAX_FONT = 1.5f;
    public static final float STEP = 0.05f;

    public static final HudStyle DEFAULT_STYLE = HudStyle.FULL;
    public static final float DEFAULT_SCALE = 1.0f;
    public static final float DEFAULT_FONT = 1.0f;
    public static final int DEFAULT_OPACITY = 90;

    // ---- values written to the JSON file ----
    /** Missing in files from 1.0.0, so Gson leaves it at 0 and load() can migrate them. */
    public int configVersion = 0;
    public boolean enabled = true;
    public HudStyle hudStyle = DEFAULT_STYLE;
    /** When true the card cannot be dragged; it stays where the player last put it. */
    public boolean lockPosition = true;
    /** Pixels (GUI-scaled) moved right from the default spot beside the crosshair; negative moves left. */
    public int offsetX = 0;
    /** Pixels (GUI-scaled) moved down from the default spot; negative moves up. */
    public int offsetY = 0;
    /** Size multiplier for the whole card, text included (HUD Size). Capped by GUI scale when drawn. */
    public float scale = DEFAULT_SCALE;
    /** Text size relative to the card (Font Size). 1.0 = the original 1.1.x text size. */
    public float fontSize = DEFAULT_FONT;
    /** Opacity of the card background, border and "IVs" tag, in percent (0 = transparent). */
    public int backgroundOpacity = DEFAULT_OPACITY;
    /** When false, only the items in extraItems trigger the HUD. */
    public boolean useDefaultItems = true;
    /** Additional item ids (e.g. "cobblemon_utility:cobblemax") that should also show the HUD. */
    public List<String> extraItems = new ArrayList<>();

    // ---- old keys, only read to migrate 1.1.x files; null values are not written back ----
    private String anchor;
    private Float textScale;

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

    /** A detached copy, used by the settings screen so changes only apply on Save. */
    public ShidConfig copy() {
        ShidConfig c = GSON.fromJson(GSON.toJson(this), ShidConfig.class);
        c.sanitize();
        return c;
    }

    /** True when every option the settings screen can change is the same. */
    public boolean sameSettings(ShidConfig o) {
        return enabled == o.enabled && hudStyle == o.hudStyle && lockPosition == o.lockPosition
            && offsetX == o.offsetX && offsetY == o.offsetY
            && Math.abs(scale - o.scale) < 0.001f && Math.abs(fontSize - o.fontSize) < 0.001f
            && backgroundOpacity == o.backgroundOpacity;
    }

    /** Replaces the live settings (from the settings screen) and writes them to disk. */
    public static void replace(ShidConfig next) {
        next.sanitize();
        next.configVersion = CURRENT_VERSION;
        instance = next;
        save();
        triggerItems = buildTriggerItems(instance);
    }

    /** Writes the live settings, e.g. after the card was dragged. */
    public static void saveCurrent() {
        save();
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
    }

    private static void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
            // Our own write must not look like an outside edit to reloadIfChanged()
            lastModified = Files.getLastModifiedTime(file).toMillis();
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

    private void migrate() {
        // 1.0.0 / 1.1.0 left-edge defaults, before the card moved beside the crosshair
        if (configVersion < 3 && "CENTER_LEFT".equals(anchor) && offsetY == 0
                && (offsetX == 8 || offsetX == 70)) {
            anchor = "BESIDE_CROSSHAIR";
            offsetX = 0;
        }
        if (configVersion < 4) {
            // 1.2.0 removed anchors: offsets are now measured from the spot beside the crosshair,
            // so offsets meant for another anchor would put the card somewhere random
            if (anchor != null && !"BESIDE_CROSSHAIR".equals(anchor)) {
                offsetX = 0;
                offsetY = 0;
            }
            // textScale was absolute (0.67 = normal); fontSize is relative to that
            if (textScale != null && textScale > 0f) {
                fontSize = textScale / 0.67f;
            }
            // Players who had moved the card keep it where it was, but locked
            lockPosition = true;
        }
        anchor = null;
        textScale = null;
        configVersion = CURRENT_VERSION;
    }

    private void sanitize() {
        if (hudStyle == null) hudStyle = DEFAULT_STYLE;
        // Missing or broken values: Gson leaves floats at 0
        scale = (scale > 0f && !Float.isNaN(scale)) ? snap(scale, MIN_SCALE, MAX_SCALE) : DEFAULT_SCALE;
        fontSize = (fontSize > 0f && !Float.isNaN(fontSize)) ? snap(fontSize, MIN_FONT, MAX_FONT) : DEFAULT_FONT;
        backgroundOpacity = Math.max(0, Math.min(100, backgroundOpacity));
        if (extraItems == null) extraItems = new ArrayList<>();
    }

    /** Clamps to [min, max] and rounds to the slider step (0.05). */
    public static float snap(float v, float min, float max) {
        float clamped = Math.max(min, Math.min(max, v));
        return Math.round(clamped / STEP) * STEP;
    }

    /**
     * Largest HUD Size that still fits at the current GUI scale:
     * GUI 4 and up 1.0x, GUI 3 1.5x, GUI 2 2.0x, GUI 1 3.0x.
     */
    public static float maxScaleForGui(double guiScale) {
        if (guiScale >= 4) return 1.0f;
        if (guiScale >= 3) return 1.5f;
        if (guiScale >= 2) return 2.0f;
        return MAX_SCALE;
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
