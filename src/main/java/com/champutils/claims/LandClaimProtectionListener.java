package com.champutils.claims;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LandClaimProtectionListener {
    private static final Set<UUID> BORDER_VIEWERS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, Long> LAST_DENY_MESSAGE_MS = new ConcurrentHashMap<>();
    private static final long DENY_MESSAGE_COOLDOWN_MS = 5_000L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File BORDER_PREF_FILE = new File("config/champutils/claim_border_viewers.json");

    private LandClaimProtectionListener() {}

    public static synchronized boolean toggleBorder(ServerPlayer player) {
        if (player == null) return false;
        loadBorderPrefs();
        UUID uuid = player.getUUID();
        boolean enabled;
        if (BORDER_VIEWERS.remove(uuid)) {
            enabled = false;
        } else {
            BORDER_VIEWERS.add(uuid);
            enabled = true;
        }
        saveBorderPrefs();
        return enabled;
    }

    public static synchronized void loadBorderPrefs() {
        try {
            if (!BORDER_PREF_FILE.exists()) return;
            try (FileReader reader = new FileReader(BORDER_PREF_FILE)) {
                String[] values = GSON.fromJson(reader, String[].class);
                BORDER_VIEWERS.clear();
                if (values != null) {
                    for (String value : values) {
                        try { BORDER_VIEWERS.add(UUID.fromString(value)); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static synchronized void saveBorderPrefs() {
        try {
            File parent = BORDER_PREF_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Set<String> values = new HashSet<>();
            for (UUID uuid : BORDER_VIEWERS) values.add(uuid.toString());
            try (FileWriter writer = new FileWriter(BORDER_PREF_FILE)) {
                GSON.toJson(values, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void register() {
        loadBorderPrefs();
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return true;
            LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, pos);
            if (claim == null || LandClaimRepository.canBuild(serverPlayer, claim)) return true;
            deny(serverPlayer, "You cannot break blocks in " + claim.ownerName + "'s claim.");
            return false;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            BlockPos targetPos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(targetPos);
            ItemStack stack = serverPlayer.getItemInHand(hand);
            BlockPos placedPos = targetPos.relative(hitResult.getDirection());

            LandClaimRepository.Claim targetClaim = LandClaimRepository.findAt(level, targetPos);
            LandClaimRepository.Claim placedClaim = LandClaimRepository.findAt(level, placedPos);
            LandClaimRepository.Claim claim = placedClaim != null ? placedClaim : targetClaim;
            if (claim == null) return InteractionResult.PASS;

            if (!LandClaimRepository.canEnter(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot interact in " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            if (isDangerousTransportItem(stack) && !LandClaimRepository.canBuild(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot place item-transfer blocks or hopper minecarts into " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            if ((stack.getItem() instanceof BucketItem || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) && !LandClaimRepository.canBuild(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot place fluids or fire in " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            if (stack.getItem() instanceof BlockItem && !LandClaimRepository.canBuild(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot place blocks in " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            if (state.getBlock() instanceof BedBlock && !LandClaimRepository.canBuild(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot sleep in " + claim.ownerName + "'s bed.");
                return InteractionResult.FAIL;
            }

            if (isContainer(level, targetPos, state) && !LandClaimRepository.canOpenContainers(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot open containers in " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            if (isRedstoneOrDoor(state) && !LandClaimRepository.canUseRedstone(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot use switches, doors, gates, hoppers, or redstone in " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, entity.blockPosition());
            if (claim == null || LandClaimRepository.canInteractEntities(serverPlayer, claim) || (entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart && LandClaimRepository.canBuild(serverPlayer, claim))) return InteractionResult.PASS;
            deny(serverPlayer, "You cannot interact with entities in " + claim.ownerName + "'s claim.");
            return InteractionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, entity.blockPosition());
            if (claim == null || LandClaimRepository.canInteractEntities(serverPlayer, claim)) return InteractionResult.PASS;
            deny(serverPlayer, "You cannot attack entities in " + claim.ownerName + "'s claim.");
            return InteractionResult.FAIL;
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(world instanceof ServerLevel level)) return;
            LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, entity.blockPosition());
            if (claim != null && isDangerousEntity(entity) && !(entity instanceof net.minecraft.world.entity.vehicle.AbstractMinecart)) {
                entity.discard();
            }
        });
    }


    public static boolean canPlaceBlock(ServerPlayer player, ServerLevel level, BlockPos placedPos) {
        if (player == null || level == null || placedPos == null) return true;
        LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, placedPos);
        if (claim == null || LandClaimRepository.canBuild(player, claim)) return true;
        deny(player, "You cannot place blocks in " + claim.ownerName + "'s claim.");
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;

        // Keep the tick hook light. Entry denial only scans online players every half second.
        if (server.getTickCount() % 10 == 0) enforceEntrySettings(server);
        if (server.getTickCount() % 100 == 0) renderBorders(server);
    }

    private static void enforceEntrySettings(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.isSpectator()) continue;
            ServerLevel level = player.serverLevel();
            LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, player.blockPosition());
            if (claim == null || LandClaimRepository.canEnter(player, claim)) continue;
            double x = player.getX();
            double z = player.getZ();
            double left = Math.abs(x - (claim.minX - 0.75D));
            double right = Math.abs(x - (claim.maxX + 1.75D));
            double north = Math.abs(z - (claim.minZ - 0.75D));
            double south = Math.abs(z - (claim.maxZ + 1.75D));
            double min = Math.min(Math.min(left, right), Math.min(north, south));
            if (min == left) x = claim.minX - 0.75D;
            else if (min == right) x = claim.maxX + 1.75D;
            else if (min == north) z = claim.minZ - 0.75D;
            else z = claim.maxZ + 1.75D;
            if (!level.getWorldBorder().isWithinBounds(new BlockPos((int)Math.floor(x), player.blockPosition().getY(), (int)Math.floor(z)))) {
                x = level.getSharedSpawnPos().getX() + 0.5D;
                z = level.getSharedSpawnPos().getZ() + 0.5D;
            }
            player.teleportTo(level, x, player.getY(), z, player.getYRot(), player.getXRot());
            deny(player, "You cannot enter " + claim.ownerName + "'s claim.");
        }
    }

    /**
     * Used by the fluid-flow mixin to stop lava/water at claim borders before it enters.
     */
    public static boolean shouldBlockFluidFlow(ServerLevel level, BlockPos fromPos, BlockPos toPos) {
        if (level == null || fromPos == null || toPos == null) return false;
        LandClaimRepository.Claim targetClaim = LandClaimRepository.findAt(level, toPos);
        if (targetClaim == null) return false;
        LandClaimRepository.Claim sourceClaim = LandClaimRepository.findAt(level, fromPos);
        return sourceClaim == null || sourceClaim.id == null || !sourceClaim.id.equals(targetClaim.id);
    }

    private static void renderBorders(MinecraftServer server) {
        int view = LandClaimConfig.borderViewDistanceBlocks();
        int step = LandClaimConfig.borderParticleStepBlocks();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!BORDER_VIEWERS.contains(player.getUUID())) continue;
            ServerLevel level = player.serverLevel();
            int y = Math.max(level.getMinBuildHeight() + 1, Math.min(level.getMaxBuildHeight() - 1, player.blockPosition().getY() + 1));
            int particleBudget = 800;
            for (LandClaimRepository.Claim claim : LandClaimRepository.allCached()) {
                if (particleBudget <= 0) break;
                if (claim == null || !claim.worldName.equalsIgnoreCase(level.dimension().location().toString())) continue;
                if (!LandClaimRepository.isOwner(player, claim) && !LandClaimRepository.isMember(player, claim)) continue;
                if (!isClaimNearPlayer(player, claim, view)) continue;
                for (int x = claim.minX; x <= claim.maxX && particleBudget > 0; x += step) {
                    if (spawnBorderParticle(player, x, y, claim.minZ, view)) particleBudget--;
                    if (spawnBorderParticle(player, x, y, claim.maxZ, view)) particleBudget--;
                }
                if (spawnBorderParticle(player, claim.maxX, y, claim.minZ, view)) particleBudget--;
                if (spawnBorderParticle(player, claim.maxX, y, claim.maxZ, view)) particleBudget--;
                for (int z = claim.minZ; z <= claim.maxZ && particleBudget > 0; z += step) {
                    if (spawnBorderParticle(player, claim.minX, y, z, view)) particleBudget--;
                    if (spawnBorderParticle(player, claim.maxX, y, z, view)) particleBudget--;
                }
                if (spawnBorderParticle(player, claim.minX, y, claim.maxZ, view)) particleBudget--;
                if (spawnBorderParticle(player, claim.maxX, y, claim.maxZ, view)) particleBudget--;
            }
        }
    }

    private static boolean isClaimNearPlayer(ServerPlayer player, LandClaimRepository.Claim claim, int view) {
        int px = player.blockPosition().getX();
        int pz = player.blockPosition().getZ();
        int nearestX = Math.max(claim.minX, Math.min(px, claim.maxX));
        int nearestZ = Math.max(claim.minZ, Math.min(pz, claim.maxZ));
        long dx = (long) px - nearestX;
        long dz = (long) pz - nearestZ;
        return dx * dx + dz * dz <= (long) view * view;
    }

    private static boolean spawnBorderParticle(ServerPlayer player, int x, int y, int z, int view) {
        if (player.distanceToSqr(x + 0.5D, y + 0.5D, z + 0.5D) > (double) view * view) return false;
        player.serverLevel().sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, x + 0.5D, y + 0.2D, z + 0.5D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        return true;
    }

    private static void sealBorderAgainstGrief(ServerLevel level, LandClaimRepository.Claim claim) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        for (int x = claim.minX; x <= claim.maxX; x++) {
            cleanColumnIfOutsideThreat(level, claim, new BlockPos(x, minY, claim.minZ), 0, -1, minY, maxY);
            cleanColumnIfOutsideThreat(level, claim, new BlockPos(x, minY, claim.maxZ), 0, 1, minY, maxY);
        }
        for (int z = claim.minZ; z <= claim.maxZ; z++) {
            cleanColumnIfOutsideThreat(level, claim, new BlockPos(claim.minX, minY, z), -1, 0, minY, maxY);
            cleanColumnIfOutsideThreat(level, claim, new BlockPos(claim.maxX, minY, z), 1, 0, minY, maxY);
        }
    }

    private static void cleanColumnIfOutsideThreat(ServerLevel level, LandClaimRepository.Claim claim, BlockPos base, int dx, int dz, int minY, int maxY) {
        for (int y = minY; y <= maxY; y++) {
            BlockPos inside = new BlockPos(base.getX(), y, base.getZ());
            BlockPos outside = inside.offset(dx, 0, dz);
            BlockState insideState = level.getBlockState(inside);
            BlockState outsideState = level.getBlockState(outside);
            boolean outsideThreat = outsideState.getFluidState().is(FluidTags.LAVA) || outsideState.getFluidState().is(FluidTags.WATER) || outsideState.getBlock() instanceof FireBlock;
            boolean insideThreat = insideState.getFluidState().is(FluidTags.LAVA) || insideState.getFluidState().is(FluidTags.WATER) || insideState.getBlock() instanceof FireBlock;
            if (insideThreat && (outsideThreat || shouldBlockFluidFlow(level, outside, inside))) {
                level.setBlock(inside, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static boolean isNearClaimBoundary(LandClaimRepository.Claim claim, BlockPos pos) {
        if (claim == null || pos == null) return false;
        return pos.getX() >= claim.minX - 1 && pos.getX() <= claim.maxX + 1 && pos.getZ() >= claim.minZ - 1 && pos.getZ() <= claim.maxZ + 1;
    }

    private static boolean isDangerousTransportItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(Items.HOPPER_MINECART) || stack.is(Items.HOPPER) || stack.is(Items.DISPENSER) || stack.is(Items.DROPPER)
                || stack.is(Items.TNT) || stack.is(Items.TNT_MINECART) || stack.is(Items.LAVA_BUCKET) || stack.is(Items.WATER_BUCKET);
    }

    private static boolean isDangerousEntity(Entity entity) {
        return entity instanceof PrimedTnt || entity.getType() == EntityType.TNT_MINECART
                || entity instanceof AbstractHurtingProjectile;
    }

    private static boolean isContainer(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof net.minecraft.world.Container) return true;
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock
                || state.getBlock() instanceof HopperBlock
                || state.getBlock() instanceof DispenserBlock
                || state.getBlock() instanceof DropperBlock;
    }

    private static boolean isRedstoneOrDoor(BlockState state) {
        return state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof HopperBlock
                || state.getBlock() instanceof DispenserBlock
                || state.getBlock() instanceof DropperBlock;
    }

    private static void deny(ServerPlayer player, String message) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        Long last = LAST_DENY_MESSAGE_MS.get(player.getUUID());
        if (last != null && now - last < DENY_MESSAGE_COOLDOWN_MS) {
            return;
        }
        LAST_DENY_MESSAGE_MS.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
