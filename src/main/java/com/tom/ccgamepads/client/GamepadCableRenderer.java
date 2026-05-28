package com.tom.ccgamepads.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tom.ccgamepads.GamepadBinding;
import com.tom.ccgamepads.GamepadRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

@OnlyIn(Dist.CLIENT)
public final class GamepadCableRenderer {
    private static final int CABLE_SEGMENTS = 18;
    private static final double FIRST_PERSON_FORWARD = 0.35;
    private static final double FIRST_PERSON_DOWN = 0.20;
    private static final double FIRST_PERSON_SIDE = 0.22;
    private static final float CABLE_WIDTH = 0.035F;

    private GamepadCableRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!GamepadClientConfig.render3dGamepad() || !GamepadClientConfig.wireCables()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(new ByteBufferBuilder(1024));
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (Player player : mc.level.players()) {
            renderCableForHand(player, InteractionHand.MAIN_HAND, partialTick, camera, poseStack, consumer);
            renderCableForHand(player, InteractionHand.OFF_HAND, partialTick, camera, poseStack, consumer);
        }
        poseStack.popPose();
        buffer.endBatch(RenderType.debugQuads());
    }

    private static void renderCableForHand(Player player, InteractionHand hand, float partialTick, Vec3 camera,
                                           PoseStack poseStack, VertexConsumer consumer) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(GamepadRegistry.GAMEPAD_ITEM.get())) return;

        GamepadBinding binding = GamepadBinding.get(stack);
        if (binding == null || !binding.dimension().equals(player.level().dimension())) return;

        Vec3 start = handPosition(player, hand, partialTick);
        BlockPos pos = binding.pos();
        Vec3 end = new Vec3(pos.getX() + 0.5, pos.getY() + 0.55, pos.getZ() + 0.5);
        looseCable(poseStack, consumer, start, end, camera, CABLE_WIDTH, 16, 16, 18);
    }

    private static Vec3 handPosition(Player player, InteractionHand hand, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player || !mc.options.getCameraType().isFirstPerson()) {
            Vec3 base = player.getPosition(partialTick).add(0.0, player.isCrouching() ? 1.05 : 1.25, 0.0);
            Vec3 look = player.getViewVector(partialTick);
            Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize();
            double direction = hand == InteractionHand.MAIN_HAND && player.getMainArm() == HumanoidArm.LEFT ? -1.0 : 1.0;
            if (hand == InteractionHand.OFF_HAND) direction = -direction;
            return base.add(side.scale(0.32 * direction)).add(look.scale(0.22));
        }

        Vec3 look = player.getViewVector(partialTick);
        Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 eye = player.getEyePosition(partialTick);

        double direction = hand == InteractionHand.MAIN_HAND ? FIRST_PERSON_SIDE : -FIRST_PERSON_SIDE;
        return eye
            .add(look.scale(FIRST_PERSON_FORWARD))
            .add(up.scale(-FIRST_PERSON_DOWN))
            .add(right.scale(direction));
    }

    private static void looseCable(PoseStack poseStack, VertexConsumer consumer, Vec3 start, Vec3 end, Vec3 camera, float width,
                                   int red, int green, int blue) {
        double distance = start.distanceTo(end);
        if (distance < 0.01) return;

        double sag = Mth.clamp(distance * 0.075, 0.08, 0.85);
        Vec3 previous = start;
        for (int i = 1; i <= CABLE_SEGMENTS; i++) {
            float t = (float)i / CABLE_SEGMENTS;
            Vec3 point = start.lerp(end, t).add(0.0, -Math.sin(t * Math.PI) * sag, 0.0);
            cableSegment(poseStack, consumer, previous, point, camera, width, red, green, blue);
            previous = point;
        }
    }

    private static void cableSegment(PoseStack poseStack, VertexConsumer consumer, Vec3 start, Vec3 end, Vec3 camera,
                                     float width, int red, int green, int blue) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 0.0001) return;
        Vec3 side = direction.cross(camera.subtract(start)).normalize();
        if (side.lengthSqr() < 0.0001) side = new Vec3(0.0, 1.0, 0.0);
        side = side.scale(width);
        Vec3 a = start.add(side);
        Vec3 b = start.subtract(side);
        Vec3 c = end.subtract(side);
        Vec3 d = end.add(side);
        Matrix4f matrix = poseStack.last().pose();
        consumer.addVertex(matrix, (float)a.x, (float)a.y, (float)a.z).setColor(red, green, blue, 255);
        consumer.addVertex(matrix, (float)b.x, (float)b.y, (float)b.z).setColor(red, green, blue, 255);
        consumer.addVertex(matrix, (float)c.x, (float)c.y, (float)c.z).setColor(red, green, blue, 255);
        consumer.addVertex(matrix, (float)d.x, (float)d.y, (float)d.z).setColor(red, green, blue, 255);
    }
}
