package com.tom.ccgamepads.client;

import com.tom.ccgamepads.CCGamepadsMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@OnlyIn(Dist.CLIENT)
public final class GamepadClientConfig {
    private static final String RENDER_3D = "render3dGamepad";
    private static final String WIRE_CABLES = "wireCables";
    private static final String CONTROLLER_NAME = "controllerName";
    private static final String CONTROLLER_GUID = "controllerGuid";
    private static boolean loaded;
    private static boolean render3dGamepad;
    private static boolean wireCables = true;
    private static String controllerName = "";
    private static String controllerGuid = "";
    private static String selectedControllerName = "";
    private static String selectedControllerGuid = "";
    private static boolean selectedControllerAuto = true;

    private GamepadClientConfig() {
    }

    public static boolean render3dGamepad() {
        load();
        return render3dGamepad;
    }

    public static void setRender3dGamepad(boolean enabled) {
        load();
        render3dGamepad = enabled;
        save();
    }

    public static boolean wireCables() {
        load();
        return wireCables;
    }

    public static void setWireCables(boolean enabled) {
        load();
        wireCables = enabled;
        save();
    }

    public static String controllerName() {
        load();
        return controllerName;
    }

    public static String controllerGuid() {
        load();
        return controllerGuid;
    }

    public static boolean hasControllerPreference() {
        load();
        return !controllerGuid.isBlank() || !controllerName.isBlank();
    }

    public static boolean isPreferredController(String name, String guid) {
        load();
        if (!controllerGuid.isBlank() && controllerGuid.equals(guid)) return true;
        return controllerGuid.isBlank() && !controllerName.isBlank() && controllerName.equals(name);
    }

    public static void selectAutoController() {
        load();
        selectedControllerName = "";
        selectedControllerGuid = "";
        selectedControllerAuto = true;
    }

    public static void selectController(String name, String guid) {
        load();
        selectedControllerName = name == null ? "" : name;
        selectedControllerGuid = guid == null ? "" : guid;
        selectedControllerAuto = false;
    }

    public static boolean isAutoControllerSelected() {
        load();
        return selectedControllerAuto;
    }

    public static String selectedControllerName() {
        load();
        return selectedControllerName;
    }

    public static String selectedControllerGuid() {
        load();
        return selectedControllerGuid;
    }

    public static boolean isSelectedController(String name, String guid) {
        load();
        if (selectedControllerAuto) return false;
        if (!selectedControllerGuid.isBlank() && selectedControllerGuid.equals(guid)) return true;
        return selectedControllerGuid.isBlank() && !selectedControllerName.isBlank() && selectedControllerName.equals(name);
    }

    public static void saveSelectedControllerAsDefault() {
        load();
        controllerName = selectedControllerAuto ? "" : selectedControllerName;
        controllerGuid = selectedControllerAuto ? "" : selectedControllerGuid;
        save();
    }

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = path();
        migrateOldPath(path);
        if (!Files.exists(path)) {
            save();
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            render3dGamepad = Boolean.parseBoolean(properties.getProperty(RENDER_3D, "false"));
            wireCables = Boolean.parseBoolean(properties.getProperty(WIRE_CABLES, "true"));
            controllerName = properties.getProperty(CONTROLLER_NAME, "");
            controllerGuid = properties.getProperty(CONTROLLER_GUID, "");
            selectedControllerName = controllerName;
            selectedControllerGuid = controllerGuid;
            selectedControllerAuto = !hasControllerPreferenceLoaded();
        } catch (IOException e) {
            CCGamepadsMod.warn("Could not load client config: " + e.getMessage());
        }
    }

    private static boolean hasControllerPreferenceLoaded() {
        return !controllerGuid.isBlank() || !controllerName.isBlank();
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty(RENDER_3D, Boolean.toString(render3dGamepad));
        properties.setProperty(WIRE_CABLES, Boolean.toString(wireCables));
        properties.setProperty(CONTROLLER_NAME, controllerName);
        properties.setProperty(CONTROLLER_GUID, controllerGuid);

        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "CC: Gamepads client options");
            }
        } catch (IOException e) {
            CCGamepadsMod.warn("Could not save client config: " + e.getMessage());
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath()
            .resolve("config")
            .resolve(CCGamepadsMod.MOD_ID)
            .resolve("client.txt");
    }

    private static Path oldPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("ccgamepads-client.properties");
    }

    private static void migrateOldPath(Path path) {
        Path oldPath = oldPath();
        if (Files.exists(path) || !Files.exists(oldPath)) return;
        try {
            Files.createDirectories(path.getParent());
            Files.copy(oldPath, path);
        } catch (IOException e) {
            CCGamepadsMod.warn("Could not migrate old client config: " + e.getMessage());
        }
    }
}
