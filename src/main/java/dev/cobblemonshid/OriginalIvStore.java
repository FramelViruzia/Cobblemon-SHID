package dev.cobblemonshid;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Remembers each Pokemon's base IVs from the first time this mod saw it.
 *
 * Needed because Cobblemon Utility+'s Wooden Cap writes its random rolls straight into the
 * base IVs (IVs.set), so after a re-roll the game itself no longer knows the original values.
 * The copy is saved to config/cobblemon_shid_original_ivs.json so it survives restarts.
 */
final class OriginalIvStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, int[]>>() {}.getType();

    /** Pokemon UUID -> base IVs in hexagon order (see IvSnapshot.ORDER). */
    private static final Map<String, int[]> originals = new HashMap<>();
    private static boolean loaded;
    private static boolean dirty;
    private static long lastScanMs;
    private static long lastSaveMs;

    private OriginalIvStore() {}

    /** The remembered original base IVs, recording the current ones if this Pokemon is new. */
    static int[] originalFor(Pokemon pokemon) {
        ensureLoaded();
        String key = pokemon.getUuid().toString();
        int[] original = originals.get(key);
        if (original == null) {
            original = currentBase(pokemon);
            originals.put(key, original);
            dirty = true;
        }
        return original;
    }

    /**
     * Called every frame. Once a second it records any party Pokemon not seen before, so the
     * originals are captured before a cap is ever used, not only when one is aimed at.
     */
    static void tick() {
        ensureLoaded();
        long now = System.currentTimeMillis();
        if (now - lastScanMs >= 1000L) {
            lastScanMs = now;
            try {
                for (Pokemon pokemon : CobblemonClient.INSTANCE.getStorage().getParty().getSlots()) {
                    if (pokemon != null) originalFor(pokemon);
                }
            } catch (Throwable ignored) {
                // Party not synced yet
            }
        }
        if (dirty && now - lastSaveMs >= 2000L) {
            lastSaveMs = now;
            save();
        }
    }

    private static int[] currentBase(Pokemon pokemon) {
        IVs ivs = pokemon.getIvs();
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            out[i] = Math.max(0, Math.min(IvSnapshot.MAX, ivs.getOrDefault(IvSnapshot.ORDER[i])));
        }
        return out;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(ShidClient.MOD_ID + "_original_ivs.json");
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path file = path();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, int[]> data = GSON.fromJson(reader, MAP_TYPE);
            if (data != null) {
                data.forEach((key, values) -> {
                    if (values != null && values.length == 6) originals.put(key, values);
                });
            }
        } catch (Exception e) {
            ShidClient.LOGGER.warn("Could not read {}: {}", file, e.getMessage());
        }
    }

    private static void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(originals, MAP_TYPE, writer);
            }
            dirty = false;
        } catch (Exception e) {
            ShidClient.LOGGER.warn("Could not write {}: {}", file, e.getMessage());
        }
    }
}
