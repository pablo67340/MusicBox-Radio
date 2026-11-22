package com.musicbox.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The handful of GUI drawing calls the screens use, over whichever API this Minecraft version
 * draws with. Text is always drawn without a shadow, matching the panel's flat look.
 */
public final class Gfx {

    //? if >=1.20 {
    private final net.minecraft.client.gui.GuiGraphics graphics;

    public Gfx(net.minecraft.client.gui.GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public void fill(int x0, int y0, int x1, int y1, int colour) {
        graphics.fill(x0, y0, x1, y1, colour);
    }

    public void text(Font font, Component text, int x, int y, int colour) {
        graphics.drawString(font, text, x, y, colour, false);
    }

    public void text(Font font, String text, int x, int y, int colour) {
        graphics.drawString(font, text, x, y, colour, false);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int colour) {
        graphics.drawString(font, text, x, y, colour, false);
    }

    /** Draws part of a 256x256 texture. */
    public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        //? if >=1.21.2 {
        /*graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, x, y, u, v, width, height, 256, 256);
        *///?} else {
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(texture, x, y, u, v, width, height);
        //?}
    }
    //?} else {
    /*private final com.mojang.blaze3d.vertex.PoseStack poseStack;

    public Gfx(com.mojang.blaze3d.vertex.PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public void fill(int x0, int y0, int x1, int y1, int colour) {
        net.minecraft.client.gui.GuiComponent.fill(poseStack, x0, y0, x1, y1, colour);
    }

    public void text(Font font, Component text, int x, int y, int colour) {
        font.draw(poseStack, text, x, y, colour);
    }

    public void text(Font font, String text, int x, int y, int colour) {
        font.draw(poseStack, text, x, y, colour);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int colour) {
        font.draw(poseStack, text, x, y, colour);
    }

    public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
        net.minecraft.client.gui.GuiComponent.blit(poseStack, x, y, u, v, width, height, 256, 256);
    }
    *///?}

    public static Button button(Component message, Button.OnPress onPress, int x, int y, int width, int height) {
        //? if >=1.19.3 {
        return Button.builder(message, onPress).bounds(x, y, width, height).build();
        //?} else {
        /*return new Button(x, y, width, height, message, onPress);
        *///?}
    }

    public static void focus(EditBox box, boolean focused) {
        //? if >=1.19.4 {
        box.setFocused(focused);
        //?} else {
        /*box.setFocus(focused);
        *///?}
    }
}
