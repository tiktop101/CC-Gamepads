package com.tom.ccgamepads.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tom.ccgamepads.CCGamepadsMod;
import com.tom.ccgamepads.GamepadRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Matrix4f;

public class GamepadItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final GamepadItemRenderer INSTANCE = new GamepadItemRenderer(
        Minecraft.getInstance().getBlockEntityRenderDispatcher(),
        Minecraft.getInstance().getEntityModels());
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CCGamepadsMod.MOD_ID, "textures/item/gamepad.png");

    public GamepadItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        if (GamepadClientConfig.render3dGamepad()) {
            apply3dContextTransform(displayContext, poseStack);
            render3d(poseStack, buffer, packedLight, stack, false);
        }
        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (!GamepadClientConfig.render3dGamepad()) return;
        if (!event.getItemStack().is(GamepadRegistry.GAMEPAD_ITEM.get())) return;

        renderFirstPersonCentered(
            event.getPoseStack(),
            event.getMultiBufferSource(),
            event.getPackedLight(),
            event.getHand(),
            event.getInterpolatedPitch(),
            event.getEquipProgress(),
            event.getSwingProgress()
        );
        event.setCanceled(true);
    }

    private static void renderFirstPersonCentered(PoseStack poseStack, MultiBufferSource buffer, int packedLight, InteractionHand hand,
                                                  float pitch, float equipProgress, float swingProgress) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        poseStack.pushPose();
        if (hand == InteractionHand.MAIN_HAND && minecraft.player.getOffhandItem().isEmpty()) {
            float swingRoot = Mth.sqrt(swingProgress);
            float tX = -0.2F * Mth.sin(swingProgress * (float)Math.PI);
            float tZ = -0.4F * Mth.sin(swingRoot * (float)Math.PI);
            poseStack.translate(0.0F, -tX / 2.0F, tZ);

            float pitchAngle = calculateMapTilt(pitch);
            poseStack.translate(0.0F, 0.04F + equipProgress * -1.2F + pitchAngle * -0.5F, -0.72F);
            poseStack.mulPose(Axis.XP.rotationDegrees(pitchAngle * -85.0F));
            if (!minecraft.player.isInvisible()) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                renderMapHand(poseStack, buffer, packedLight, HumanoidArm.RIGHT);
                renderMapHand(poseStack, buffer, packedLight, HumanoidArm.LEFT);
                poseStack.popPose();
            }

            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(swingRoot * (float)Math.PI) * 20.0F));
            poseStack.scale(1.01F, 1.01F, 1.01F);
            poseStack.translate(0.0F, -0.12F, -0.14F);
            render3d(poseStack, buffer, packedLight, minecraft.player.getMainHandItem(), true);
        } else {
            HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? minecraft.player.getMainArm() : minecraft.player.getMainArm().getOpposite();
            float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            poseStack.translate(side * 0.50F, -0.35F + equipProgress * -0.7F, -0.78F);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * -25.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(12.0F));
            poseStack.scale(0.44F, 0.44F, 0.44F);
            render3d(poseStack, buffer, packedLight, minecraft.player.getItemInHand(hand), true);
        }
        poseStack.popPose();
    }

    private static float calculateMapTilt(float pitch) {
        float value = 1.0F - pitch / 45.0F + 0.1F;
        value = Mth.clamp(value, 0.0F, 1.0F);
        return -Mth.cos(value * (float)Math.PI) * 0.5F + 0.5F;
    }

    private static void renderMapHand(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HumanoidArm arm) {
        Minecraft minecraft = Minecraft.getInstance();
        PlayerRenderer renderer = (PlayerRenderer)minecraft.getEntityRenderDispatcher().getRenderer(minecraft.player);
        poseStack.pushPose();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -41.0F));
        poseStack.translate(side * 0.3F, -1.1F, 0.45F);
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, buffer, packedLight, minecraft.player);
        } else {
            renderer.renderLeftHand(poseStack, buffer, packedLight, minecraft.player);
        }
        poseStack.popPose();
    }

    private static void apply3dContextTransform(ItemDisplayContext displayContext, PoseStack poseStack) {
        switch (displayContext) {
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                poseStack.translate(0.0F, 0.08F, 0.0F);
                poseStack.scale(0.32F, 0.32F, 0.32F);
            }
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> poseStack.scale(0.41F, 0.41F, 0.41F);
            case GUI -> {
                poseStack.mulPose(Axis.YP.rotationDegrees(25.0F));
                poseStack.scale(0.54F, 0.54F, 0.54F);
            }
            case GROUND -> poseStack.scale(0.34F, 0.34F, 0.34F);
            default -> poseStack.scale(0.49F, 0.49F, 0.49F);
        }
    }

    private static void render3d(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ItemStack stack, boolean renderHeldCable) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int light = packedLight == 0 ? LightTexture.FULL_BRIGHT : packedLight;

        texturedLayer(poseStack, consumer, -0.018F, light, 72, 72, 72);
        texturedLayer(poseStack, consumer, -0.009F, light, 112, 112, 112);
        texturedLayer(poseStack, consumer, 0.000F, light, 168, 168, 168);
        texturedLayer(poseStack, consumer, 0.009F, light, 218, 218, 218);
        texturedLayer(poseStack, consumer, 0.018F, light, 255, 255, 255);
    }

    private static void texturedLayer(PoseStack poseStack, VertexConsumer consumer, float z, int packedLight, int red, int green, int blue) {
        Matrix4f matrix = poseStack.last().pose();
        float min = -0.5F;
        float max = 0.5F;
        consumer.addVertex(matrix, min, min, z)
            .setColor(red, green, blue, 255)
            .setUv(0.0F, 1.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, max, min, z)
            .setColor(red, green, blue, 255)
            .setUv(1.0F, 1.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, max, max, z)
            .setColor(red, green, blue, 255)
            .setUv(1.0F, 0.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
        consumer.addVertex(matrix, min, max, z)
            .setColor(red, green, blue, 255)
            .setUv(0.0F, 0.0F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
    }

    private static void box(PoseStack poseStack, VertexConsumer consumer, float x, float y, float z, float width, float height, float depth,
                            int packedLight, int red, int green, int blue) {
        float x2 = x + width;
        float y2 = y + height;
        float z2 = z + depth;
        quad(poseStack, consumer, x, y, z, x2, y, z, x2, y2, z, x, y2, z, packedLight, red, green, blue, 0, 0, -1);
        quad(poseStack, consumer, x2, y, z2, x, y, z2, x, y2, z2, x2, y2, z2, packedLight, red, green, blue, 0, 0, 1);
        quad(poseStack, consumer, x, y, z2, x, y, z, x, y2, z, x, y2, z2, packedLight, red, green, blue, -1, 0, 0);
        quad(poseStack, consumer, x2, y, z, x2, y, z2, x2, y2, z2, x2, y2, z, packedLight, red, green, blue, 1, 0, 0);
        quad(poseStack, consumer, x, y, z2, x2, y, z2, x2, y, z, x, y, z, packedLight, red, green, blue, 0, -1, 0);
        quad(poseStack, consumer, x, y2, z, x2, y2, z, x2, y2, z2, x, y2, z2, packedLight, red, green, blue, 0, 1, 0);
    }

    private static void quad(PoseStack poseStack, VertexConsumer consumer,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             int packedLight, int red, int green, int blue, float nx, float ny, float nz) {
        Matrix4f matrix = poseStack.last().pose();
        consumer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, 255).setNormal(poseStack.last(), nx, ny, nz);
        consumer.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, 255).setNormal(poseStack.last(), nx, ny, nz);
        consumer.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, 255).setNormal(poseStack.last(), nx, ny, nz);
        consumer.addVertex(matrix, x4, y4, z4).setColor(red, green, blue, 255).setNormal(poseStack.last(), nx, ny, nz);
    }

    private static void quad(PoseStack poseStack, VertexConsumer consumer, float x1, float y1, float z, float x2, float y2, float z2,
                             int packedLight, int red, int green, int blue) {
        quad(poseStack, consumer, x1, y1, z, x1, y2, z, x2, y2, z2, x2, y1, z2, packedLight, red, green, blue, 0, 0, 1);
        quad(poseStack, consumer, x2, y1, z2, x2, y2, z2, x1, y2, z, x1, y1, z, packedLight, red, green, blue, 0, 0, -1);
    }
}
