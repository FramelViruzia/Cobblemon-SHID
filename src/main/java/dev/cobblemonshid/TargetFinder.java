package dev.cobblemonshid;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.storage.ClientPC;
import com.cobblemon.mod.common.client.storage.ClientParty;
import com.cobblemon.mod.common.client.storage.ClientStorageManager;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

/** Decides whether the HUD should show, and for which Pokemon. */
final class TargetFinder {

    /** Result of a lookup: the Pokemon whose IVs we can show, or a reason we cannot. */
    record Target(Pokemon pokemon, boolean ownedButUnsynced) {}

    private TargetFinder() {}

    static boolean isHoldingTriggerItem(Player player) {
        return isTrigger(player.getMainHandItem()) || isTrigger(player.getOffhandItem());
    }

    private static boolean isTrigger(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return ShidConfig.isTriggerItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** The Pokemon entity under the crosshair right now, or null. */
    static PokemonEntity lookedAtEntity(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.ENTITY
                && ((EntityHitResult) hit).getEntity() instanceof PokemonEntity entity) {
            return entity;
        }
        return null;
    }

    /**
     * Finds the player's own copy of this Pokemon. Cobblemon keeps the owner's party (and any PC it
     * has synced) on the client, and updates their IVs whenever the server changes them, which is
     * what makes the HUD update live. Wild or other players' Pokemon return null.
     */
    static Target resolve(PokemonEntity entity, Player player) {
        UUID pokemonId = entity.getPokemon().getUuid();
        Pokemon owned = findInClientStorage(pokemonId);
        if (owned != null) return new Target(owned, false);

        UUID owner = entity.getOwnerUUID();
        if (owner != null && owner.equals(player.getUUID())) {
            return new Target(null, true);
        }
        return null;
    }

    private static Pokemon findInClientStorage(UUID pokemonId) {
        try {
            ClientStorageManager storage = CobblemonClient.INSTANCE.getStorage();
            ClientParty party = storage.getParty();
            Pokemon found = party.findByUUID(pokemonId);
            if (found != null) return found;
            for (ClientPC pc : storage.getPcStores().values()) {
                found = pc.findByUUID(pokemonId);
                if (found != null) return found;
            }
        } catch (Throwable t) {
            // Storage not ready yet (e.g. while joining a world)
        }
        return null;
    }
}
