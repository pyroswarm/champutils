package com.champutils.commands;

import com.champutils.antilag.AntiLagConfig;
import com.champutils.account.AccountUpgradeConfig;
import com.champutils.auction.AuctionHouseConfig;
import com.champutils.auction.AuctionHouseNpcBindingRegistry;
import com.champutils.badge.BadgeUnlockConfig;
import com.champutils.battle.ChampBattleAIConfig;
import com.champutils.chat.ChatTagConfig;
import com.champutils.claims.LandClaimConfig;
import com.champutils.claims.LandClaimRepository;
import com.champutils.config.Config;
import com.champutils.cosmetic.TitleConfig;
import com.champutils.crafting.ChampCraftingConfig;
import com.champutils.crate.CrateConfig;
import com.champutils.crate.CrateKeyCraftingConfig;
import com.champutils.dailylogin.DailyLoginConfig;
import com.champutils.dex.DexRewardConfig;
import com.champutils.economy.SellPriceConfig;
import com.champutils.emblem.EmblemConfig;
import com.champutils.expeditions.ExpeditionConfig;
import com.champutils.exploration.ExplorationLootConfig;
import com.champutils.exploration.ExplorationWorldConfig;
import com.champutils.exploration.ItemBindRegistry;
import com.champutils.gamerule.GlobalGameruleConfig;
import com.champutils.gamerule.GlobalGameruleManager;
import com.champutils.genesis.GenesisShopConfig;
import com.champutils.genesis.MegaShopConfig;
import com.champutils.guild.BossConfig;
import com.champutils.guild.GuildBuffConfig;
import com.champutils.guild.GuildConfig;
import com.champutils.gym.GymConfig;
import com.champutils.gym.GymRegistry;
import com.champutils.gym.GymRewardConfig;
import com.champutils.gym.GymSettingsConfig;
import com.champutils.hunt.PokemonHuntConfig;
import com.champutils.matchmaking.ArenaLocationConfig;
import com.champutils.megaboss.MegaBossConfig;
import com.champutils.menu.MenuNpcBindingRegistry;
import com.champutils.moderation.ModerationConfig;
import com.champutils.profession.BattleProfessionLootConfig;
import com.champutils.profession.ProfessionChunkConfig;
import com.champutils.profession.ProfessionBackpackConfig;
import com.champutils.profession.ProfessionConfig;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionGearConfig;
import com.champutils.profession.ProfessionLootConfig;
import com.champutils.profession.ProfessionRewardPassiveConfig;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionTrinketConfig;
import com.champutils.profession.ProfessionWeaponFragmentConfig;
import com.champutils.profession.WildBattleLootConfig;
import com.champutils.profile.IslanderMineConfig;
import com.champutils.profile.IslanderSpawningConfig;
import com.champutils.quest.QuestConfig;
import com.champutils.rank.RankedTokenConfig;
import com.champutils.roaming.RoamingTrainerConfig;
import com.champutils.shop.ChestShopRegistry;
import com.champutils.shop.FirstJoinKitConfig;
import com.champutils.shop.IslanderShopConfig;
import com.champutils.shop.NpcShopConfig;
import com.champutils.specialspawn.SpecialWildSpawnConfig;
import com.champutils.survival.SurvivalWorldConfig;
import com.champutils.survival.SurvivalWhitelistConfig;
import com.champutils.teleport.DefaultSpawnManager;
import com.champutils.teleport.PortalConfig;
import com.champutils.teleport.TeleportConfig;
import com.champutils.territory.TerritoryConfig;
import com.champutils.tm.TMConfig;
import com.champutils.worldborder.ChampWorldBorderConfig;
import com.champutils.worldborder.ChampWorldBorderManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChampReloadCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> dispatcher.register(
                        Commands.literal("champreload")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> reload(context.getSource()))
                )
        );
    }

    private static int reload(CommandSourceStack source) {
        List<String> reloaded = new ArrayList<>();

        try {
            File configDir = new File("config/champutils");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            reloadConfig(reloaded, "rules.json", () -> Config.load(new File(configDir, "rules.json")));

            /*
             * Intentionally NOT reloaded here:
             * - network_servers.json / NetworkServerConfig
             * - database.json / DatabaseConfig / live database connection state
             * - SQL-backed player/profile/economy/progression state files
             *
             * Those affect proxy routing, profile handoff, or live storage connections and should stay restart-only.
             */

            reloadConfig(reloaded, "account_upgrades.json", AccountUpgradeConfig::load);
            reloadConfig(reloaded, "anti_lag.json", AntiLagConfig::load);
            reloadConfig(reloaded, "arena_locations.json", ArenaLocationConfig::load);
            reloadConfig(reloaded, "auction_house.json", AuctionHouseConfig::reload);
            reloadConfig(reloaded, "auction_house_npcs.json", AuctionHouseNpcBindingRegistry::load);
            reloadConfig(reloaded, "badge_unlocks.json", BadgeUnlockConfig::load);
            reloadConfig(reloaded, "battle_ai.json", ChampBattleAIConfig::load);
            reloadConfig(reloaded, "boss_config.json", BossConfig::load);
            reloadConfig(reloaded, "chat_tags.json", ChatTagConfig::load);
            reloadConfig(reloaded, "chest_shops.json", ChestShopRegistry::load);
            reloadConfig(reloaded, "champ_crafting.json", ChampCraftingConfig::load);
            reloadConfig(reloaded, "crate_key_crafting.json", CrateKeyCraftingConfig::load);
            reloadConfig(reloaded, "crates.json", CrateConfig::load);
            reloadConfig(reloaded, "daily_login.json", DailyLoginConfig::load);
            reloadConfig(reloaded, "default_spawn.json", DefaultSpawnManager::load);
            reloadConfig(reloaded, "dex_rewards.json", DexRewardConfig::load);
            reloadConfig(reloaded, "emblems.json", EmblemConfig::load);
            reloadConfig(reloaded, "expeditions.json", ExpeditionConfig::load);
            reloadConfig(reloaded, "exploration_loot.json", ExplorationLootConfig::load);
            reloadConfig(reloaded, "exploration_worlds.json", ExplorationWorldConfig::load);
            reloadConfig(reloaded, "first_join_kit.json", FirstJoinKitConfig::load);
            reloadConfig(reloaded, "genesis_shop.json", GenesisShopConfig::load);
            reloadConfig(reloaded, "global_gamerules.json", GlobalGameruleConfig::load);
            reloadConfig(reloaded, "guild_buffs.json", GuildBuffConfig::load);
            reloadConfig(reloaded, "guild_config.json", GuildConfig::load);
            reloadConfig(reloaded, "gym_rewards.json", GymRewardConfig::load);
            reloadConfig(reloaded, "gym_settings.json", GymSettingsConfig::load);
            reloadConfig(reloaded, "gyms.json/gymleaders.json", () -> { GymConfig.load(); GymRegistry.load(); });
            reloadConfig(reloaded, "islander_mines.json", IslanderMineConfig::load);
            reloadConfig(reloaded, "islander_spawning.json", IslanderSpawningConfig::load);
            reloadConfig(reloaded, "islander_shop.json", IslanderShopConfig::load);
            reloadConfig(reloaded, "item_bindings.json", ItemBindRegistry::load);
            reloadConfig(reloaded, "land_claims.json", LandClaimConfig::load);
            reloadConfig(reloaded, "mega_bosses.json", MegaBossConfig::load);
            reloadConfig(reloaded, "mega_shop.json", MegaShopConfig::load);
            reloadConfig(reloaded, "menu_npc_bindings.json", MenuNpcBindingRegistry::load);
            reloadConfig(reloaded, "moderation.json", ModerationConfig::load);
            reloadConfig(reloaded, "npc_shops.json", NpcShopConfig::load);
            reloadConfig(reloaded, "pokemon_hunts.json", PokemonHuntConfig::load);
            reloadConfig(reloaded, "portals.json", PortalConfig::load);
            reloadConfig(reloaded, "profession_chunks.json", ProfessionChunkConfig::load);
            reloadConfig(reloaded, "profession_backpack.json", ProfessionBackpackConfig::load);
            reloadConfig(reloaded, "profession_gear.json", ProfessionGearConfig::load);
            reloadConfig(reloaded, "profession_trinkets.json", ProfessionTrinketConfig::load);
            reloadConfig(reloaded, "profession_essence.json", ProfessionFragmentConfig::load);
            reloadConfig(reloaded, "profession_loot.json", ProfessionLootConfig::load);
            reloadConfig(reloaded, "profession_reward_passives.json", ProfessionRewardPassiveConfig::load);
            reloadConfig(reloaded, "profession_tools.json", ProfessionToolConfig::load);
            reloadConfig(reloaded, "profession_weapon_essence.json", ProfessionWeaponFragmentConfig::load);
            reloadConfig(reloaded, "professions.json", ProfessionConfig::load);
            reloadConfig(reloaded, "quest_config.json", QuestConfig::load);
            reloadConfig(reloaded, "ranked_tokens.json", RankedTokenConfig::load);
            reloadConfig(reloaded, "roaming_trainers.json", RoamingTrainerConfig::load);
            reloadConfig(reloaded, "server_sell_prices.json", SellPriceConfig::load);
            reloadConfig(reloaded, "special_wild_spawns.json", SpecialWildSpawnConfig::load);
            reloadConfig(reloaded, "survival_worlds.json", SurvivalWorldConfig::load);
            reloadConfig(reloaded, "survival_whitelist.json", SurvivalWhitelistConfig::load);
            reloadConfig(reloaded, "teleport.json", TeleportConfig::load);
            reloadConfig(reloaded, "territories.json", TerritoryConfig::load);
            reloadConfig(reloaded, "titles.json", TitleConfig::load);
            reloadConfig(reloaded, "tm_config.json", TMConfig::load);
            reloadConfig(reloaded, "wild_battle_loot.json", WildBattleLootConfig::load);
            reloadConfig(reloaded, "battle_profession_loot.json", BattleProfessionLootConfig::load);
            reloadConfig(reloaded, "world_borders.json", ChampWorldBorderConfig::load);

            LandClaimRepository.refreshAll();
            ChampWorldBorderManager.applyAll(source.getServer());
            GlobalGameruleManager.applyAll(source.getServer());

            source.sendSuccess(
                    () -> Component.literal("§aChampUtils configs reloaded: §f" + reloaded.size() + "§a files/registries."),
                    true
            );
            source.sendSuccess(
                    () -> Component.literal("§7Skipped restart-only configs: network_servers.json, database.json, and live SQL/player profile state."),
                    false
            );
            source.sendSuccess(
                    () -> Component.literal("§eNote: adding brand-new registered item IDs still requires restart. Existing prices, drops, shops, gyms, bosses, rewards, and tuning values update now."),
                    false
            );
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            source.sendFailure(Component.literal("§cFailed to reload ChampUtils configs. Check console for details."));
            return 0;
        }
    }

    private static void reloadConfig(List<String> reloaded, String name, ReloadAction action) throws Exception {
        action.run();
        reloaded.add(name);
    }

    @FunctionalInterface
    private interface ReloadAction {
        void run() throws Exception;
    }
}
