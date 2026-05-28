package com.tom.ccgamepads.client;

import com.tom.ccgamepads.GamepadRegistry;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

public final class GamepadClientEntrypoint {
    private GamepadClientEntrypoint() {
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(GamepadClientEntrypoint::clientSetup);
        modEventBus.addListener(GamepadClientEntrypoint::registerClientExtensions);
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
            (IConfigScreenFactory)(container, parent) -> new GamepadConfigScreen(parent));
    }

    private static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForge.EVENT_BUS.register(GamepadClientBridge.class);
            NeoForge.EVENT_BUS.register(GamepadCableRenderer.class);
            NeoForge.EVENT_BUS.register(GamepadItemRenderer.class);
        });
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return GamepadItemRenderer.INSTANCE;
            }
        }, GamepadRegistry.GAMEPAD_ITEM.get());
    }
}
