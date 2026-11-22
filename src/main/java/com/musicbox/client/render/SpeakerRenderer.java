package com.musicbox.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.musicbox.blockentity.SpeakerBlockEntity;
import com.musicbox.client.audio.RadioManager;
import com.musicbox.client.audio.RadioStream;
import com.musicbox.client.audio.SpectrumFeed;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Draws the speaker's driver, pushed out of the cabinet by the low end.
 * <p>
 * The cone travels along the face normal rather than scaling up, because a woofer moving
 * toward you is what the eye actually reads as bass; growing it just looks like a zoom. Throw
 * is a couple of pixels at most, which is roughly the exaggeration a real driver gets in
 * slow motion and is enough to see across a room.
 */
public final class SpeakerRenderer implements BlockEntityRenderer<SpeakerBlockEntity> {

    /** Furthest the cone comes out of the cabinet, in blocks. Two pixels and a bit. */
    private static final float MAX_THROW = 0.14F;

    /** Cone sits a little below centre, matching the well in the block texture. */
    private static final float CENTRE_X = 8.0F;
    private static final float CENTRE_Y = 9.5F;
    private static final float RADIUS = 5.6F;

    private static final int CONE_TINT = 0xFFFFFF;
    private static final int RIM_IDLE = 0x1A1A20;

    public SpeakerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SpeakerBlockEntity speaker, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = speaker.getBlockState();
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

        float bass = 0.0F;
        float[] levels = null;
        if (speaker.isPlaying()) {
            RadioStream stream = RadioManager.get().streamAt(speaker.getBlockPos());
            if (stream != null) {
                SpectrumFeed feed = stream.feed();
                levels = feed.levels();
                bass = feed.bass();
            }
        }

        // Square the level so quiet passages barely move the cone and loud ones snap it out.
        float excursion = MAX_THROW * Mth.clamp(bass * bass, 0.0F, 1.0F);
        float ticks = speaker.getLevel() == null ? 0.0F : speaker.getLevel().getGameTime() + partialTick;

        renderCone(poseStack, buffers, facing, excursion, levels != null, ticks);
        renderMeter(poseStack, buffers, facing.getClockWise(), levels);
        renderMeter(poseStack, buffers, facing.getCounterClockWise(), levels);
    }

    private void renderCone(PoseStack poseStack, MultiBufferSource buffers, Direction facing,
                            float outward, boolean live, float ticks) {
        VertexConsumer cone = buffers.getBuffer(RenderType.entityCutoutNoCull(NeonRender.CONE));

        // Halo first: the same circular art a touch larger and tinted, which rims the driver in
        // neon without the square corners a flat quad behind it would show.
        disc(poseStack, cone, facing, outward, RADIUS + 0.7F, live ? NeonRender.cycleColour(ticks) : RIM_IDLE);
        // Driver itself, a hair in front so it never fights the halo for depth.
        disc(poseStack, cone, facing, outward + 0.003F, RADIUS, CONE_TINT);
    }

    private void disc(PoseStack poseStack, VertexConsumer buffer, Direction facing,
                      float outward, float radius, int tint) {
        poseStack.pushPose();
        NeonRender.toFace(poseStack, facing, outward);
        poseStack.translate(MeterPanel.tx(CENTRE_X), MeterPanel.ty(CENTRE_Y), 0.0D);
        float half = radius / 16.0F;
        NeonRender.quad(poseStack, buffer, -half, -half, half, half, 0.0F, 0.0F, 1.0F, 1.0F, tint, 1.0F);
        poseStack.popPose();
    }

    private void renderMeter(PoseStack poseStack, MultiBufferSource buffers, Direction face,
                             float[] levels) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(NeonRender.WHITE));
        poseStack.pushPose();
        NeonRender.toFace(poseStack, face, 0.0F);
        MeterPanel.render(poseStack, buffer, levels);
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
