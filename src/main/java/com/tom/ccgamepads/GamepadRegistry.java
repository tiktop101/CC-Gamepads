package com.tom.ccgamepads;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class GamepadRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CCGamepadsMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CCGamepadsMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CCGamepadsMod.MOD_ID);

    public static final Supplier<Block> GAMEPAD_BLOCK = BLOCKS.register("gamepad_peripheral", GamepadPeripheralBlock::new);
    public static final Supplier<Item> GAMEPAD_BLOCK_ITEM = ITEMS.register("gamepad_peripheral", () -> new BlockItem(GAMEPAD_BLOCK.get(), new Item.Properties()));
    public static final Supplier<BlockEntityType<GamepadPeripheralBlockEntity>> GAMEPAD_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("gamepad_peripheral", () -> BlockEntityType.Builder.of(GamepadPeripheralBlockEntity::new, GAMEPAD_BLOCK.get()).build(null));

    public static final Supplier<Item> GAMEPAD_ITEM = ITEMS.register("gamepad", () -> new GamepadItem(new Item.Properties().stacksTo(1)));
}
