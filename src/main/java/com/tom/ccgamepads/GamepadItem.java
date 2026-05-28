package com.tom.ccgamepads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class GamepadItem extends Item {
    public GamepadItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!context.getPlayer().isShiftKeyDown()) return InteractionResult.PASS;

        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof GamepadPeripheralBlockEntity)) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        if (!level.isClientSide && context.getPlayer() instanceof ServerPlayer player) {
            GamepadBinding binding = GamepadBinding.get(stack);
            if (binding != null && binding.matches(level, pos)) {
                GamepadBinding.clear(stack);
                player.displayClientMessage(Component.translatable("message.ccgamepads.unbound"), true);
            } else {
                GamepadBinding.bind(stack, level.dimension(), pos);
                player.displayClientMessage(Component.translatable("message.ccgamepads.bound"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GamepadBinding binding = GamepadBinding.get(stack);
        if (binding == null) {
            tooltip.add(Component.translatable("tooltip.ccgamepads.gamepad.status.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.ccgamepads.gamepad.status.bound").withStyle(ChatFormatting.AQUA));
            int slot = GamepadBinding.getSlot(stack);
            if (slot > 0) {
                tooltip.add(Component.translatable("tooltip.ccgamepads.gamepad.slot", slot).withStyle(ChatFormatting.GOLD));
            } else {
                tooltip.add(Component.translatable("tooltip.ccgamepads.gamepad.slot.unassigned").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
