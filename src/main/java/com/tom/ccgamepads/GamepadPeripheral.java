package com.tom.ccgamepads;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class GamepadPeripheral implements IPeripheral {
    private final Level level;
    private final BlockPos pos;
    private final Set<IComputerAccess> computers = new CopyOnWriteArraySet<>();

    public GamepadPeripheral(Level level, BlockPos pos) {
        this.level = level;
        this.pos = pos;
    }

    @Nonnull
    @Override
    public String getType() {
        return "gamepad";
    }

    @Override
    public void attach(@Nonnull IComputerAccess computer) {
        computers.add(computer);
    }

    @Override
    public void detach(@Nonnull IComputerAccess computer) {
        computers.remove(computer);
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return this == other || other instanceof GamepadPeripheral gamepad && gamepad.pos.equals(pos);
    }

    public void cleanup() {
        computers.clear();
    }

    public void onControllerState(GamepadServerState.ControllerState state) {
        for (IComputerAccess computer : computers) {
            computer.queueEvent("cc_events", "gamepad", state.slot(), state.playerName(), state.controllerId());
        }
    }

    @LuaFunction
    public final List<Map<String, Object>> getPlayers() {
        return GamepadServerState.getStates(level, pos).stream().map(GamepadServerState.ControllerState::toLua).toList();
    }

    @LuaFunction
    public final Map<String, Object> getState(int slot) throws LuaException {
        return requireSlot(slot).toLua();
    }

    @LuaFunction
    public final boolean isDown(int slot, int button) throws LuaException {
        byte[] buttons = requireSlot(slot).buttons();
        int index = button - 1;
        return index >= 0 && index < buttons.length && buttons[index] != 0;
    }

    @LuaFunction
    public final double getAxis(int slot, int axis) throws LuaException {
        float[] axes = requireSlot(slot).axes();
        int index = axis - 1;
        return index >= 0 && index < axes.length ? axes[index] : 0.0;
    }

    @LuaFunction
    public final boolean isButtonDown(int slot, String name) throws LuaException {
        GamepadServerState.ControllerState state = requireSlot(slot);
        return switch (name) {
            case "a"           -> state.btn(0);
            case "b"           -> state.btn(1);
            case "x"           -> state.btn(2);
            case "y"           -> state.btn(3);
            case "leftBumper"  -> state.btn(4);
            case "rightBumper" -> state.btn(5);
            case "back",
                 "select"      -> state.btn(6);
            case "start"       -> state.btn(7);
            case "guide",
                 "home"        -> state.btn(8);
            case "leftStick"   -> state.btn(9);
            case "rightStick"  -> state.btn(10);
            case "dpadUp"      -> state.btn(11);
            case "dpadRight"   -> state.btn(12);
            case "dpadDown"    -> state.btn(13);
            case "dpadLeft"    -> state.btn(14);
            default -> throw new LuaException("unknown button name: " + name);
        };
    }

    @LuaFunction
    public final double getAxisValue(int slot, String name) throws LuaException {
        GamepadServerState.ControllerState state = requireSlot(slot);
        return switch (name) {
            case "leftX"        -> state.axis(0);
            case "leftY"        -> state.axis(1);
            case "rightX"       -> state.axis(2);
            case "rightY"       -> state.axis(3);
            case "leftTrigger"  -> state.axis(4);
            case "rightTrigger" -> state.axis(5);
            default -> throw new LuaException("unknown axis name: " + name);
        };
    }

    @LuaFunction
    public final int getMaxPlayers() {
        return GamepadConstants.MAX_PLAYERS;
    }

    @LuaFunction
    public final int getButtonCount() {
        return GamepadConstants.BUTTON_COUNT;
    }

    @LuaFunction
    public final int getAxisCount() {
        return GamepadConstants.AXIS_COUNT;
    }

    private GamepadServerState.ControllerState requireSlot(int slot) throws LuaException {
        if (slot < 1 || slot > GamepadConstants.MAX_PLAYERS) throw new LuaException("slot must be between 1 and " + GamepadConstants.MAX_PLAYERS);
        GamepadServerState.ControllerState state = GamepadServerState.getState(level, pos, slot);
        if (state == null) throw new LuaException("no controller bound to slot " + slot);
        return state;
    }
}
