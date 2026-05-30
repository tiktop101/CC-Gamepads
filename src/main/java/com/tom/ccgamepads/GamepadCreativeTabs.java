package com.tom.ccgamepads;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class GamepadCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CCGamepadsMod.MOD_ID);

    public static final Supplier<CreativeModeTab> TAB = CREATIVE_MODE_TABS.register("ccgamepads",
        () -> CreativeModeTab.builder()
            .icon(() -> new ItemStack(GamepadRegistry.GAMEPAD_BLOCK_ITEM.get()))
            .title(Component.translatable("itemGroup.ccgamepads"))
            .displayItems((parameters, output) -> {
                output.accept(GamepadRegistry.GAMEPAD_BLOCK_ITEM.get());
                output.accept(GamepadRegistry.GAMEPAD_ITEM.get());
            })
            .build()
    );

    public static void register(IEventBus eventBus) {
        if (!ModList.get().isLoaded("directgpu")) {
            CREATIVE_MODE_TABS.register(eventBus);
        }
    }
}
