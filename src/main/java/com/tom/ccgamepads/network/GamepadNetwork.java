package com.tom.ccgamepads.network;

import com.tom.ccgamepads.CCGamepadsMod;
import com.tom.ccgamepads.GamepadConstants;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@EventBusSubscriber(modid = CCGamepadsMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class GamepadNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final List<PacketDef<?>> PACKETS = new ArrayList<>();
    private static int packetId;
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        add(GamepadListPacket.class, GamepadListPacket::encode, GamepadListPacket::decode, GamepadListPacket::handle);
        add(GamepadInputPacket.class, GamepadInputPacket::encode, GamepadInputPacket::decode, GamepadInputPacket::handle);
    }

    @SubscribeEvent
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        register();
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playBidirectional(GamepadPayload.TYPE, GamepadPayload.STREAM_CODEC, GamepadNetwork::handlePayload);
    }

    private static <T> void add(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        PACKETS.add(new PacketDef<>(packetId++, type, encoder, decoder, handler));
    }

    private static GamepadPayload wrap(Object packet) {
        for (PacketDef<?> def : PACKETS) if (def.type.isInstance(packet)) return def.encodeAny(packet);
        throw new IllegalArgumentException("Unregistered CC:Gamepads packet: " + packet.getClass().getName());
    }

    private static void handlePayload(GamepadPayload payload, IPayloadContext ctx) {
        if (payload.packetId < 0 || payload.packetId >= PACKETS.size()) {
            CCGamepadsMod.warn("Dropped unknown packet id " + payload.packetId);
            return;
        }
        if (payload.data.length > GamepadConstants.MAX_PACKET_BYTES) {
            CCGamepadsMod.warn("Dropped oversized packet id " + payload.packetId + " (" + payload.data.length + " bytes)");
            return;
        }
        try {
            PACKETS.get(payload.packetId).decodeAndHandle(payload.data, () -> new NetworkEvent.Context(ctx));
        } catch (RuntimeException e) {
            CCGamepadsMod.warn("Dropped malformed packet id " + payload.packetId + ": " + e.getMessage());
        }
    }

    public static void sendToServer(Object packet) {
        PacketDistributor.sendToServer(wrap(packet));
    }

    private record PacketDef<T>(int id, Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        GamepadPayload encodeAny(Object packet) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            encoder.accept(type.cast(packet), buf);
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new GamepadPayload(id, data);
        }

        void decodeAndHandle(byte[] data, Supplier<NetworkEvent.Context> ctx) {
            handler.accept(decoder.apply(new FriendlyByteBuf(Unpooled.wrappedBuffer(data))), ctx);
        }
    }

    public record GamepadPayload(int packetId, byte[] data) implements CustomPacketPayload {
        public static final Type<GamepadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CCGamepadsMod.MOD_ID, "main"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GamepadPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.packetId);
                if (payload.data.length > GamepadConstants.MAX_PACKET_BYTES) {
                    throw new IllegalArgumentException("CC:Gamepads packet is too large: " + payload.data.length + " bytes");
                }
                buf.writeByteArray(payload.data);
            },
            buf -> new GamepadPayload(buf.readVarInt(), buf.readByteArray(GamepadConstants.MAX_PACKET_BYTES))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
