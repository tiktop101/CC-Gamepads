package com.tom.ccgamepads.client;

import com.tom.ccgamepads.CCGamepadsMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GamepadConfigScreen extends Screen {
    private final Screen parent;

    public GamepadConfigScreen(Screen parent) {
        super(Component.translatable("config." + CCGamepadsMod.MOD_ID + ".title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 130;
        int y = this.height / 4;
        this.addRenderableWidget(CycleButton.onOffBuilder(GamepadClientConfig.render3dGamepad())
            .create(x, y, 260, 20, Component.translatable("config.ccgamepads.render3d_gamepad"),
                (button, value) -> GamepadClientConfig.setRender3dGamepad(value)));
        this.addRenderableWidget(CycleButton.onOffBuilder(GamepadClientConfig.wireCables())
            .create(x, y + 26, 260, 20, Component.translatable("config.ccgamepads.wire_cables"),
                (button, value) -> GamepadClientConfig.setWireCables(value)));

        this.addRenderableWidget(Button.builder(controllerButtonLabel(), button -> this.minecraft.setScreen(new GamepadControllerSelectScreen(this)))
            .bounds(x, y + 52, 260, 20)
            .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.minecraft.setScreen(this.parent))
            .bounds(x, this.height / 4 + 100, 260, 20)
            .build());
    }

    private static Component controllerButtonLabel() {
        String value = GamepadClientConfig.isAutoControllerSelected()
            ? Component.translatable("config.ccgamepads.controller.auto").getString()
            : GamepadControllerSelectScreen.controllerLabel(GamepadClientConfig.selectedControllerName(), GamepadClientConfig.selectedControllerGuid());
        return Component.translatable("config.ccgamepads.controller.button", value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
