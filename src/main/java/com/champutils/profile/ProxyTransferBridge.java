package com.champutils.profile;

import com.champutils.debug.ChampDebugManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Arrays;

/**
 * Sends Velocity/Bungee-compatible backend plugin messages.
 *
 * 1.21.1 no longer lets DiscardedPayload carry arbitrary bytes. This registers a real
 * CustomPacketPayload for bungeecord:main and sends the standard BungeeCord "Connect"
 * subchannel payload that Velocity understands.
 */
public final class ProxyTransferBridge {
    private static final ResourceLocation BUNGEE_CHANNEL = ResourceLocation.fromNamespaceAndPath("bungeecord", "main");
    private static boolean registered = false;

    private ProxyTransferBridge() {}

    public static void register() {
        if (registered) return;
        registered = true;
        try {
            PayloadTypeRegistry.playS2C().register(BungeeCordPayload.TYPE, BungeeCordPayload.CODEC);
        } catch (Throwable ignored) {
            // Duplicate registration can happen during dev reloads; do not kill the server for it.
        }
    }

    public static boolean connect(ServerPlayer player, String targetServer) {
        if (player == null || targetServer == null || targetServer.isBlank()) {
            return false;
        }
        register();

        try {
            byte[] payload = createConnectPayload(targetServer.trim());
            ServerPlayNetworking.send(player, new BungeeCordPayload(payload));
            ChampDebugManager.log(
                    ChampDebugManager.Category.PROFILES,
                    "[ChampUtils][ProfileTransferDebug] sent proxy plugin-message Connect target=" + targetServer.trim()
                            + " player=" + player.getGameProfile().getName()
            );
            return true;
        } catch (Throwable error) {
            ChampDebugManager.log(
                    ChampDebugManager.Category.PROFILES,
                    "[ChampUtils][ProfileTransferDebug] proxy plugin-message Connect failed target=" + targetServer.trim()
                            + " player=" + player.getGameProfile().getName()
                            + " error=" + error.getClass().getSimpleName() + ": " + error.getMessage()
            );
            return false;
        }
    }

    private static byte[] createConnectPayload(String targetServer) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF("Connect");
            out.writeUTF(targetServer);
        }
        return bytes.toByteArray();
    }

    public record BungeeCordPayload(byte[] data) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BungeeCordPayload> TYPE = new CustomPacketPayload.Type<>(BUNGEE_CHANNEL);

        public static final StreamCodec<FriendlyByteBuf, BungeeCordPayload> CODEC = StreamCodec.of(
                (buf, payload) -> {
                    byte[] data = payload == null || payload.data == null ? new byte[0] : payload.data;
                    buf.writeBytes(data);
                },
                buf -> {
                    byte[] data = new byte[buf.readableBytes()];
                    buf.readBytes(data);
                    return new BungeeCordPayload(data);
                }
        );

        public BungeeCordPayload {
            data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
