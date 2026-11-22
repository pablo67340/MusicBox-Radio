package com.musicbox.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.musicbox.MusicBox;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Shared drawing for the animated block faces.
 * <p>
 * Everything here works in "face space": the origin sits at the middle of one block face
 * with x and y running -0.5 to 0.5 across it and z pointing out of the block, which means
 * a panel can be laid out once in flat 2D and then stamped onto whichever side it belongs
 * on. Quads are drawn at full brightness so the neon reads at night, which is the whole
 * point of it.
 */
public final class NeonRender {

    public static final ResourceLocation WHITE =
            MusicBox.id("textures/block/white.png");
    public static final ResourceLocation VINYL =
            MusicBox.id("textures/block/vinyl.png");
    public static final ResourceLocation CONE =
            MusicBox.id("textures/block/speaker_cone.png");

    public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    /** Lifts a panel off the block face so it does not fight the block texture for depth. */
    private static final float LIFT = 0.002F;

    /** Meter gradient, bottom to top, taken from the classic teal-to-magenta LED look. */
    private static final int[] GRADIENT = {0x00E5FF, 0x1E7BFF, 0x7C4DFF, 0xFF3DA6};

    private NeonRender() {
    }

    /**
     * Moves the pose to the middle of one face, looking straight at it.
     * <p>
     * Call inside {@code poseStack.pushPose()}. Afterwards x and y span the face and +z
     * points away from the block.
     */
    public static void toFace(PoseStack poseStack, Direction face, float outward) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        if (face == Direction.UP) {
            rotateX(poseStack, -90.0F);
        } else if (face == Direction.DOWN) {
            rotateX(poseStack, 90.0F);
        } else {
            rotateY(poseStack, -face.toYRot());
        }
        poseStack.translate(0.0D, 0.0D, 0.5D + LIFT + outward);
    }

    /** Axis-aligned quad in face space, facing the viewer. */
    public static void quad(PoseStack poseStack, VertexConsumer buffer,
                            float x0, float y0, float x1, float y1,
                            float u0, float v0, float u1, float v1, int rgb, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        float red = (rgb >> 16 & 0xFF) / 255.0F;
        float green = (rgb >> 8 & 0xFF) / 255.0F;
        float blue = (rgb & 0xFF) / 255.0F;

        vertex(pose, buffer, x0, y0, u0, v1, red, green, blue, alpha);
        vertex(pose, buffer, x1, y0, u1, v1, red, green, blue, alpha);
        vertex(pose, buffer, x1, y1, u1, v0, red, green, blue, alpha);
        vertex(pose, buffer, x0, y1, u0, v0, red, green, blue, alpha);
    }

    /** Solid colour quad, using the single white pixel as its texture. */
    public static void fill(PoseStack poseStack, VertexConsumer buffer,
                            float x0, float y0, float x1, float y1, int rgb, float alpha) {
        quad(poseStack, buffer, x0, y0, x1, y1, 0.25F, 0.25F, 0.75F, 0.75F, rgb, alpha);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y,
                               float u, float v, float red, float green, float blue, float alpha) {
        //? if >=1.21 {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        //?} else {
        /*buffer.vertex(pose.pose(), x, y, 0.0F)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(pose.normal(), 0.0F, 0.0F, 1.0F)
                .endVertex();
        *///?}
    }

    public static void rotateX(PoseStack poseStack, float degrees) {
        //? if >=1.19.3 {
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(degrees));
        //?} else {
        /*poseStack.mulPose(com.mojang.math.Vector3f.XP.rotationDegrees(degrees));
        *///?}
    }

    public static void rotateY(PoseStack poseStack, float degrees) {
        //? if >=1.19.3 {
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(degrees));
        //?} else {
        /*poseStack.mulPose(com.mojang.math.Vector3f.YP.rotationDegrees(degrees));
        *///?}
    }

    public static void rotateZ(PoseStack poseStack, float degrees) {
        //? if >=1.19.3 {
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
        //?} else {
        /*poseStack.mulPose(com.mojang.math.Vector3f.ZP.rotationDegrees(degrees));
        *///?}
    }

    /** Meter colour for a segment, 0 at the bottom of the bar and 1 at the top. */
    public static int meterColour(float height) {
        float scaled = Mth.clamp(height, 0.0F, 1.0F) * (GRADIENT.length - 1);
        int index = (int) scaled;
        if (index >= GRADIENT.length - 1) {
            return GRADIENT[GRADIENT.length - 1];
        }
        return lerpColour(GRADIENT[index], GRADIENT[index + 1], scaled - index);
    }

    public static int lerpColour(int from, int to, float t) {
        int red = channel(from >> 16 & 0xFF, to >> 16 & 0xFF, t);
        int green = channel(from >> 8 & 0xFF, to >> 8 & 0xFF, t);
        int blue = channel(from & 0xFF, to & 0xFF, t);
        return red << 16 | green << 8 | blue;
    }

    private static int channel(int from, int to, float t) {
        return Mth.clamp(Math.round(from + (to - from) * t), 0, 255);
    }

    /** Slow neon hue cycle shared by every animated part, so they stay in step. */
    public static int cycleColour(float ticks) {
        return Mth.hsvToRgb((ticks * 0.004F) % 1.0F, 0.75F, 1.0F) & 0xFFFFFF;
    }
}
