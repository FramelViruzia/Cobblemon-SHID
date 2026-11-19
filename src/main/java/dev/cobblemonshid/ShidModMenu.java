package dev.cobblemonshid;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Adds the settings button in Mod Menu. Only loaded when Mod Menu is installed. */
public final class ShidModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ShidConfigScreen::new;
    }
}
