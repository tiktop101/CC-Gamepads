package com.tom.ccgamepads.network;

import com.tom.ccgamepads.ControllerSecurityManager;
import com.tom.ccgamepads.GamepadConstants;
import com.tom.ccgamepads.GamepadServerState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Supplier;

public class GamepadInputPacket {
    private final UUID playerId;
    private final int controllerId;
    private final String guid;
    private final byte[] buttons;
    private final float[] axes;

    public GamepadInputPacket(UUID playerId, int controllerId, String guid, byte[] buttons, float[] axes) {
        this.playerId = playerId;
        this.controllerId = controllerId;
        this.guid = guid;
        this.buttons = buttons;
        this.axes = axes;
    }

    public static void encode(GamepadInputPacket packet, FriendlyByteBuf buf) {
        buf.writeUUID(packet.playerId);
        buf.writeVarInt(packet.controllerId);
        buf.writeUtf(packet.guid, GamepadConstants.MAX_CONTROLLER_GUID_LENGTH);
        buf.writeByteArray(packet.buttons);
        buf.writeVarInt(packet.axes.length);
        for (float axis : packet.axes) buf.writeFloat(axis);
    }

    public static GamepadInputPacket decode(FriendlyByteBuf buf) {
        UUID playerId = buf.readUUID();
        int controllerId = buf.readVarInt();
        String guid = buf.readUtf(GamepadConstants.MAX_CONTROLLER_GUID_LENGTH);
        byte[] buttons = buf.readByteArray(GamepadConstants.BUTTON_COUNT);
        int axisCount = buf.readVarInt();
        if (axisCount != GamepadConstants.AXIS_COUNT) throw new IllegalArgumentException("invalid axis count: " + axisCount);
        float[] axes = new float[axisCount];
        for (int i = 0; i < axisCount; i++) axes[i] = sanitizeAxis(buf.readFloat(), i >= 4);
        return new GamepadInputPacket(playerId, controllerId, guid, buttons, axes);
    }

    public static void handle(GamepadInputPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !sender.getUUID().equals(packet.playerId)) return;
            if (!packet.isValid()) return;
            if (!GamepadServerState.accepts(sender, packet.controllerId, packet.guid)) return;

            GamepadServerState.update(sender, packet.controllerId,
                ControllerSecurityManager.getControllerName(sender.getUUID(), packet.controllerId),
                packet.guid, packet.buttons, packet.axes);
        });
        ctx.get().setPacketHandled(true);
    }

    private boolean isValid() {
        if (controllerId < 0 || controllerId >= GamepadConstants.MAX_CONTROLLERS_PER_PLAYER) return false;
        if (guid == null || guid.isBlank() || guid.length() > GamepadConstants.MAX_CONTROLLER_GUID_LENGTH) return false;
        if (buttons.length != GamepadConstants.BUTTON_COUNT || axes.length != GamepadConstants.AXIS_COUNT) return false;
        for (byte button : buttons) if (button != 0 && button != 1) return false;
        for (int i = 0; i < axes.length; i++) {
            float axis = axes[i];
            if (!Float.isFinite(axis)) return false;
            if (i >= 4) {
                if (axis < 0.0f || axis > 1.0f) return false;
            } else if (axis < -1.0f || axis > 1.0f) {
                return false;
            }
        }
        return true;
    }

    private static float sanitizeAxis(float value, boolean trigger) {
        if (!Float.isFinite(value)) return 0.0f;
        float min = trigger ? 0.0f : -1.0f;
        return Math.max(min, Math.min(1.0f, value));
    }
}
