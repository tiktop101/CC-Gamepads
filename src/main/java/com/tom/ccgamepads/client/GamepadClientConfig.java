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
    private static boolean loaded;
    private static boolean render3dGamepad;
    private static boolean wireCables = true;

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

    public static void load() {
        if (loaded) return;
        loaded = true;
        Path path = path();
        if (!Files.exists(path)) {
            save();
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            render3dGamepad = Boolean.parseBoolean(properties.getProperty(RENDER_3D, "false"));
            wireCables = Boolean.parseBoolean(properties.getProperty(WIRE_CABLES, "true"));
        } catch (IOException e) {
            CCGamepadsMod.warn("Could not load client config: " + e.getMessage());
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty(RENDER_3D, Boolean.toString(render3dGamepad));
        properties.setProperty(WIRE_CABLES, Boolean.toString(wireCables));

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
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("ccgamepads-client.properties");
    }
}
