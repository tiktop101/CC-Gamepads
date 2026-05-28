package com.tom.ccgamepads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public record GamepadBinding(ResourceKey<Level> dimension, BlockPos pos) {
    private static final String DIMENSION = "ccgamepads_dimension";
    private static final String X = "ccgamepads_x";
    private static final String Y = "ccgamepads_y";
    private static final String Z = "ccgamepads_z";
    private static final String SLOT = "ccgamepads_slot";

    public static void bind(ItemStack stack, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(DIMENSION, dimension.location().toString());
        tag.putInt(X, pos.getX());
        tag.putInt(Y, pos.getY());
        tag.putInt(Z, pos.getZ());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clear(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(DIMENSION);
        tag.remove(X);
        tag.remove(Y);
        tag.remove(Z);
        tag.remove(SLOT);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void setSlot(ItemStack stack, int slot) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(SLOT, slot);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Nullable
    public static GamepadBinding get(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(DIMENSION) || !tag.contains(X) || !tag.contains(Y) || !tag.contains(Z)) return null;

        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(DIMENSION));
        if (dimensionId == null) return null;
        return new GamepadBinding(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId),
            new BlockPos(tag.getInt(X), tag.getInt(Y), tag.getInt(Z)));
    }

    public boolean matches(Level level, BlockPos otherPos) {
        return level.dimension().equals(dimension) && pos.equals(otherPos);
    }

    public static int getSlot(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        return tag.contains(SLOT) ? tag.getInt(SLOT) : 0;
    }
}
