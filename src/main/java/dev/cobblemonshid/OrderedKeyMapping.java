package dev.cobblemonshid;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * Key mapping with a fixed place in the Controls list. Minecraft sorts keys inside a category by
 * their translated name; this keeps the mod's keys in the order they were declared instead.
 */
final class OrderedKeyMapping extends KeyMapping {

    private final int order;

    OrderedKeyMapping(String name, int keyCode, String category, int order) {
        super(name, InputConstants.Type.KEYSYM, keyCode, category);
        this.order = order;
    }

    @Override
    public int compareTo(KeyMapping other) {
        if (other instanceof OrderedKeyMapping o && getCategory().equals(o.getCategory())) {
            return Integer.compare(order, o.order);
        }
        return super.compareTo(other);
    }
}
