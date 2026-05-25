package com.champutils.moderation;

import com.champutils.time.DailyResetManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Evidence-based xray detector.
 *
 * Evidence-based and conservative. It alerts staff for review early, and only escalates AutoMod
 * when the score is very high and several independent signals agree:
 * hidden valuable ores, abnormal ore density, repeated direct-to-ore behavior, streaks, and low mining context.
 */
public final class XrayDetectionManager {
    private static final Map<UUID, PlayerWindow> windows = new HashMap<>();
    private static long resetKey = DailyResetManager.currentResetKeyMillis();

    private XrayDetectionManager() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer sp && world instanceof ServerLevel level) {
                record(sp, level, pos, state);
            }
        });
    }

    public static void dailyReset() {
        windows.clear();
        resetKey = DailyResetManager.currentResetKeyMillis();
    }

    private static void record(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (!ModerationConfig.DATA.xrayDetectionEnabled) return;
        if (player.hasPermissions(4) && ModerationConfig.DATA.xrayIgnoreOps) return;

        long key = DailyResetManager.currentResetKeyMillis();
        if (key != resetKey) dailyReset();

        String blockId = blockId(state);
        long now = System.currentTimeMillis();
        PlayerWindow window = windows.computeIfAbsent(player.getUUID(), id -> new PlayerWindow());
        window.prune(now, windowMillis());

        boolean trackedOre = isTrackedOre(blockId);
        boolean valuableOre = isValuableOre(blockId);
        boolean commonMiningBlock = isCommonMiningBlock(blockId);

        if (!trackedOre && !commonMiningBlock) return;

        int exposedFaces = countExposedFaces(level, pos);
        boolean hidden = exposedFaces == 0;
        Sample sample = new Sample(now, blockId, pos.getX(), pos.getY(), pos.getZ(), level.dimension().location().toString(), trackedOre, valuableOre, hidden);
        window.samples.addLast(sample);

        if (commonMiningBlock) window.commonBlocks++;
        if (trackedOre) window.trackedOres++;
        if (valuableOre) {
            window.valuableOres++;
            if (hidden) window.hiddenValuableOres++;
            if (isDiamond(blockId)) {
                window.diamonds++;
                if (pos.getY() <= ModerationConfig.DATA.xrayMinYForDiamondAlert) window.deepDiamonds++;
                if (hidden) window.hiddenDiamonds++;
            } else if (isAncientDebris(blockId)) {
                window.ancientDebris++;
                if (hidden) window.hiddenDebris++;
            }
        } else if (isIron(blockId)) {
            window.iron++;
            if (hidden) window.hiddenIron++;
        } else if (isGold(blockId)) {
            window.gold++;
            if (hidden) window.hiddenGold++;
        }

        evaluate(player, window, now);
    }

    private static void evaluate(ServerPlayer player, PlayerWindow window, long now) {
        if (now < window.nextAlertAt) return;

        int totalBreaks = window.samples.size();
        if (totalBreaks < ModerationConfig.DATA.xrayMinBlocksMinedForAlert) return;
        if (window.valuableOres < ModerationConfig.DATA.xrayMinValuableOresForAlert) return;

        double valuableDensity = ratio(window.valuableOres, totalBreaks);
        double hiddenValuableDensity = ratio(window.hiddenValuableOres, Math.max(1, window.valuableOres));
        int directHiddenRuns = countDirectHiddenRuns(window.samples);
        int closeClusters = countCloseValuableClusters(window.samples);
        int dimensionHops = countDimensionChanges(window.samples);

        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (window.deepDiamonds >= ModerationConfig.DATA.xrayDiamondThreshold) {
            score += 18;
            reasons.add("deepDiamonds=" + window.deepDiamonds);
        }
        if (window.ancientDebris >= ModerationConfig.DATA.xrayAncientDebrisThreshold) {
            score += 22;
            reasons.add("ancientDebris=" + window.ancientDebris);
        }
        if (window.hiddenValuableOres >= ModerationConfig.DATA.xrayHiddenValuableOreThreshold) {
            score += 24;
            reasons.add("hiddenValuableOres=" + window.hiddenValuableOres);
        }
        if (valuableDensity >= ModerationConfig.DATA.xrayValuableOreDensityAlertRatio) {
            score += 18;
            reasons.add("valuableDensity=" + percent(valuableDensity));
        }
        if (hiddenValuableDensity >= ModerationConfig.DATA.xrayHiddenOreRatioAlert) {
            score += 16;
            reasons.add("hiddenRatio=" + percent(hiddenValuableDensity));
        }
        if (directHiddenRuns >= ModerationConfig.DATA.xrayDirectHiddenOreRunThreshold) {
            score += 20;
            reasons.add("directHiddenRuns=" + directHiddenRuns);
        }
        if (closeClusters >= ModerationConfig.DATA.xrayCloseOreClusterThreshold) {
            score += 10;
            reasons.add("closeOreClusters=" + closeClusters);
        }
        if (dimensionHops >= 2 && window.ancientDebris >= Math.max(4, ModerationConfig.DATA.xrayAncientDebrisThreshold / 2)) {
            score += 8;
            reasons.add("dimensionHops=" + dimensionHops);
        }

        // Strong false-positive guard: do not alert from one lucky vein or one short mining burst.
        if (score < ModerationConfig.DATA.xrayAlertScoreThreshold) return;
        if (reasons.size() < ModerationConfig.DATA.xrayMinIndependentSignals) return;

        window.nextAlertAt = now + Math.max(1, ModerationConfig.DATA.xrayAlertCooldownMinutes) * 60_000L;
        window.alerts++;

        String name = player.getGameProfile().getName();
        String message = "§6[Xray] Review recommended for §f" + name
                + "§7 score=§e" + score
                + "§7 signals=§e" + reasons.size()
                + "§7 window=§e" + ModerationConfig.DATA.xrayWindowMinutes + "m"
                + "§7 breaks=§e" + totalBreaks
                + "§7 diamonds=§b" + window.diamonds
                + "§7 deep=§b" + window.deepDiamonds
                + "§7 debris=§6" + window.ancientDebris
                + "§7 iron=§f" + window.iron
                + "§7 gold=§e" + window.gold
                + "§7 hiddenValuable=§c" + window.hiddenValuableOres
                + "§7 reasons=§f" + String.join(", ", reasons)
                + "§7 autoActionEligible=§e" + (ModerationConfig.DATA.xrayAutoPunishEnabled && score >= ModerationConfig.DATA.xrayAutoPunishScoreThreshold && reasons.size() >= ModerationConfig.DATA.xrayAutoPunishMinIndependentSignals);

        ModerationManager.alertAdmins(player.server, message);
        ModerationManager.webhook("Xray review: " + name + " score=" + score + " signals=" + reasons.size()
                + " breaks=" + totalBreaks + " diamonds=" + window.diamonds + " deep=" + window.deepDiamonds
                + " debris=" + window.ancientDebris + " iron=" + window.iron + " gold=" + window.gold
                + " hiddenValuable=" + window.hiddenValuableOres + " reasons=" + String.join(", ", reasons));

        boolean actionEligible = ModerationConfig.DATA.xrayAutoPunishEnabled
                && score >= ModerationConfig.DATA.xrayAutoPunishScoreThreshold
                && reasons.size() >= ModerationConfig.DATA.xrayAutoPunishMinIndependentSignals;

        if (actionEligible) {
            ModerationManager.systemViolation(player, "Xray", "very high confidence mining pattern score=" + score + " signals=" + reasons.size() + " reasons=" + String.join(", ", reasons), true);
        } else if (ModerationConfig.DATA.xrayNotifyPlayerOnStaffAlert) {
            player.sendSystemMessage(Component.literal("Your mining pattern was sent to staff for review. No punishment was applied automatically.").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static int countDirectHiddenRuns(Deque<Sample> samples) {
        int runs = 0;
        Sample previousValuable = null;
        for (Sample sample : samples) {
            if (!sample.valuableOre || !sample.hidden) continue;
            if (previousValuable != null) {
                long dt = sample.time - previousValuable.time;
                double dist = distance(sample, previousValuable);
                if (dt <= ModerationConfig.DATA.xrayDirectOreSeconds * 1000L && dist <= ModerationConfig.DATA.xrayDirectOreMaxDistance) {
                    runs++;
                }
            }
            previousValuable = sample;
        }
        return runs;
    }

    private static int countCloseValuableClusters(Deque<Sample> samples) {
        List<Sample> ores = samples.stream().filter(s -> s.valuableOre).toList();
        int clusters = 0;
        double max = ModerationConfig.DATA.xrayCloseOreClusterDistance;
        for (int i = 0; i < ores.size(); i++) {
            int nearby = 0;
            for (Sample other : ores) {
                if (ores.get(i) == other) continue;
                if (ores.get(i).dimension.equals(other.dimension) && distance(ores.get(i), other) <= max) nearby++;
            }
            if (nearby >= 2) clusters++;
        }
        return clusters;
    }

    private static int countDimensionChanges(Deque<Sample> samples) {
        int changes = 0;
        String last = null;
        for (Sample sample : samples) {
            if (last != null && !last.equals(sample.dimension)) changes++;
            last = sample.dimension;
        }
        return changes;
    }

    private static int countExposedFaces(ServerLevel level, BlockPos pos) {
        int exposed = 0;
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.isAir() || !neighbor.getFluidState().isEmpty()) exposed++;
        }
        return exposed;
    }

    private static boolean isTrackedOre(String id) {
        return ModerationConfig.DATA.xrayOreIds.contains(id) || isIron(id) || isGold(id);
    }

    private static boolean isValuableOre(String id) {
        return isDiamond(id) || isAncientDebris(id) || isConfiguredCobblemonRareOre(id);
    }

    private static boolean isConfiguredCobblemonRareOre(String id) {
        return ModerationConfig.DATA.xrayOreIds.contains(id) && id.startsWith("cobblemon:");
    }

    private static boolean isDiamond(String id) { return id.equals("minecraft:diamond_ore") || id.equals("minecraft:deepslate_diamond_ore"); }
    private static boolean isAncientDebris(String id) { return id.equals("minecraft:ancient_debris"); }
    private static boolean isIron(String id) { return id.equals("minecraft:iron_ore") || id.equals("minecraft:deepslate_iron_ore"); }
    private static boolean isGold(String id) { return id.equals("minecraft:gold_ore") || id.equals("minecraft:deepslate_gold_ore") || id.equals("minecraft:nether_gold_ore"); }

    private static boolean isCommonMiningBlock(String id) {
        return id.equals("minecraft:stone") || id.equals("minecraft:deepslate") || id.equals("minecraft:netherrack")
                || id.equals("minecraft:tuff") || id.equals("minecraft:calcite") || id.equals("minecraft:granite")
                || id.equals("minecraft:diorite") || id.equals("minecraft:andesite") || id.equals("minecraft:basalt")
                || id.equals("minecraft:blackstone");
    }

    private static String blockId(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        return key.toString();
    }

    private static long windowMillis() { return Math.max(1, ModerationConfig.DATA.xrayWindowMinutes) * 60_000L; }
    private static double ratio(int a, int b) { return b <= 0 ? 0.0 : (double) a / (double) b; }
    private static String percent(double value) { return String.format(Locale.ROOT, "%.1f%%", value * 100.0); }
    private static double distance(Sample a, Sample b) {
        long dx = (long) a.x - b.x;
        long dy = (long) a.y - b.y;
        long dz = (long) a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static final class PlayerWindow {
        final Deque<Sample> samples = new ArrayDeque<>();
        int commonBlocks;
        int trackedOres;
        int valuableOres;
        int hiddenValuableOres;
        int diamonds;
        int deepDiamonds;
        int hiddenDiamonds;
        int ancientDebris;
        int hiddenDebris;
        int iron;
        int hiddenIron;
        int gold;
        int hiddenGold;
        int alerts;
        long nextAlertAt;

        void prune(long now, long windowMillis) {
            long cutoff = now - windowMillis;
            while (!samples.isEmpty() && samples.peekFirst().time < cutoff) {
                Sample removed = samples.removeFirst();
                if (isCommonMiningBlock(removed.blockId)) commonBlocks--;
                if (removed.trackedOre) trackedOres--;
                if (removed.valuableOre) {
                    valuableOres--;
                    if (removed.hidden) hiddenValuableOres--;
                    if (isDiamond(removed.blockId)) {
                        diamonds--;
                        if (removed.y <= ModerationConfig.DATA.xrayMinYForDiamondAlert) deepDiamonds--;
                        if (removed.hidden) hiddenDiamonds--;
                    } else if (isAncientDebris(removed.blockId)) {
                        ancientDebris--;
                        if (removed.hidden) hiddenDebris--;
                    }
                } else if (isIron(removed.blockId)) {
                    iron--;
                    if (removed.hidden) hiddenIron--;
                } else if (isGold(removed.blockId)) {
                    gold--;
                    if (removed.hidden) hiddenGold--;
                }
            }
        }
    }

    private record Sample(long time, String blockId, int x, int y, int z, String dimension, boolean trackedOre, boolean valuableOre, boolean hidden) {}
}
