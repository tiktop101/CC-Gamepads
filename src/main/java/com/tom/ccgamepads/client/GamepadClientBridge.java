package com.tom.ccgamepads.client;

import com.tom.ccgamepads.CCGamepadsMod;
import com.tom.ccgamepads.GamepadConstants;
import com.tom.ccgamepads.network.GamepadInputPacket;
import com.tom.ccgamepads.network.GamepadListPacket;
import com.tom.ccgamepads.network.GamepadNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public class GamepadClientBridge {
    private static final int SEND_INTERVAL = 2;
    private static final int SCAN_INTERVAL = 100;
    private static final Map<Integer, Snapshot> PREVIOUS = new HashMap<>();
    private static final Map<Integer, String> GUIDS = new HashMap<>();
    private static int tickCounter;
    private static int scanCounter;
    private static boolean sentInitialList;
    private static int lastCount = -1;
    private static boolean mappingsLoaded;

    /**
     * Button indices (0-based internally, 1-based in Lua API) — SDL gamepad layout:
     *  0=A  1=B  2=X  3=Y  4=LBumper  5=RBumper  6=Back  7=Start  8=Guide
     *  9=LStick  10=RStick  11=DpadUp  12=DpadRight  13=DpadDown  14=DpadLeft
     */
    public static final int BUTTON_COUNT = GamepadConstants.BUTTON_COUNT;

    /**
     * Axis indices (0-based internally, 1-based in Lua API) — SDL gamepad layout:
     *  0=LeftX  1=LeftY  2=RightX  3=RightY
     *  4=LeftTrigger(0..1)  5=RightTrigger(0..1)
     */
    public static final int AXIS_COUNT = GamepadConstants.AXIS_COUNT;

    public static void requestControllerListRefresh() {
        sentInitialList = false;
        lastCount = -1;
        PREVIOUS.clear();
    }

    public static List<ClientControllerInfo> availableControllers() {
        ensureMappingsLoaded();
        return listControllers();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            sentInitialList = false;
            lastCount = -1;
            PREVIOUS.clear();
            return;
        }

        ensureMappingsLoaded();

        if (!sentInitialList) {
            sendControllerList(mc.player.getUUID());
            sentInitialList = true;
        }

        scanCounter++;
        if (scanCounter >= SCAN_INTERVAL) {
            scanCounter = 0;
            int count = selectedControllers().size();
            if (count != lastCount) {
                PREVIOUS.clear();
                sendControllerList(mc.player.getUUID());
            }
        }

        tickCounter++;
        if (tickCounter >= SEND_INTERVAL) {
            tickCounter = 0;
            sendChangedStates(mc.player.getUUID());
        }
    }

    private static void ensureMappingsLoaded() {
        if (mappingsLoaded) return;
        mappingsLoaded = true;

        try (InputStream internal = GamepadClientBridge.class.getResourceAsStream("/gamecontrollerdb.txt")) {
            updateMappings(internal);
        } catch (IOException e) {
            CCGamepadsMod.warn("Failed to apply bundled controller mappings: " + e.getMessage());
        }

        Path local = Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(CCGamepadsMod.MOD_ID).resolve("gamecontrollerdb.txt");
        if (Files.exists(local)) {
            try (InputStream input = Files.newInputStream(local)) {
                updateMappings(input);
            } catch (IOException e) {
                CCGamepadsMod.warn("Failed to apply local controller mappings: " + e.getMessage());
            }
        }
    }

    private static void updateMappings(InputStream input) throws IOException {
        if (input == null) return;
        byte[] bytes = input.readAllBytes();
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length + 1);
        try {
            buffer.put(bytes).put((byte) 0).flip();
            GLFW.glfwUpdateGamepadMappings(buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private static void sendControllerList(UUID playerId) {
        List<GamepadListPacket.Info> infos = new ArrayList<>();
        GUIDS.clear();
        for (ClientControllerInfo controller : selectedControllers()) {
            infos.add(new GamepadListPacket.Info(controller.id, controller.name, controller.guid, BUTTON_COUNT, AXIS_COUNT));
            GUIDS.put(controller.id, controller.guid);
        }
        lastCount = infos.size();
        GamepadNetwork.sendToServer(new GamepadListPacket(infos, playerId));
    }

    private static void sendChangedStates(UUID playerId) {
        for (ClientControllerInfo controller : selectedControllers()) {
            Snapshot snapshot = Snapshot.capture(controller);
            if (snapshot == null) continue;
            Snapshot previous = PREVIOUS.get(controller.id);
            if (previous == null || !snapshot.sameAs(previous)) {
                PREVIOUS.put(controller.id, snapshot);
                GamepadNetwork.sendToServer(new GamepadInputPacket(
                    playerId, controller.id, GUIDS.getOrDefault(controller.id, ""),
                    snapshot.buttons, snapshot.axes));
            }
        }
    }

    private static List<ClientControllerInfo> selectedControllers() {
        List<ClientControllerInfo> controllers = listControllers();
        if (controllers.isEmpty()) return List.of();

        if (GamepadClientConfig.isAutoControllerSelected()) return List.of(controllers.getFirst());

        for (ClientControllerInfo controller : controllers) {
            if (GamepadClientConfig.isSelectedController(controller.name, controller.guid)) return List.of(controller);
        }

        return List.of();
    }

    private static List<ClientControllerInfo> listControllers() {
        List<ClientControllerInfo> controllers = new ArrayList<>();
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (!GLFW.glfwJoystickPresent(jid)) continue;
            boolean isMapped = GLFW.glfwJoystickIsGamepad(jid);
            String name = isMapped ? GLFW.glfwGetGamepadName(jid) : GLFW.glfwGetJoystickName(jid);
            String guid = GLFW.glfwGetJoystickGUID(jid);
            controllers.add(new ClientControllerInfo(jid, jid, name == null ? "Gamepad" : name, guid == null ? "" : guid));
        }
        return controllers;
    }

    public record ClientControllerInfo(int id, int deviceIndex, String name, String guid) {}

    static class Snapshot {
        final byte[] buttons;
        final float[] axes;

        Snapshot(byte[] buttons, float[] axes) {
            this.buttons = buttons;
            this.axes = axes;
        }

        static Snapshot capture(ClientControllerInfo controller) {
            int jid = controller.deviceIndex;
            byte[] buttons = new byte[BUTTON_COUNT];
            float[] axes = new float[AXIS_COUNT];

            if (GLFW.glfwJoystickIsGamepad(jid)) {
                // SDL-mapped path (Xbox, PS, Switch Pro, etc).
                // GLFW_GAMEPAD_BUTTON_* indices 0-14 map 1:1 to our button array.
                // GLFW_GAMEPAD_AXIS triggers report -1.0 (released) to +1.0 (fully pressed)
                // per the GLFW/SDL spec — normalize to 0..1 with (v+1)/2.
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    GLFWGamepadState state = GLFWGamepadState.malloc(stack);
                    if (GLFW.glfwGetGamepadState(jid, state)) {
                        for (int i = 0; i < BUTTON_COUNT; i++) {
                            buttons[i] = state.buttons(i) == GLFW.GLFW_PRESS ? (byte) 1 : 0;
                        }
                        axes[0] = clamp(state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X));
                        axes[1] = clamp(state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y));
                        axes[2] = clamp(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X));
                        axes[3] = clamp(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y));
                        // SDL triggers: -1=released, +1=fully pressed -> always use (v+1)/2
                        axes[4] = sdlTriggerToUnit(state.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER));
                        axes[5] = sdlTriggerToUnit(state.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER));
                    }
                }
                return new Snapshot(buttons, axes);
            }

            // Raw HID fallback for controllers with no SDL mapping. GLFW can still see
            // these as joysticks even when glfwJoystickIsGamepad is false, but Xbox USB
            // and Xbox Bluetooth expose different raw indices on Linux.
            RawProfile profile = RawProfile.forController(controller);
            ByteBuffer rawButtons = GLFW.glfwGetJoystickButtons(jid);
            FloatBuffer rawAxes   = GLFW.glfwGetJoystickAxes(jid);
            ByteBuffer rawHats    = GLFW.glfwGetJoystickHats(jid);

            buttons[0]  = rawButton(rawButtons, profile.a);
            buttons[1]  = rawButton(rawButtons, profile.b);
            buttons[2]  = rawButton(rawButtons, profile.x);
            buttons[3]  = rawButton(rawButtons, profile.y);
            buttons[4]  = rawButton(rawButtons, profile.leftBumper);
            buttons[5]  = rawButton(rawButtons, profile.rightBumper);
            buttons[6]  = rawButton(rawButtons, profile.back);
            buttons[7]  = rawButton(rawButtons, profile.start);
            buttons[8]  = rawButton(rawButtons, profile.guide);
            buttons[9]  = rawButton(rawButtons, profile.leftStick);
            buttons[10] = rawButton(rawButtons, profile.rightStick);

            int hat = rawHats != null && rawHats.capacity() > 0 ? rawHats.get(0) : 0;
            buttons[11] = (byte) ((hat & GLFW.GLFW_HAT_UP)    != 0 ? 1 : 0);
            buttons[12] = (byte) ((hat & GLFW.GLFW_HAT_RIGHT) != 0 ? 1 : 0);
            buttons[13] = (byte) ((hat & GLFW.GLFW_HAT_DOWN)  != 0 ? 1 : 0);
            buttons[14] = (byte) ((hat & GLFW.GLFW_HAT_LEFT)  != 0 ? 1 : 0);

            axes[0] = rawAxis(rawAxes, profile.leftX);
            axes[1] = rawAxis(rawAxes, profile.leftY);
            axes[2] = rawAxis(rawAxes, profile.rightX);
            axes[3] = rawAxis(rawAxes, profile.rightY);
            axes[4] = sdlTriggerToUnit(rawAxis(rawAxes, profile.leftTrigger));
            axes[5] = sdlTriggerToUnit(rawAxis(rawAxes, profile.rightTrigger));

            return new Snapshot(buttons, axes);
        }

        private record RawProfile(
            int a, int b, int x, int y,
            int leftBumper, int rightBumper,
            int back, int start, int guide,
            int leftStick, int rightStick,
            int leftX, int leftY, int rightX, int rightY,
            int leftTrigger, int rightTrigger
        ) {
            private static final RawProfile XBOX_USB = new RawProfile(
                0, 1, 2, 3,
                4, 5,
                6, 7, 8,
                9, 10,
                0, 1, 3, 4,
                2, 5
            );

            private static final RawProfile XBOX_BLUETOOTH = new RawProfile(
                0, 1, 3, 4,
                6, 7,
                10, 11, 12,
                13, 14,
                0, 1, 2, 3,
                5, 4
            );

            static RawProfile forController(ClientControllerInfo controller) {
                String guid = controller.guid.toLowerCase();
                String name = controller.name.toLowerCase();

                // SDL_GameControllerDB Linux mappings for Xbox One/Series over Bluetooth
                // use GUIDs beginning 050000005e040000 and raw buttons/axes above.
                if (guid.startsWith("050000005e040000")
                    || name.contains("xbox wireless")
                    || name.contains("xbox series")) {
                    return XBOX_BLUETOOTH;
                }
                return XBOX_USB;
            }
        }

        /**
         * Normalize a trigger from SDL/GLFW range [-1, +1] to [0, 1].
         *   -1.0 (fully released) → 0.0
         *    0.0 (half pressed)   → 0.5
         *   +1.0 (fully pressed)  → 1.0
         *
         * Do NOT use a threshold branch here — it breaks intermediate values.
         */
        private static float sdlTriggerToUnit(float v) {
            return Math.max(0.0f, Math.min(1.0f, (v + 1.0f) * 0.5f));
        }

        private static float clamp(float v) {
            return Math.max(-1.0f, Math.min(1.0f, v));
        }

        private static byte rawButton(ByteBuffer buf, int index) {
            return buf != null && index >= 0 && index < buf.capacity() && buf.get(index) == GLFW.GLFW_PRESS ? (byte) 1 : 0;
        }

        private static float rawAxis(FloatBuffer buf, int index) {
            return buf != null && index >= 0 && index < buf.capacity() ? buf.get(index) : 0.0f;
        }

        boolean sameAs(Snapshot other) {
            if (buttons.length != other.buttons.length || axes.length != other.axes.length) return false;
            for (int i = 0; i < buttons.length; i++) if (buttons[i] != other.buttons[i]) return false;
            for (int i = 0; i < axes.length; i++) if (Math.abs(axes[i] - other.axes[i]) > 0.01f) return false;
            return true;
        }
    }
}
