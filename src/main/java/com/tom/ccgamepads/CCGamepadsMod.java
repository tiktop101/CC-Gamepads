package com.tom.ccgamepads;

import com.tom.ccgamepads.network.GamepadNetwork;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.IEventBus;

@Mod(CCGamepadsMod.MOD_ID)
public class CCGamepadsMod {
    public static final String MOD_ID = "ccgamepads";

    public CCGamepadsMod(IEventBus modEventBus, ModContainer modContainer) {
        GamepadRegistry.BLOCKS.register(modEventBus);
        GamepadRegistry.ITEMS.register(modEventBus);
        GamepadRegistry.BLOCK_ENTITIES.register(modEventBus);
        GamepadCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::setup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClient(modEventBus, modContainer);
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            GamepadRegistry.GAMEPAD_BLOCK_ENTITY.get(),
            (be, side) -> be.getPeripheral()
        );
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(GamepadNetwork::register);
    }

    private static void registerClient(IEventBus modEventBus, ModContainer modContainer) {
        try {
            Class<?> entrypoint = Class.forName("com.tom.ccgamepads.client.GamepadClientEntrypoint");
            entrypoint.getMethod("register", IEventBus.class, ModContainer.class).invoke(null, modEventBus, modContainer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register CC:Gamepads client hooks", e);
        }
    }

    public static void log(String message) {
        System.out.println("[CC:Gamepads] " + message);
    }

    public static void warn(String message) {
        System.err.println("[CC:Gamepads] " + message);
    }
}
