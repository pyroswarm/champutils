package com.champutils.breeding;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class BreedingEggData {
    public static final String ROOT_KEY = "champutils_breeding_egg";
    public static final String EGG_SPECIES = "champutils:egg";
    private static final int DATA_VERSION = 2;

    private BreedingEggData() {}

    public static boolean isEgg(Pokemon pokemon) {
        if (pokemon == null) return false;
        try {
            CompoundTag root = pokemon.getPersistentData().getCompound(ROOT_KEY);
            return !root.isEmpty()
                    && EGG_SPECIES.equals(speciesId(pokemon))
                    && root.getInt("version") >= 1
                    && root.hasUUID("egg_uuid")
                    && pokemon.getUuid().equals(root.getUUID("egg_uuid"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static UUID eggUuid(Pokemon egg) {
        if (!isEgg(egg)) return null;
        try { return data(egg).getUUID("egg_uuid"); }
        catch (Throwable ignored) { return null; }
    }

    public static Pokemon createPlaceholder(ServerPlayer player,
                                            Pokemon hatchling,
                                            Pokemon parentA,
                                            Pokemon parentB,
                                            int requiredSteps,
                                            boolean mysteryEgg,
                                            BreedingProfessionService.RarityTier rarity) {
        Pokemon egg = PokemonProperties.Companion
                .parse("species=\"" + EGG_SPECIES + "\" level=1")
                .create();
        egg.setTradeable(true);
        egg.setNickname(Component.literal("Egg"));
        egg.setOriginalTrainer(player.getUUID());
        egg.setOriginalTrainerName(player.getGameProfile().getName());
        egg.setCurrentHealth(Math.max(1, egg.getMaxHealth()));

        CompoundTag root = new CompoundTag();
        root.putInt("version", DATA_VERSION);
        root.putUUID("egg_uuid", egg.getUuid());
        UUID activeProfileId = com.champutils.profile.PlayerProfileManager.activeProfileIdOrNull(player.getUUID());
        if (activeProfileId == null) throw new IllegalStateException("Cannot create an Egg without an active profile.");
        root.putUUID("profile_id", activeProfileId);
        root.putUUID("audit_profile_id", activeProfileId);
        root.putUUID("parent_a_uuid", parentA.getUuid());
        root.putUUID("parent_b_uuid", parentB.getUuid());
        root.putString("parent_a_species", speciesId(parentA));
        root.putString("parent_b_species", speciesId(parentB));
        root.putString("offspring_species", speciesId(hatchling));
        root.putString("offspring_types", typeSummary(hatchling));
        root.putBoolean("mystery_egg", mysteryEgg);
        root.putString("rarity_tier", (rarity == null ? BreedingProfessionService.rarityFor(hatchling) : rarity).name());
        root.putInt("steps", 0);
        root.putInt("required_steps", Math.max(1, requiredSteps));
        root.putInt("last_persisted_steps", 0);
        root.putLong("created_at", System.currentTimeMillis());
        root.put("hatchling", hatchling.saveToNBT(player.registryAccess(), new CompoundTag()));
        egg.getPersistentData().put(ROOT_KEY, root);
        return egg;
    }

    public static int steps(Pokemon egg) {
        return data(egg).getInt("steps");
    }

    public static int requiredSteps(Pokemon egg) {
        return Math.max(1, data(egg).getInt("required_steps"));
    }

    public static int remainingSteps(Pokemon egg) {
        return Math.max(0, requiredSteps(egg) - steps(egg));
    }

    public static int progressPercent(Pokemon egg) {
        return Math.min(100, (int) Math.floor((steps(egg) * 100.0D) / requiredSteps(egg)));
    }

    public static void addSteps(Pokemon egg, int amount) {
        if (!isEgg(egg) || amount <= 0) return;
        CompoundTag root = data(egg);
        root.putInt("steps", Math.min(requiredSteps(egg), Math.max(0, root.getInt("steps") + amount)));
        egg.getPersistentData().put(ROOT_KEY, root);
    }

    public static boolean shouldPersist(Pokemon egg, int interval) {
        CompoundTag root = data(egg);
        return root.getInt("steps") - root.getInt("last_persisted_steps") >= Math.max(1, interval)
                || root.getInt("steps") >= requiredSteps(egg);
    }

    public static void markPersisted(Pokemon egg) {
        CompoundTag root = data(egg);
        root.putInt("last_persisted_steps", root.getInt("steps"));
        egg.getPersistentData().put(ROOT_KEY, root);
    }

    public static Pokemon loadHatchling(ServerPlayer player, Pokemon egg) {
        CompoundTag hatchling = data(egg).getCompound("hatchling");
        if (hatchling.isEmpty()) throw new IllegalStateException("Breeding egg is missing its hatchling payload.");
        return Pokemon.Companion.loadFromNBT(player.registryAccess(), hatchling.copy());
    }

    public static String offspringSpecies(Pokemon egg) {
        return data(egg).getString("offspring_species");
    }

    public static String offspringTypes(Pokemon egg) {
        return data(egg).getString("offspring_types");
    }

    public static boolean isMysteryEgg(Pokemon egg) {
        return isEgg(egg) && data(egg).getBoolean("mystery_egg");
    }

    public static String publicOffspringSpecies(Pokemon egg) {
        return isMysteryEgg(egg) ? "???" : offspringSpecies(egg);
    }

    public static BreedingProfessionService.RarityTier rarityTier(Pokemon egg) {
        if (!isEgg(egg)) return BreedingProfessionService.RarityTier.COMMON;
        try {
            String value = data(egg).getString("rarity_tier");
            if (!value.isBlank()) return BreedingProfessionService.RarityTier.valueOf(value);
        } catch (Throwable ignored) {
        }
        return BreedingProfessionService.RarityTier.COMMON;
    }

    /** Current profile allowed to carry and hatch this Egg. Auction delivery updates this value. */
    public static UUID profileId(Pokemon egg) {
        try { return data(egg).getUUID("profile_id"); } catch (Throwable ignored) { return null; }
    }

    /** Original breeding audit profile. This remains stable when an Egg is sold. */
    public static UUID auditProfileId(Pokemon egg) {
        if (!isEgg(egg)) return null;
        try {
            CompoundTag root = data(egg);
            if (root.hasUUID("audit_profile_id")) return root.getUUID("audit_profile_id");
            return root.hasUUID("profile_id") ? root.getUUID("profile_id") : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Moves hatch ownership to the recipient's active profile while preserving the original
     * server-side breeding audit row. This is what makes Auction House Egg sales hatchable.
     */
    public static void transferOwnership(ServerPlayer recipient, Pokemon egg) {
        if (recipient == null || !isEgg(egg)) return;
        UUID recipientProfile = com.champutils.profile.PlayerProfileManager.activeProfileIdOrNull(recipient.getUUID());
        if (recipientProfile == null) throw new IllegalStateException("Cannot deliver an Egg without an active profile.");
        CompoundTag root = data(egg);
        if (!root.hasUUID("audit_profile_id") && root.hasUUID("profile_id")) {
            root.putUUID("audit_profile_id", root.getUUID("profile_id"));
        }
        root.putUUID("profile_id", recipientProfile);
        egg.getPersistentData().put(ROOT_KEY, root);
    }

    public static void markHatchOrigin(Pokemon hatchling, UUID eggUuid, UUID profileId) {
        if (hatchling == null || eggUuid == null || profileId == null) return;
        CompoundTag origin = new CompoundTag();
        origin.putInt("version", DATA_VERSION);
        origin.putUUID("egg_uuid", eggUuid);
        origin.putUUID("profile_id", profileId);
        origin.putLong("hatched_at", System.currentTimeMillis());
        hatchling.getPersistentData().put("champutils_breeding_origin", origin);
    }

    public static UUID hatchOriginEggUuid(Pokemon pokemon) {
        try {
            CompoundTag origin = pokemon.getPersistentData().getCompound("champutils_breeding_origin");
            return origin.isEmpty() || !origin.hasUUID("egg_uuid") ? null : origin.getUUID("egg_uuid");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static UUID hatchOriginProfileId(Pokemon pokemon) {
        try {
            CompoundTag origin = pokemon.getPersistentData().getCompound("champutils_breeding_origin");
            return origin.isEmpty() || !origin.hasUUID("profile_id") ? null : origin.getUUID("profile_id");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String hatchStage(Pokemon egg) {
        int percent = progressPercent(egg);
        if (percent >= 100) return "It is ready to hatch!";
        if (percent >= 75) return "Sounds can be heard coming from inside!";
        if (percent >= 50) return "It appears to move occasionally.";
        if (percent >= 25) return "What will hatch from this? It doesn't seem close.";
        return "It looks as though this Egg will take a long time to hatch.";
    }

    private static CompoundTag data(Pokemon pokemon) {
        if (pokemon == null) return new CompoundTag();
        return pokemon.getPersistentData().getCompound(ROOT_KEY).copy();
    }

    private static String speciesId(Pokemon pokemon) {
        try { return pokemon.getSpecies().getResourceIdentifier().toString(); }
        catch (Throwable ignored) { return "unknown"; }
    }

    private static String typeSummary(Pokemon pokemon) {
        StringBuilder result = new StringBuilder();
        try {
            for (Object type : pokemon.getTypes()) {
                String name;
                try { name = String.valueOf(type.getClass().getMethod("getShowdownId").invoke(type)); }
                catch (Throwable ignored) { name = String.valueOf(type); }
                if (result.length() > 0) result.append('/');
                result.append(name);
            }
        } catch (Throwable ignored) {
        }
        return result.length() == 0 ? "unknown" : result.toString();
    }
}
