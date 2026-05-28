package com.tom.ccgamepads.client;

import com.tom.ccgamepads.CCGamepadsMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class GamepadControllerSelectScreen extends Screen {
    private static final ControllerChoice AUTO = new ControllerChoice("", "", false);
    private final Screen parent;
    private int page;

    public GamepadControllerSelectScreen(Screen parent) {
        super(Component.translatable("config." + CCGamepadsMod.MOD_ID + ".controller.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(320, this.width - 40);
        int x = this.width / 2 - buttonWidth / 2;
        int top = 46;
        int bottomControlsTop = this.height - 56;
        int rows = Math.max(1, (bottomControlsTop - top) / 24);

        List<ControllerChoice> choices = controllerChoices();
        int maxPage = Math.max(0, (choices.size() - 1) / rows);
        if (page > maxPage) page = maxPage;

        int start = page * rows;
        int end = Math.min(choices.size(), start + rows);
        for (int i = start; i < end; i++) {
            ControllerChoice choice = choices.get(i);
            int y = top + (i - start) * 24;
            this.addRenderableWidget(Button.builder(Component.literal(choice.buttonLabel()), button -> {
                if (choice.auto()) {
                    GamepadClientConfig.selectAutoController();
                } else {
                    GamepadClientConfig.selectController(choice.name(), choice.guid());
                }
                GamepadClientBridge.requestControllerListRefresh();
                this.rebuildWidgets();
            }).bounds(x, y, buttonWidth, 20).build());
        }

        int smallWidth = (buttonWidth - 8) / 3;
        Button previous = Button.builder(Component.literal("<"), button -> {
            page--;
            this.rebuildWidgets();
        }).bounds(x, bottomControlsTop, smallWidth, 20).build();
        previous.active = page > 0;
        this.addRenderableWidget(previous);

        this.addRenderableWidget(Button.builder(Component.translatable("config.ccgamepads.controller.set_default"), button -> {
            GamepadClientConfig.saveSelectedControllerAsDefault();
            GamepadClientBridge.requestControllerListRefresh();
            this.rebuildWidgets();
        }).bounds(x + smallWidth + 4, bottomControlsTop, smallWidth, 20).build());

        Button next = Button.builder(Component.literal(">"), button -> {
            page++;
            this.rebuildWidgets();
        }).bounds(x + (smallWidth + 4) * 2, bottomControlsTop, smallWidth, 20).build();
        next.active = page < maxPage;
        this.addRenderableWidget(next);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.minecraft.setScreen(this.parent))
            .bounds(x, bottomControlsTop + 26, buttonWidth, 20)
            .build());
    }

    private static List<ControllerChoice> controllerChoices() {
        List<ControllerChoice> choices = new ArrayList<>();
        choices.add(AUTO);

        for (GamepadClientBridge.ClientControllerInfo controller : GamepadClientBridge.availableControllers()) {
            ControllerChoice choice = new ControllerChoice(controller.name(), controller.guid(), false);
            if (!containsController(choices, choice)) choices.add(choice);
        }

        if (GamepadClientConfig.hasControllerPreference()) {
            ControllerChoice preferred = new ControllerChoice(GamepadClientConfig.controllerName(), GamepadClientConfig.controllerGuid(), true);
            if (!containsController(choices, preferred)) choices.add(preferred);
        }

        return choices;
    }

    private static boolean containsController(List<ControllerChoice> choices, ControllerChoice candidate) {
        for (ControllerChoice choice : choices) {
            if (choice.sameController(candidate)) return true;
        }
        return false;
    }

    public static String controllerLabel(String name, String guid) {
        return name == null || name.isBlank() ? guid : name;
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

    private record ControllerChoice(String name, String guid, boolean unavailable) {
        boolean auto() {
            return name.isBlank() && guid.isBlank();
        }

        boolean selected() {
            return auto()
                ? GamepadClientConfig.isAutoControllerSelected()
                : GamepadClientConfig.isSelectedController(name, guid);
        }

        boolean savedDefault() {
            return auto()
                ? !GamepadClientConfig.hasControllerPreference()
                : GamepadClientConfig.isPreferredController(name, guid);
        }

        boolean sameController(ControllerChoice other) {
            if (auto() || other.auto()) return auto() == other.auto();
            if (!guid.isBlank() && !other.guid.isBlank()) return guid.equals(other.guid);
            return name.equals(other.name);
        }

        String buttonLabel() {
            String label = auto()
                ? Component.translatable("config.ccgamepads.controller.auto").getString()
                : controllerLabel(name, guid);
            if (unavailable) label += " (" + Component.translatable("config.ccgamepads.controller.unavailable").getString() + ")";
            if (savedDefault()) label += " [" + Component.translatable("config.ccgamepads.controller.default").getString() + "]";
            return selected() ? "> " + label : label;
        }
    }
}
