package com.champutils.profile;

import com.champutils.auction.AuctionHouseService;
import com.champutils.shop.ShopPokemonCrateOpeningGui;
import com.champutils.economy.EconomyManager;
import com.champutils.guild.GuildRepository;
import com.champutils.battle.DisconnectForfeitManager;
import com.champutils.notifications.NotificationManager;
import com.champutils.party.PartyManager;
import com.champutils.profession.ProfessionDataManager;
import com.champutils.quest.QuestManager;
import com.champutils.shop.FirstJoinKitManager;
import com.champutils.wondertrade.WonderTradeSeeder;
import com.champutils.chat.ChatPreferenceManager;
import com.champutils.moderation.ModerationManager;
import com.champutils.dailylogin.DailyLoginManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * Initializes systems that are safe to start only after a real profile is selected.
 */
public final class ProfileSessionLoader {
    private ProfileSessionLoader() {}

    public static void load(ServerPlayer player) {
        if (player == null) return;
        String playerName = player.getName().getString();

        PlayerDataManager.ensurePlayer(player.getUUID(), playerName);
        EconomyManager.ensurePlayer(player);
        ProfessionDataManager.ensurePlayer(player.getUUID(), playerName);

        int storedRp = PlayerDataManager.getRp(player.getUUID(), playerName);
        ProfileManager.setElo(player, storedRp);

        DisconnectForfeitManager.handleJoin(player);
        NotificationManager.handleJoin(player);
        FirstJoinKitManager.handleJoin(player);
        QuestManager.handleJoin(player);
        WonderTradeSeeder.handleJoin(player);
        ShopPokemonCrateOpeningGui.handleJoin(player);
        AuctionHouseService.handleJoin(player);
        GuildRepository.loadForPlayer(player.getUUID(), playerName);
        ChatPreferenceManager.load(player);
        ModerationManager.handleJoin(player);
        DailyLoginManager.handleJoin(player);
    }

    public static void unload(ServerPlayer player) {
        if (player == null) return;
        ChatPreferenceManager.save(player);
        ShopPokemonCrateOpeningGui.handleDisconnect(player);
        com.champutils.profession.ProfessionManager.unloadPlayer(player);
        QuestManager.unloadPlayer(player);
        PartyManager.handleDisconnect(player);
        DailyLoginManager.handleDisconnect(player);
    }
}
