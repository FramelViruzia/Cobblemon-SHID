package dev.cobblemonshid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers the last IVs seen for each Pokemon so the HUD can mark which stats just changed
 * (e.g. after a Wooden Cap re-roll) and by how much.
 */
final class ChangeTracker {

    private static final class Entry {
        final int[] lastFinal = new int[6];
        final int[] delta = new int[6];
        final long[] changedAt = new long[6];
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    /** Compares against the previous snapshot of this Pokemon and records any differences. */
    void observe(UUID id, IvSnapshot snap) {
        Entry entry = entries.get(id);
        long now = System.currentTimeMillis();
        if (entry == null) {
            entry = new Entry();
            System.arraycopy(snap.fin, 0, entry.lastFinal, 0, 6);
            entries.put(id, entry);
            return;
        }
        for (int i = 0; i < 6; i++) {
            int before = entry.lastFinal[i];
            int after = snap.fin[i];
            if (before != after) {
                entry.delta[i] = after - before;
                entry.changedAt[i] = now;
                entry.lastFinal[i] = after;
            }
        }
    }

    /** Change in the final IV for stat i, or 0 if it has not changed recently. */
    int recentDelta(UUID id, int i, long windowMs) {
        Entry entry = entries.get(id);
        if (entry == null || entry.changedAt[i] == 0L) return 0;
        return System.currentTimeMillis() - entry.changedAt[i] <= windowMs ? entry.delta[i] : 0;
    }

    /** 1.0 right after a change, fading to 0.0 at the end of the window. */
    float recentStrength(UUID id, int i, long windowMs) {
        Entry entry = entries.get(id);
        if (entry == null || entry.changedAt[i] == 0L || windowMs <= 0) return 0f;
        long age = System.currentTimeMillis() - entry.changedAt[i];
        if (age > windowMs) return 0f;
        return 1f - (float) age / (float) windowMs;
    }
}
