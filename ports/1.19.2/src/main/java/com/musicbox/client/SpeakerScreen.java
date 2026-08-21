package com.musicbox.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.musicbox.MusicBox;
import com.musicbox.blockentity.SpeakerBlockEntity;
import com.musicbox.client.audio.RadioManager;
import com.musicbox.client.audio.RadioStream;
import com.musicbox.menu.SpeakerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Re-points a placed speaker at a different music box and sets how loud it is. Shares the
 * music box panel texture, since the layout is the same shape: a list, a status strip and a
 * row of controls.
 */
public class SpeakerScreen extends AbstractContainerScreen<SpeakerMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(MusicBox.MOD_ID, "textures/gui/music_box.png");

    private static final int LIST_X = 7;
    private static final int LIST_Y = 18;
    private static final int LIST_WIDTH = 154;
    private static final int ROW_HEIGHT = 14;
    private static final int VISIBLE_ROWS = 9;
    private static final int LIST_HEIGHT = ROW_HEIGHT * VISIBLE_ROWS;

    private static final int SCROLLBAR_X = 163;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int KNOB_HEIGHT = 15;

    private static final int STATUS_WIDTH = 150;

    private int scrollRow;
    private boolean draggingScrollbar;
    private VolumeSlider volumeSlider;
    private Button unpairButton;

    public SpeakerScreen(SpeakerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 200;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;

        SpeakerBlockEntity speaker = speaker();
        float volume = speaker == null ? menu.volume() : speaker.getVolume();

        volumeSlider = addRenderableWidget(new VolumeSlider(leftPos + 7, topPos + 170, 104, 18,
                SpeakerMenu.VOLUME_STEPS, volume,
                step -> sendButton(SpeakerMenu.VOLUME_BUTTON_BASE + step)));
        unpairButton = addRenderableWidget(new Button(leftPos + 115, topPos + 170, 54, 18,
                Component.translatable("gui.musicboxradio.unpair"),
                button -> sendButton(SpeakerMenu.BUTTON_UNPAIR)));

        scrollToSelected();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderTooltip(poseStack, mouseX, mouseY);

        SpeakerBlockEntity speaker = speaker();
        if (speaker != null && volumeSlider != null) {
            volumeSlider.syncTo(speaker.getVolume());
        }
        if (unpairButton != null) {
            unpairButton.active = speaker != null && speaker.isBound();
        }
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        renderBoxRows(poseStack, mouseX, mouseY);
        renderScrollbar(poseStack);
        renderStatus(poseStack);
    }

    private void renderBoxRows(PoseStack poseStack, int mouseX, int mouseY) {
        SpeakerBlockEntity speaker = speaker();
        BlockPos bound = speaker == null ? menu.bound() : speaker.getBoundPos();
        int rows = Math.min(VISIBLE_ROWS, menu.candidates().size() - scrollRow);

        for (int i = 0; i < rows; i++) {
            SpeakerMenu.Candidate candidate = menu.candidates().get(scrollRow + i);
            int rowX = leftPos + LIST_X;
            int rowY = topPos + LIST_Y + i * ROW_HEIGHT;

            boolean selected = candidate.pos().equals(bound);
            boolean hovered = mouseX >= rowX && mouseX < rowX + LIST_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (selected) {
                fill(poseStack, rowX, rowY, rowX + LIST_WIDTH, rowY + ROW_HEIGHT, 0x803C7A3C);
            } else if (hovered) {
                fill(poseStack, rowX, rowY, rowX + LIST_WIDTH, rowY + ROW_HEIGHT, 0x40FFFFFF);
            }

            int colour = selected ? 0xFFFFFF : (hovered ? 0xE0E0E0 : 0xA8A8A8);
            font.draw(poseStack, font.plainSubstrByWidth(describe(candidate), LIST_WIDTH - 10),
                    rowX + 4, rowY + 3, colour);
        }

        if (menu.candidates().isEmpty()) {
            Component empty = Component.translatable("gui.musicboxradio.speaker.no_boxes")
                    .withStyle(ChatFormatting.DARK_GRAY);
            font.draw(poseStack, empty, leftPos + LIST_X + 4, topPos + LIST_Y + 4, 0x808080);
        }
    }

    /** A box's station if it has one, falling back to coordinates so rows are never blank. */
    private String describe(SpeakerMenu.Candidate candidate) {
        BlockPos pos = candidate.pos();
        String where = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        return candidate.label().isEmpty() ? where : candidate.label() + "  (" + where + ")";
    }

    private void renderScrollbar(PoseStack poseStack) {
        int overflow = Math.max(0, menu.candidates().size() - VISIBLE_ROWS);
        int trackX = leftPos + SCROLLBAR_X;
        int trackY = topPos + LIST_Y;
        int knobY = overflow == 0
                ? trackY
                : trackY + Math.round((LIST_HEIGHT - KNOB_HEIGHT) * (scrollRow / (float) overflow));
        int colour = overflow == 0 ? 0xFF555555 : 0xFFBFBFBF;
        fill(poseStack, trackX, knobY, trackX + SCROLLBAR_WIDTH, knobY + KNOB_HEIGHT, colour);
        fill(poseStack, trackX, knobY, trackX + SCROLLBAR_WIDTH - 1, knobY + KNOB_HEIGHT - 1, 0xFF8B8B8B);
    }

    private void renderStatus(PoseStack poseStack) {
        SpeakerBlockEntity speaker = speaker();
        Component line;
        if (speaker == null || !speaker.isBound()) {
            line = Component.translatable("gui.musicboxradio.speaker.unpaired").withStyle(ChatFormatting.DARK_GRAY);
        } else if (!speaker.isPlaying()) {
            line = Component.translatable("gui.musicboxradio.speaker.idle").withStyle(ChatFormatting.DARK_GRAY);
        } else {
            RadioStream stream = RadioManager.get().streamAt(menu.pos());
            if (!ClientConfig.streamingEnabled()) {
                line = Component.translatable("gui.musicboxradio.streaming_disabled").withStyle(ChatFormatting.RED);
            } else if (stream == null) {
                line = Component.literal(speaker.getStationLabel()).withStyle(ChatFormatting.DARK_GRAY);
            } else if (stream.isFailed()) {
                line = Component.translatable("gui.musicboxradio.failed").withStyle(ChatFormatting.RED);
            } else if (stream.isBuffering()) {
                line = Component.translatable("gui.musicboxradio.buffering").withStyle(ChatFormatting.YELLOW);
            } else {
                String title = stream.nowPlaying();
                line = Component.literal(title == null || title.isEmpty() ? speaker.getStationLabel() : title)
                        .withStyle(ChatFormatting.GREEN);
            }
        }

        font.draw(poseStack,
                Language.getInstance().getVisualOrder(font.substrByWidth(line, STATUS_WIDTH)),
                leftPos + 8, topPos + 152, 0xFFFFFF);
    }

    @Override
    protected void renderLabels(PoseStack poseStack, int mouseX, int mouseY) {
        font.draw(poseStack, title, titleLabelX, titleLabelY, 0x404040);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listX = leftPos + LIST_X;
            int listY = topPos + LIST_Y;
            if (mouseX >= listX && mouseX < listX + LIST_WIDTH
                    && mouseY >= listY && mouseY < listY + LIST_HEIGHT) {
                int index = scrollRow + (int) ((mouseY - listY) / ROW_HEIGHT);
                if (index >= 0 && index < menu.candidates().size()) {
                    sendButton(index);
                    return true;
                }
            }

            int trackX = leftPos + SCROLLBAR_X;
            if (mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                    && mouseY >= listY && mouseY < listY + LIST_HEIGHT) {
                draggingScrollbar = true;
                dragScrollbarTo(mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int overflow = Math.max(0, menu.candidates().size() - VISIBLE_ROWS);
        if (overflow > 0) {
            scrollRow = clamp(scrollRow - (int) Math.signum(delta), overflow);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void dragScrollbarTo(double mouseY) {
        int overflow = Math.max(0, menu.candidates().size() - VISIBLE_ROWS);
        if (overflow == 0) {
            return;
        }
        double travel = LIST_HEIGHT - KNOB_HEIGHT;
        double relative = (mouseY - (topPos + LIST_Y) - KNOB_HEIGHT / 2.0D) / travel;
        scrollRow = clamp((int) Math.round(relative * overflow), overflow);
    }

    private void scrollToSelected() {
        BlockPos bound = menu.bound();
        if (bound == null) {
            return;
        }
        for (int i = 0; i < menu.candidates().size(); i++) {
            if (menu.candidates().get(i).pos().equals(bound)) {
                int overflow = Math.max(0, menu.candidates().size() - VISIBLE_ROWS);
                scrollRow = clamp(i - VISIBLE_ROWS / 2, overflow);
                return;
            }
        }
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(max, value));
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Nullable
    private SpeakerBlockEntity speaker() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.pos());
        return be instanceof SpeakerBlockEntity speaker ? speaker : null;
    }
}
