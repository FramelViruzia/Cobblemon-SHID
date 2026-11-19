package dev.cobblemonshid;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;

import java.util.Map;

/**
 * IVs for one Pokemon, in hexagon order (clockwise from the top):
 * HP, Attack, Defence, Speed, Sp. Def, Sp. Atk. Same layout as Cobblemon's summary IV tab.
 *
 * "Base" is the remembered original (see OriginalIvStore) and never moves when caps or candies
 * are used. "Final" is what the Pokemon has now: its hyper-trained value if it has one,
 * otherwise its current base, which a Wooden Cap may have re-rolled.
 */
final class IvSnapshot {
    static final Stats[] ORDER = {
        Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPEED, Stats.SPECIAL_DEFENCE, Stats.SPECIAL_ATTACK
    };
    static final int MAX = 31;

    final int[] base = new int[6];
    final int[] fin = new int[6];
    final boolean anyChanged;
    /** No IV data on the client yet: the card shows "-" instead of numbers. */
    final boolean unknown;

    /** Fixed values, used for the settings preview. */
    IvSnapshot(int[] baseValues, int[] finalValues) {
        boolean any = false;
        for (int i = 0; i < 6; i++) {
            base[i] = clamp(baseValues[i]);
            fin[i] = clamp(finalValues[i]);
            if (fin[i] != base[i]) any = true;
        }
        anyChanged = any;
        unknown = false;
    }

    private IvSnapshot() {
        anyChanged = false;
        unknown = true;
    }

    /** Placeholder for an owned Pokemon whose IVs the client has not received yet. */
    static IvSnapshot unknown() {
        return new IvSnapshot();
    }

    IvSnapshot(Pokemon pokemon, int[] original) {
        unknown = false;
        IVs ivs = pokemon.getIvs();
        Map<?, Integer> trained = ivs.getHyperTrainedIVs();
        boolean any = false;
        for (int i = 0; i < 6; i++) {
            Stats stat = ORDER[i];
            base[i] = clamp(original[i]);
            Integer hyper = trained.get(stat);
            fin[i] = clamp(hyper != null ? hyper : ivs.getOrDefault(stat));
            if (fin[i] != base[i]) any = true;
        }
        anyChanged = any;
    }

    boolean isChanged(int i) {
        return fin[i] != base[i];
    }

    int finalTotal() {
        int sum = 0;
        for (int v : fin) sum += v;
        return sum;
    }

    double finalAverage() {
        return finalTotal() / 6.0;
    }

    int perfectCount() {
        int n = 0;
        for (int v : fin) if (v >= MAX) n++;
        return n;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(MAX, v));
    }
}
