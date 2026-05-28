package com.tom.ccgamepads.network;

import com.tom.ccgamepads.ControllerSecurityManager;
import com.tom.ccgamepads.GamepadConstants;
import com.tom.ccgamepads.GamepadServerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class GamepadListPacket {
    private final List<Info> controllers;
    private final UUID playerId;

    public GamepadListPacket(List<Info> controllers, UUID playerId) {
        this.controllers = controllers;
        this.playerId = playerId;
    }

    public static void encode(GamepadListPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.playerId);
        buf.writeVarInt(packet.controllers.size());
        for (Info info : packet.controllers) {
            buf.writeVarInt(info.id);
            buf.writeUtf(info.name, GamepadConstants.MAX_CONTROLLER_NAME_LENGTH);
            buf.writeUtf(info.guid, GamepadConstants.MAX_CONTROLLER_GUID_LENGTH);
            buf.writeVarInt(info.buttonCount);
            buf.writeVarInt(info.axisCount);
        }
    }

    public static GamepadListPacket decode(FriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        int count = buf.readVarInt();
        if (count < 0 || count > GamepadConstants.MAX_CONTROLLERS_PER_PLAYER) throw new IllegalArgumentException("invalid controller count: " + count);
        List<Info> controllers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            controllers.add(new Info(buf.readVarInt(), buf.readUtf(GamepadConstants.MAX_CONTROLLER_NAME_LENGTH),
                buf.readUtf(GamepadConstants.MAX_CONTROLLER_GUID_LENGTH), buf.readVarInt(), buf.readVarInt()));
        }
        return new GamepadListPacket(controllers, playerId);
    }

    public static void handle(GamepadListPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !sender.getUUID().equals(packet.playerId)) return;
            Map<Integer, ControllerSecurityManager.ControllerIdentity> identities = new LinkedHashMap<>();
            for (Info info : packet.controllers) {
                if (!info.isValid()) return;
                identities.put(info.id, new ControllerSecurityManager.ControllerIdentity(info.safeName(), info.guid));
            }
            ControllerSecurityManager.replaceControllers(sender.getUUID(), identities);
            GamepadServerState.clearPlayerStates(sender.getUUID());
        });
        ctx.get().setPacketHandled(true);
    }

    public record Info(int id, String name, String guid, int buttonCount, int axisCount) {
        private boolean isValid() {
            return id >= 0
                && id < GamepadConstants.MAX_CONTROLLERS_PER_PLAYER
                && guid != null
                && !guid.isBlank()
                && guid.length() <= GamepadConstants.MAX_CONTROLLER_GUID_LENGTH
                && name != null
                && name.length() <= GamepadConstants.MAX_CONTROLLER_NAME_LENGTH
                && buttonCount == GamepadConstants.BUTTON_COUNT
                && axisCount == GamepadConstants.AXIS_COUNT;
        }

        private String safeName() {
            String trimmed = name.trim();
            return trimmed.isEmpty() ? "Gamepad" : trimmed;
        }
    }
}
