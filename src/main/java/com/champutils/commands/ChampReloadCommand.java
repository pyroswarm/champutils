package com.champutils.commands;

import com.champutils.config.Config;
import com.champutils.gym.GymConfig;
import com.champutils.gym.GymRegistry;
import com.champutils.profession.ProfessionConfig;
import com.champutils.profession.ProfessionLootConfig;
import com.champutils.profession.ProfessionRewardPassiveConfig;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.WildBattleLootConfig;
import com.champutils.worldevent.WorldEventConfig;
import com.champutils.worldevent.WorldEventBindingRegistry;
import com.champutils.dungeon.DungeonConfig;
import com.champutils.dungeon.DungeonKeyConfig;
import com.champutils.dungeon.DungeonTrainerConfig;
import com.champutils.dungeon.DungeonRewardConfig;
import com.champutils.dungeon.DungeonKeyDropConfig;
import com.champutils.dungeon.DungeonNativeCrateRegistry;
import com.champutils.teleport.TeleportConfig;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.File;

public class ChampReloadCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            Commands.literal("champreload")
                                    .requires(source ->
                                            source.hasPermission(4)
                                    )
                                    .executes(context -> reload(context.getSource()))
                    );
                }
        );
    }

    private static int reload(
            net.minecraft.commands.CommandSourceStack source
    ) {

        try {

            File configDir =
                    new File("config/champutils");

            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File rulesFile =
                    new File(
                            configDir,
                            "rules.json"
                    );

            Config.load(rulesFile);
            ProfessionConfig.load();
            ProfessionToolConfig.load();
            ProfessionFragmentConfig.load();
            ProfessionRewardPassiveConfig.load();
            ProfessionLootConfig.load();
            WildBattleLootConfig.load();
            WorldEventConfig.load();
            WorldEventBindingRegistry.load();
            DungeonKeyConfig.load();
            DungeonKeyDropConfig.load();
            DungeonConfig.load();
            DungeonNativeCrateRegistry.load();
            DungeonTrainerConfig.load();
            DungeonRewardConfig.load();
            GymConfig.load();
            GymRegistry.load();
            TeleportConfig.load();

            source.sendSuccess(
                    () -> Component.literal(
                            "§aChampUtils configs reloaded."
                    ),
                    true
            );

            source.sendSuccess(
                    () -> Component.literal(
                            "§7Reloaded: rules.json, professions.json, profession_tools.json, profession_fragments.json, profession_loot.json, wild_battle_loot.json, world_events.json, world_event_bindings.json, dungeon_keys.json, dungeon_key_drops.json, champ_dungeons.json, dungeon_trainers.json, dungeon_rewards.json, dungeon_native_crates.json, profession_reward_passives.json, gyms.json, gymleaders.json, teleport.json"
                    ),
                    false
            );

            source.sendSuccess(
                    () -> Component.literal(
                            "§eNote: newly added custom tool IDs still require a server restart because Minecraft item registries are created during startup. Existing tool values update now."
                    ),
                    false
            );

            return 1;

        } catch (Exception e) {

            e.printStackTrace();

            source.sendFailure(
                    Component.literal(
                            "§cFailed to reload ChampUtils configs. Check console for details."
                    )
            );

            return 0;
        }
    }
}
