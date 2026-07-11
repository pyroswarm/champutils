package com.champutils.breeding;

import java.lang.reflect.Method;

public final class BreedingResourcePackBridge {
    private BreedingResourcePackBridge() {}

    public static void registerAssets() {
        try {
            Class<?> utils = Class.forName("eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils");
            Method method = utils.getMethod("addModAssets", String.class);
            method.invoke(null, "champutils");
            System.out.println("[ChampUtils][Breeding] Registered Egg assets with Polymer resource pack generation.");
        } catch (ClassNotFoundException missingPolymerResourcePack) {
            System.out.println("[ChampUtils][Breeding] Polymer resource-pack module was not found; the Egg will use Cobblemon's fallback model until assets are included.");
        } catch (Throwable error) {
            System.err.println("[ChampUtils][Breeding] Failed to register Egg assets with Polymer.");
            error.printStackTrace();
        }
    }
}
