package com.musicbox.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.client.audio.RadioManager;
import com.musicbox.client.audio.RadioStream;
import com.musicbox.client.audio.SpectrumFeed;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the parts of a music box that move: the record on the front and a spectrum
 * display on each side.
 * <p>
 * The band levels come from the stream this client is actually playing for this box, so a
 * box you cannot hear sits dark rather than miming to music. That also means the meter is
 * showing the same audio reaching your ears, not the audio queued up behind it.
 */
public final class MusicBoxRenderer implements BlockEntityRenderer<MusicBoxBlockEntity> {

    /** Degrees per tick. Roughly a third of a turn a second, close to a 33 RPM record. */
    private static final float SPIN = 6.0F;

    private static final float DISC_CENTRE_X = 6.5F;
    private static final float DISC_CENTRE_Y = 8.5F;
    private static final float DISC_RADIUS = 4.5F;

    private static final int DISC_IDLE = 0x9AA0B4;

    public MusicBoxRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MusicBoxBlockEntity box, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = box.getBlockState();
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean playing = box.isPlaying();

        float[] levels = null;
        if (playing) {
            RadioStream stream = RadioManager.get().streamAt(box.getBlockPos());
            if (stream != null) {
                SpectrumFeed feed = stream.feed();
                levels = feed.levels();
            }
        }

        float ticks = box.getLevel() == null ? 0.0F : box.getLevel().getGameTime() + partialTick;

        renderDisc(poseStack, buffers, facing, playing, ticks);
        renderMeter(poseStack, buffers, facing.getClockWise(), levels);
        renderMeter(poseStack, buffers, facing.getCounterClockWise(), levels);
    }

    private void renderDisc(PoseStack poseStack, MultiBufferSource buffers, Direction facing,
                            boolean playing, float ticks) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(NeonRender.VINYL));
        int tint = playing ? NeonRender.cycleColour(ticks) : DISC_IDLE;

        poseStack.pushPose();
        NeonRender.toFace(poseStack, facing, 0.0F);
        poseStack.translate(MeterPanel.tx(DISC_CENTRE_X), MeterPanel.ty(DISC_CENTRE_Y), 0.0D);
        if (playing) {
            poseStack.mulPose(Vector3f.ZP.rotationDegrees(-ticks * SPIN));
        }

        float half = DISC_RADIUS / 16.0F;
        NeonRender.quad(poseStack, buffer, -half, -half, half, half, 0.0F, 0.0F, 1.0F, 1.0F, tint, 1.0F);
        poseStack.popPose();
    }

    private void renderMeter(PoseStack poseStack, MultiBufferSource buffers, Direction face,
                             @Nullable float[] levels) {
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
