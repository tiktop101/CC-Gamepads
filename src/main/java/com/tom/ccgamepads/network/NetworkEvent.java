package com.tom.ccgamepads.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class NetworkEvent {
    public static class Context {
        private final IPayloadContext context;

        public Context(IPayloadContext context) {
            this.context = context;
        }

        public void enqueueWork(Runnable runnable) {
            context.enqueueWork(runnable);
        }

        public ServerPlayer getSender() {
            return context.player() instanceof ServerPlayer player ? player : null;
        }

        public void setPacketHandled(boolean handled) {
        }
    }
}
