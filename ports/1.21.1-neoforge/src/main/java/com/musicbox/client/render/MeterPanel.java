package com.musicbox.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.musicbox.client.audio.Spectrum;
import net.minecraft.util.Mth;

/**
 * The segmented spectrum display, laid out in face space.
 * <p>
 * Positions are given in texels so the panel lines up with the hand-drawn block faces, and
 * the segments are deliberately chunky: a smooth bar would look out of place next to
 * sixteen-pixel textures, and stepped LEDs read better at a distance anyway.
 */
public final class MeterPanel {

    private static final int SEGMENTS = 8;

    /** Depth between stacked layers of the panel, in blocks. Invisible, but enough to sort. */
    private static final float LAYER = 0.0012F;

    private static final int PANEL = 0x07090E;
    private static final int BEZEL = 0x1B1F28;
    private static final int UNLIT = 0x121822;

    /** Bars occupy texels 2 to 14 across, 3 to 13 down. */
    private static final float BAR_LEFT = 2.0F;
    private static final float BAR_PITCH = 2.5F;
    private static final float BAR_WIDTH = 2.0F;
    private static final float BASELINE = 13.0F;
    private static final float TRAVEL = 10.0F;

    private MeterPanel() {
    }

    /**
     * @param levels band levels in 0..1, or null when nothing is playing
     */
    public static void render(PoseStack poseStack, VertexConsumer buffer, float[] levels) {
        // Each layer sits slightly proud of the one beneath. Stacking them on a single plane
        // leaves the depth test to break ties, which shows up as diagonal moire across the
        // segments rather than as a clean overlay.
        rect(poseStack, buffer, 1.0F, 2.0F, 15.0F, 14.0F, BEZEL, 1.0F);
        poseStack.translate(0.0D, 0.0D, LAYER);
        rect(poseStack, buffer, 1.5F, 2.5F, 14.5F, 13.5F, PANEL, 1.0F);
        poseStack.translate(0.0D, 0.0D, LAYER);

        float segment = TRAVEL / SEGMENTS;
        for (int band = 0; band < Spectrum.BANDS; band++) {
            float level = levels == null ? 0.0F : Mth.clamp(levels[band], 0.0F, 1.0F);
            // Round up so any audible signal lights at least the bottom LED.
            int lit = (int) Math.ceil(level * SEGMENTS - 0.001F);
            float left = BAR_LEFT + band * BAR_PITCH;

            for (int i = 0; i < SEGMENTS; i++) {
                float bottom = BASELINE - i * segment;
                float top = bottom - segment + 0.35F;
                boolean on = i < lit;
                int colour = on ? NeonRender.meterColour(i / (float) (SEGMENTS - 1)) : UNLIT;
                rect(poseStack, buffer, left, top, left + BAR_WIDTH, bottom, colour, 1.0F);
            }
        }
    }

    /** Rectangle given in texture-style texels: y0 is the top edge, y1 the bottom. */
    static void rect(PoseStack poseStack, VertexConsumer buffer,
                     float x0, float y0, float x1, float y1, int rgb, float alpha) {
        NeonRender.fill(poseStack, buffer, tx(x0), ty(y1), tx(x1), ty(y0), rgb, alpha);
    }

    static float tx(float texel) {
        return texel / 16.0F - 0.5F;
    }

    /** Texture rows count downward, face space counts up, so this one inverts. */
    static float ty(float texel) {
        return 0.5F - texel / 16.0F;
    }
}
