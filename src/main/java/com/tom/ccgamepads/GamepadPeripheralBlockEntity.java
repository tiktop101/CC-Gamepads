package com.tom.ccgamepads;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class GamepadPeripheralBlockEntity extends BlockEntity {
    private GamepadPeripheral peripheral;

    public GamepadPeripheralBlockEntity(BlockPos pos, BlockState state) {
        super(GamepadRegistry.GAMEPAD_BLOCK_ENTITY.get(), pos, state);
    }

    public GamepadPeripheral getPeripheral() {
        if (peripheral == null && level != null) {
            peripheral = new GamepadPeripheral(level, worldPosition);
        }
        return peripheral;
    }

    public void cleanup() {
        if (peripheral != null) peripheral.cleanup();
        GamepadServerState.remove(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        cleanup();
        super.setRemoved();
    }
}
