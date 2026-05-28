package com.tom.ccgamepads;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GamepadServerState {
    private static final Map<Key, LinkedHashMap<UUID, ControllerState>> STATES = new ConcurrentHashMap<>();

    public static boolean accepts(ServerPlayer player, int controllerId, String guid) {
        return findHeldBinding(player) != null && ControllerSecurityManager.verifyController(player.getUUID(), controllerId, guid);
    }

    public static void update(ServerPlayer player, int controllerId, String name, String guid, byte[] buttons, float[] axes) {
        GamepadBinding binding = findHeldBinding(player);
        if (binding == null) return;
        if (buttons.length != GamepadConstants.BUTTON_COUNT || axes.length != GamepadConstants.AXIS_COUNT) return;

        Key key = new Key(binding.dimension().location().toString(), binding.pos());
        LinkedHashMap<UUID, ControllerState> blockStates = STATES.computeIfAbsent(key, ignored -> new LinkedHashMap<>());

        synchronized (blockStates) {
            ControllerState previous = blockStates.remove(player.getUUID());
            if (previous == null && blockStates.size() >= GamepadConstants.MAX_PLAYERS) {
                UUID oldest = blockStates.keySet().iterator().next();
                blockStates.remove(oldest);
            }

            int slot = previous == null ? nextFreeSlot(blockStates) : previous.slot;
            ControllerState state = new ControllerState(slot, player.getUUID(), player.getGameProfile().getName(), controllerId, name, guid,
                buttons.clone(), axes.clone(), System.currentTimeMillis());
            blockStates.put(player.getUUID(), state);
            updateBoundItemSlot(player, binding, state.slot());
            GamepadPeripheral peripheral = findPeripheral(player.level(), binding.pos());
            if (peripheral != null) peripheral.onControllerState(state);
        }
    }

    @Nullable
    public static GamepadBinding findHeldBinding(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(GamepadRegistry.GAMEPAD_ITEM.get())) continue;
            GamepadBinding binding = GamepadBinding.get(stack);
            if (binding != null) return binding;
        }
        return null;
    }

    public static List<ControllerState> getStates(Level level, BlockPos pos) {
        LinkedHashMap<UUID, ControllerState> blockStates = STATES.get(new Key(level.dimension().location().toString(), pos));
        if (blockStates == null) return List.of();
        synchronized (blockStates) {
            return new ArrayList<>(blockStates.values());
        }
    }

    @Nullable
    public static ControllerState getState(Level level, BlockPos pos, int slot) {
        for (ControllerState state : getStates(level, pos)) {
            if (state.slot == slot) return state;
        }
        return null;
    }

    public static void remove(@Nullable Level level, BlockPos pos) {
        if (level != null) STATES.remove(new Key(level.dimension().location().toString(), pos));
    }

    public static void clearPlayer(UUID playerId) {
        ControllerSecurityManager.clearPlayer(playerId);
        clearPlayerStates(playerId);
    }

    public static void clearPlayerStates(UUID playerId) {
        for (LinkedHashMap<UUID, ControllerState> blockStates : STATES.values()) {
            synchronized (blockStates) {
                blockStates.remove(playerId);
            }
        }
    }

    @Nullable
    private static GamepadPeripheral findPeripheral(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof GamepadPeripheralBlockEntity be) return be.getPeripheral();
        return null;
    }

    private static void updateBoundItemSlot(ServerPlayer player, GamepadBinding binding, int slot) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            GamepadBinding held = GamepadBinding.get(stack);
            if (held != null && held.matches(player.level(), binding.pos())) {
                GamepadBinding.setSlot(stack, slot);
            }
        }
    }

    private static int nextFreeSlot(LinkedHashMap<UUID, ControllerState> states) {
        for (int slot = 1; slot <= GamepadConstants.MAX_PLAYERS; slot++) {
            boolean used = false;
            for (ControllerState state : states.values()) {
                if (state.slot == slot) {
                    used = true;
                    break;
                }
            }
            if (!used) return slot;
        }
        return GamepadConstants.MAX_PLAYERS;
    }

    private record Key(String dimension, BlockPos pos) {}

    public record ControllerState(int slot, UUID playerId, String playerName, int controllerId, String controllerName,
                                  String guid, byte[] buttons, float[] axes, long updatedAt) {
        public Map<String, Object> toLua() {
            Map<String, Object> result = new HashMap<>();
            result.put("slot", slot);
            result.put("player", playerName);
            result.put("playerId", playerId.toString());
            result.put("controllerId", controllerId);
            result.put("controllerName", controllerName);
            result.put("guid", guid);
            result.put("updatedAt", updatedAt);

            // buttons[] is 0-indexed internally; Lua uses 1-indexed keys
            Map<Integer, Boolean> buttonMap = new HashMap<>();
            for (int i = 0; i < buttons.length; i++) buttonMap.put(i + 1, buttons[i] != 0);
            result.put("buttons", buttonMap);
            result.put("buttonNames", namedButtons());

            // axes[] is 0-indexed internally; Lua uses 1-indexed keys
            Map<Integer, Double> axisMap = new HashMap<>();
            for (int i = 0; i < axes.length; i++) axisMap.put(i + 1, (double) axes[i]);
            result.put("axes", axisMap);
            result.put("axisNames", namedAxes());

            return result;
        }

        /**
         * Named button map. Internal button array follows SDL/GLFW gamepad layout (0-indexed).
         * Lua slots are 1-indexed (internal index + 1).
         *
         * Internal index -> SDL name -> Lua slot:
         *  0 = A           -> slot 1
         *  1 = B           -> slot 2
         *  2 = X           -> slot 3
         *  3 = Y           -> slot 4
         *  4 = LeftBumper  -> slot 5
         *  5 = RightBumper -> slot 6
         *  6 = Back/Select -> slot 7
         *  7 = Start       -> slot 8
         *  8 = Guide/Home  -> slot 9
         *  9 = LeftStick   -> slot 10
         * 10 = RightStick  -> slot 11
         * 11 = DpadUp      -> slot 12
         * 12 = DpadRight   -> slot 13
         * 13 = DpadDown    -> slot 14
         * 14 = DpadLeft    -> slot 15
         */
        private Map<String, Boolean> namedButtons() {
            Map<String, Boolean> names = new HashMap<>();
            names.put("a",            btn(0));
            names.put("b",            btn(1));
            names.put("x",            btn(2));
            names.put("y",            btn(3));
            names.put("leftBumper",   btn(4));
            names.put("rightBumper",  btn(5));
            names.put("back",         btn(6));
            names.put("select",       btn(6));   // alias for back
            names.put("start",        btn(7));
            names.put("guide",        btn(8));
            names.put("home",         btn(8));   // alias for guide
            names.put("leftStick",    btn(9));
            names.put("rightStick",   btn(10));
            names.put("dpadUp",       btn(11));
            names.put("dpadRight",    btn(12));
            names.put("dpadDown",     btn(13));
            names.put("dpadLeft",     btn(14));
            return names;
        }

        boolean btn(int index) {
            return index >= 0 && index < buttons.length && buttons[index] != 0;
        }

        /**
         * Named axis map. Internal axis array follows SDL/GLFW gamepad axis layout (0-indexed).
         * Triggers are normalized to [0, 1] before being stored.
         *
         * Internal index -> SDL name -> Lua slot:
         *  0 = LeftX         -> slot 1
         *  1 = LeftY         -> slot 2
         *  2 = RightX        -> slot 3
         *  3 = RightY        -> slot 4
         *  4 = LeftTrigger   -> slot 5  (0=released, 1=fully pressed)
         *  5 = RightTrigger  -> slot 6  (0=released, 1=fully pressed)
         */
        private Map<String, Double> namedAxes() {
            Map<String, Double> names = new HashMap<>();
            names.put("leftX",        axis(0));
            names.put("leftY",        axis(1));
            names.put("rightX",       axis(2));
            names.put("rightY",       axis(3));
            names.put("leftTrigger",  axis(4));
            names.put("rightTrigger", axis(5));
            return names;
        }

        double axis(int index) {
            return index >= 0 && index < axes.length ? (double) axes[index] : 0.0;
        }
    }
}
