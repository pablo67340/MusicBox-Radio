package com.musicbox.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.musicbox.MusicBox;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.client.audio.PairedFeed;
import com.musicbox.client.audio.RadioManager;
import com.musicbox.client.audio.RadioStream;
import com.musicbox.item.HeadphoneAccess;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.network.PairedBoxPayload;
import com.musicbox.station.Station;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public class MusicBoxScreen extends AbstractContainerScreen<MusicBoxMenu> {

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

    private static final int STATUS_WIDTH = 100;

    private int scrollRow;
    private boolean draggingScrollbar;
    private VolumeSlider volumeSlider;
    private Button pairButton;
    private Button playButton;

    public MusicBoxScreen(MusicBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 200;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;

        MusicBoxBlockEntity box = box();
        float volume = box == null ? 1.0F : box.getVolume();

        volumeSlider = new VolumeSlider(leftPos + 7, topPos + 170, 104, 18, volume);
        addRenderableWidget(volumeSlider);

        playButton = addRenderableWidget(new Button(leftPos + 115, topPos + 170, 54, 18,
                Component.translatable("gui.musicboxradio.stop"),
                button -> sendButton(MusicBoxMenu.BUTTON_TOGGLE)));

        pairButton = addRenderableWidget(new Button(leftPos + 113, topPos + 146, 56, 18,
                Component.translatable("gui.musicboxradio.pair"),
                button -> sendButton(MusicBoxMenu.BUTTON_PAIR)));

        scrollToSelected();
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        refreshFromServer();
        renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        renderTooltip(poseStack, mouseX, mouseY);
    }

    /** The box is server-owned, so anyone else's changes have to show up here as they land. */
    private void refreshFromServer() {
        MusicBoxBlockEntity box = box();
        if (box != null && volumeSlider != null && !volumeSlider.dragging) {
            volumeSlider.syncTo(box.getVolume());
        }
        if (playButton != null) {
            boolean playing = box != null && box.isPlaying();
            playButton.setMessage(Component.translatable(
                    playing ? "gui.musicboxradio.stop" : "gui.musicboxradio.play"));
            playButton.active = playing || (box != null && !box.getStationUrl().isEmpty());
        }
        if (pairButton != null && minecraft != null && minecraft.player != null) {
            pairButton.setMessage(Component.translatable(
                    pairedHere() ? "gui.musicboxradio.unpair" : "gui.musicboxradio.pair"));
            pairButton.active = !HeadphoneAccess.findWornOrHeld(minecraft.player).isEmpty();
        }
    }

    private boolean pairedHere() {
        PairedBoxPayload feed = PairedFeed.get();
        if (!feed.paired() || minecraft == null || minecraft.level == null) {
            return false;
        }
        return feed.pos().equals(menu.pos())
                && feed.dimension().equals(minecraft.level.dimension().location().toString());
    }

    @Override
    protected void renderBg(PoseStack poseStack, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        blit(poseStack, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        renderStationRows(poseStack, mouseX, mouseY);
        renderScrollbar(poseStack);
        renderStatus(poseStack);
    }

    private void renderStationRows(PoseStack poseStack, int mouseX, int mouseY) {
        MusicBoxBlockEntity box = box();
        String activeUrl = box == null ? "" : box.getStationUrl();
        int rows = Math.min(VISIBLE_ROWS, menu.stations().size() - scrollRow);

        for (int i = 0; i < rows; i++) {
            int index = scrollRow + i;
            Station station = menu.stations().get(index);
            int rowX = leftPos + LIST_X;
            int rowY = topPos + LIST_Y + i * ROW_HEIGHT;

            boolean selected = !activeUrl.isEmpty() && activeUrl.equals(station.url());
            boolean hovered = mouseX >= rowX && mouseX < rowX + LIST_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (selected) {
                fill(poseStack, rowX, rowY, rowX + LIST_WIDTH, rowY + ROW_HEIGHT, 0x803C7A3C);
            } else if (hovered) {
                fill(poseStack, rowX, rowY, rowX + LIST_WIDTH, rowY + ROW_HEIGHT, 0x40FFFFFF);
            }

            int colour = selected ? 0xFFFFFF : (hovered ? 0xE0E0E0 : 0xA8A8A8);
            String label = font.plainSubstrByWidth(station.label(), LIST_WIDTH - 10);
            font.draw(poseStack, label, rowX + 4, rowY + 3, colour);
        }

        if (menu.stations().isEmpty()) {
            Component empty = Component.translatable("gui.musicboxradio.no_stations")
                    .withStyle(ChatFormatting.DARK_GRAY);
            font.draw(poseStack, empty, leftPos + LIST_X + 4, topPos + LIST_Y + 4, 0x808080);
        }
    }

    private void renderScrollbar(PoseStack poseStack) {
        int overflow = Math.max(0, menu.stations().size() - VISIBLE_ROWS);
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
        MusicBoxBlockEntity box = box();
        if (box == null) {
            return;
        }

        Component line;
        if (!box.isPlaying()) {
            line = Component.translatable("gui.musicboxradio.stopped").withStyle(ChatFormatting.DARK_GRAY);
        } else {
            RadioStream stream = RadioManager.get().streamAt(menu.pos());
            if (!ClientConfig.streamingEnabled()) {
                line = Component.translatable("gui.musicboxradio.streaming_disabled").withStyle(ChatFormatting.RED);
            } else if (stream == null) {
                line = Component.translatable("gui.musicboxradio.out_of_range").withStyle(ChatFormatting.DARK_GRAY);
            } else if (stream.isFailed()) {
                line = Component.translatable("gui.musicboxradio.failed").withStyle(ChatFormatting.RED);
            } else if (stream.isBuffering()) {
                line = Component.translatable("gui.musicboxradio.buffering").withStyle(ChatFormatting.YELLOW);
            } else {
                String title = stream.nowPlaying();
                line = title == null || title.isEmpty()
                        ? Component.literal(box.getStationLabel()).withStyle(ChatFormatting.GREEN)
                        : Component.literal(title).withStyle(ChatFormatting.GREEN);
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
                if (index >= 0 && index < menu.stations().size()) {
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
        int overflow = Math.max(0, menu.stations().size() - VISIBLE_ROWS);
        if (overflow > 0) {
            scrollRow = clamp(scrollRow - (int) Math.signum(delta), overflow);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void dragScrollbarTo(double mouseY) {
        int overflow = Math.max(0, menu.stations().size() - VISIBLE_ROWS);
        if (overflow == 0) {
            return;
        }
        double travel = LIST_HEIGHT - KNOB_HEIGHT;
        double relative = (mouseY - (topPos + LIST_Y) - KNOB_HEIGHT / 2.0D) / travel;
        scrollRow = clamp((int) Math.round(relative * overflow), overflow);
    }

    private void scrollToSelected() {
        MusicBoxBlockEntity box = box();
        if (box == null || box.getStationUrl().isEmpty()) {
            return;
        }
        for (int i = 0; i < menu.stations().size(); i++) {
            if (menu.stations().get(i).url().equals(box.getStationUrl())) {
                int overflow = Math.max(0, menu.stations().size() - VISIBLE_ROWS);
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
    private MusicBoxBlockEntity box() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        BlockEntity be = minecraft.level.getBlockEntity(menu.pos());
        return be instanceof MusicBoxBlockEntity box ? box : null;
    }

    private final class VolumeSlider extends AbstractSliderButton {

        private boolean dragging;

        VolumeSlider(int x, int y, int width, int height, double initial) {
            super(x, y, width, height, Component.empty(), initial);
            updateMessage();
        }

        /** Adopt a volume someone else set, without fighting the hand currently on the slider. */
        void syncTo(float target) {
            double snapped = snap(Math.max(0.0F, Math.min(1.0F, target)));
            if (Math.abs(snapped - value) > 1.0E-4D) {
                value = snapped;
                updateMessage();
            }
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.musicboxradio.volume", Math.round(value * 100.0D)));
        }

        @Override
        protected void applyValue() {
            // Snap to the step size the button-id encoding can actually represent.
            value = snap(value);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            // Dragging fires applyValue every frame; only tell the server once, on release.
            dragging = false;
            sendButton(MusicBoxMenu.volumeButton((float) value));
            super.onRelease(mouseX, mouseY);
        }

        private double snap(double raw) {
            return Math.round(raw * MusicBoxMenu.VOLUME_STEPS) / (double) MusicBoxMenu.VOLUME_STEPS;
        }
    }
}
