package com.champutils.account;

import com.champutils.commerce.AccountCommerceRepository;
import com.champutils.cosmetic.TrailCosmeticManager;
import com.champutils.database.DatabaseManager;
import com.champutils.economy.EconomyManager;
import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AccountUpgradeMenu {
    private static final List<BoosterPack> BOOSTER_PACKS = List.of(
            new BoosterPack(1, 149_000L, Items.EMERALD),
            new BoosterPack(5, 699_000L, Items.EMERALD_BLOCK),
            new BoosterPack(10, 1_299_000L, Items.NETHER_STAR)
    );

    private static final int[] TRAIL_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final Set<UUID> TRAIL_PURCHASES_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> BOOSTER_PURCHASES_IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private AccountUpgradeMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Champs Shop"));
        MenuUtil.fillBordersForced(gui, 11, 15, 22);

        gui.setSlot(11, new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("§aBooster Credits"))
                .addLoreLine(Component.literal("§7Trade in-game Credits for account"))
                .addLoreLine(Component.literal("§7booster credits."))
                .addLoreLine(Component.literal("§7Balance: §6" + EconomyManager.format(EconomyManager.getBalance(player))))
                .addLoreLine(Component.literal("§eClick to browse"))
                .setCallback((i, c, t) -> openBoosters(player)));

        gui.setSlot(15, new GuiElementBuilder(Items.BLAZE_POWDER)
                .hideDefaultTooltip()
                .setName(Component.literal("§dParticle Trails"))
                .addLoreLine(Component.literal("§7Account-bound cosmetics."))
                .addLoreLine(Component.literal("§7Move around to show your active trail."))
                .addLoreLine(Component.literal("§eClick to browse"))
                .setCallback((i, c, t) -> openTrails(player, 0)));

        MenuUtil.addBackButton(gui, 22, () -> com.champutils.menu.MainMenu.open(player));
        gui.open();
    }

    public static void openBoosters(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Champs Shop - Boosters"));
        MenuUtil.fillBordersForced(gui, 11, 13, 15, 22);

        int[] slots = {11, 13, 15};
        for (int i = 0; i < BOOSTER_PACKS.size(); i++) {
            addBoosterPack(gui, player, slots[i], BOOSTER_PACKS.get(i));
        }

        MenuUtil.addBackButton(gui, 22, () -> open(player));
        gui.open();
    }

    public static void openTrails(ServerPlayer player, int requestedPage) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Champs Shop - Trails"));
        MenuUtil.fillBordersForced(gui, 10, 11, 12, 13, 14, 15, 16, 22);

        Set<String> owned = TrailCosmeticManager.unlocked(player);
        String selected = TrailCosmeticManager.selected(player);
        List<TrailCosmeticManager.TrailDef> trails = TrailCosmeticManager.trails();
        for (int i = 0; i < Math.min(TRAIL_SLOTS.length, trails.size()); i++) {
            addTrail(gui, player, TRAIL_SLOTS[i], trails.get(i), owned, selected);
        }

        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("§7Disable Trail"))
                .addLoreLine(Component.literal("§eClick to turn your trail off"))
                .setCallback((i, c, t) -> {
                    TrailCosmeticManager.select(player, "off");
                    openTrails(player, requestedPage);
                }));
        MenuUtil.addBackButton(gui, 18, () -> open(player));
        gui.open();
    }

    private static void addBoosterPack(SimpleGui gui, ServerPlayer player, int slot, BoosterPack pack) {
        long price = EconomyManager.wholeCreditsToCents(pack.priceCredits());
        boolean affordable = EconomyManager.getBalance(player) >= price;
        GuiElementBuilder builder = new GuiElementBuilder(pack.icon())
                .hideDefaultTooltip()
                .setName(Component.literal("§a" + pack.amount() + " Booster Credit" + (pack.amount() == 1 ? "" : "s")))
                .addLoreLine(Component.literal("§7Cost: §6" + EconomyManager.format(price)))
                .addLoreLine(Component.literal("§7Account-bound balance."))
                .addLoreLine(Component.literal("§8Reference rate: $1 = 100,000 Credits."))
                .addLoreLine(Component.literal(affordable ? "§eClick to purchase" : "§cYou cannot afford this yet."));
        if (affordable) {
            builder.setCallback((i, c, t) -> purchaseBoosterPack(player, pack));
        }
        gui.setSlot(slot, builder);
    }

    private static void addTrail(SimpleGui gui, ServerPlayer player, int slot, TrailCosmeticManager.TrailDef trail, Set<String> owned, String selected) {
        boolean has = owned.contains(trail.id());
        boolean active = trail.id().equals(selected);
        long price = trailPrice(trail.id());
        boolean affordable = EconomyManager.getBalance(player) >= price;
        GuiElementBuilder builder = new GuiElementBuilder(trail.icon())
                .hideDefaultTooltip()
                .setName(Component.literal((active ? "§a" : has ? "§d" : "§6") + trail.displayName()))
                .addLoreLine(Component.literal("§7" + trail.description()))
                .addLoreLine(Component.literal("§7Scope: §fAccount"))
                .addLoreLine(Component.literal(has ? active ? "§aEquipped." : "§eUnlocked. Click to equip." : "§7Cost: §6" + EconomyManager.format(price)));
        if (!has) {
            builder.addLoreLine(Component.literal(affordable ? "§eClick to purchase" : "§cYou cannot afford this yet."));
        }
        builder.setCallback((i, c, t) -> {
            if (TRAIL_PURCHASES_IN_FLIGHT.contains(player.getUUID())) {
                openTrailLoading(player, trail.displayName());
                return;
            }
            if (has) {
                TrailCosmeticManager.select(player, trail.id());
                openTrails(player, 0);
            } else if (affordable) {
                purchaseTrail(player, trail);
            }
        });
        gui.setSlot(slot, builder);
    }

    private static void purchaseBoosterPack(ServerPlayer player, BoosterPack pack) {
        UUID uuid = player.getUUID();
        if (!BOOSTER_PURCHASES_IN_FLIGHT.add(uuid)) {
            openPurchaseLoading(player, "Booster Credits");
            return;
        }
        long price = EconomyManager.wholeCreditsToCents(pack.priceCredits());
        openPurchaseLoading(player, pack.amount() + " Booster Credit" + (pack.amount() == 1 ? "" : "s"));
        DatabaseManager.supplyAsync("champs shop booster withdraw", connection -> EconomyManager.withdraw(player, price, "Champs Shop booster credits x" + pack.amount()))
                .whenComplete((withdrawn, withdrawError) -> player.server.execute(() -> {
                    if (withdrawError != null || withdrawn == null || !withdrawn.success) {
                        BOOSTER_PURCHASES_IN_FLIGHT.remove(uuid);
                        if (withdrawError != null) withdrawError.printStackTrace();
                        player.sendSystemMessage(Component.literal(withdrawn == null || withdrawn.error == null ? "You cannot afford that pack." : withdrawn.error).withStyle(ChatFormatting.RED));
                        openBoosters(player);
                        return;
                    }

                    String reference = "champsshop:booster:" + player.getUUID() + ":" + System.currentTimeMillis();
                    AccountCommerceRepository.ResolvedAccount account = new AccountCommerceRepository.ResolvedAccount(player.getUUID(), player.getGameProfile().getName(), true);
                    AccountCommerceRepository.grantBoosterCreditsAsync(account, pack.amount(), "IN_GAME", reference, "Purchased in Champs Shop")
                            .thenAccept(result -> player.server.execute(() -> {
                                BOOSTER_PURCHASES_IN_FLIGHT.remove(uuid);
                                if (result == null || result.account() == null) {
                                    refundAsync(player, price, "Refund failed Champs Shop booster purchase");
                                    player.sendSystemMessage(Component.literal("Purchase failed. Credits were refunded.").withStyle(ChatFormatting.RED));
                                    openBoosters(player);
                                    return;
                                }
                                com.champutils.cashshop.BoosterCreditManager.setCachedPurchasedCredits(player.getUUID(), result.balance());
                                player.sendSystemMessage(Component.literal("Purchased " + pack.amount() + " account booster credit(s).").withStyle(ChatFormatting.GREEN));
                                openBoosters(player);
                            }))
                            .exceptionally(error -> {
                                player.server.execute(() -> {
                                    BOOSTER_PURCHASES_IN_FLIGHT.remove(uuid);
                                    refundAsync(player, price, "Refund failed Champs Shop booster purchase");
                                    player.sendSystemMessage(Component.literal("Purchase failed. Credits were refunded.").withStyle(ChatFormatting.RED));
                                    openBoosters(player);
                                });
                                return null;
                            });
                }));
    }

    private static void purchaseTrail(ServerPlayer player, TrailCosmeticManager.TrailDef trail) {
        UUID uuid = player.getUUID();
        if (!TRAIL_PURCHASES_IN_FLIGHT.add(uuid)) {
            openTrailLoading(player, trail.displayName());
            return;
        }
        if (TrailCosmeticManager.owns(player, trail.id())) {
            TRAIL_PURCHASES_IN_FLIGHT.remove(uuid);
            TrailCosmeticManager.select(player, trail.id());
            openTrails(player, 0);
            return;
        }
        long price = trailPrice(trail.id());
        openTrailLoading(player, trail.displayName());
        DatabaseManager.supplyAsync("champs shop trail withdraw", connection -> EconomyManager.withdraw(player, price, "Champs Shop trail: " + trail.id()))
                .whenComplete((withdrawn, withdrawError) -> player.server.execute(() -> {
                    if (withdrawError != null || withdrawn == null || !withdrawn.success) {
                        TRAIL_PURCHASES_IN_FLIGHT.remove(uuid);
                        if (withdrawError != null) withdrawError.printStackTrace();
                        player.sendSystemMessage(Component.literal(withdrawn == null || withdrawn.error == null ? "You cannot afford that trail." : withdrawn.error).withStyle(ChatFormatting.RED));
                        openTrails(player, 0);
                        return;
                    }

                    String reference = "champsshop:trail:" + trail.id() + ":" + player.getUUID() + ":" + System.currentTimeMillis();
                    AccountCommerceRepository.ResolvedAccount account = new AccountCommerceRepository.ResolvedAccount(player.getUUID(), player.getGameProfile().getName(), true);
                    AccountCommerceRepository.unlockCosmeticAsync(account, TrailCosmeticManager.COSMETIC_TYPE, trail.id(), "IN_GAME", reference, "Purchased in Champs Shop")
                            .thenAccept(result -> player.server.execute(() -> {
                                TRAIL_PURCHASES_IN_FLIGHT.remove(uuid);
                                if (result == null || result.account() == null) {
                                    refundAsync(player, price, "Refund failed Champs Shop trail purchase");
                                    player.sendSystemMessage(Component.literal("Purchase failed. Credits were refunded.").withStyle(ChatFormatting.RED));
                                    openTrails(player, 0);
                                    return;
                                }
                                TrailCosmeticManager.addUnlocked(player.getUUID(), trail.id());
                                TrailCosmeticManager.select(player, trail.id());
                                player.sendSystemMessage(Component.literal("Unlocked " + trail.displayName() + ".").withStyle(ChatFormatting.GREEN));
                                openTrails(player, 0);
                            }))
                            .exceptionally(error -> {
                                player.server.execute(() -> {
                                    TRAIL_PURCHASES_IN_FLIGHT.remove(uuid);
                                    refundAsync(player, price, "Refund failed Champs Shop trail purchase");
                                    player.sendSystemMessage(Component.literal("Purchase failed. Credits were refunded.").withStyle(ChatFormatting.RED));
                                    openTrails(player, 0);
                                });
                                return null;
                            });
                }));
    }

    private static void refundAsync(ServerPlayer player, long price, String reason) {
        if (player == null || price <= 0L) return;
        DatabaseManager.runAsync("champs shop refund", connection -> EconomyManager.deposit(player, price, reason))
                .exceptionally(error -> { error.printStackTrace(); return null; });
    }

    private static void openPurchaseLoading(ServerPlayer player, String itemName) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Champs Shop - Processing"));
        MenuUtil.fillBordersForced(gui, 13);
        gui.setSlot(13, new GuiElementBuilder(Items.CLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("§eProcessing purchase..."))
                .addLoreLine(Component.literal("§7Item: §f" + itemName))
                .addLoreLine(Component.literal("§7Please wait. Do not click another item."))
                .addLoreLine(Component.literal("§8Your Credits will be refunded if this fails.")));
        gui.open();
    }

    private static void openTrailLoading(ServerPlayer player, String trailName) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Champs Shop - Processing"));
        MenuUtil.fillBordersForced(gui, 13);
        gui.setSlot(13, new GuiElementBuilder(Items.CLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("§eProcessing purchase..."))
                .addLoreLine(Component.literal("§7Unlocking: §f" + trailName))
                .addLoreLine(Component.literal("§7Please wait. Do not click another trail."))
                .addLoreLine(Component.literal("§8Your Credits will be refunded if this fails.")));
        gui.open();
    }

    private static long trailPrice(String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
        long credits = switch (normalized) {
            case "starlight" -> 399_000L;
            case "shadow", "frost" -> 299_000L;
            default -> 199_000L;
        };
        return EconomyManager.wholeCreditsToCents(credits);
    }

    private record BoosterPack(int amount, long priceCredits, Item icon) {}
}
