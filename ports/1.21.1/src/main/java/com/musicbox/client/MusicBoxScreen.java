package com.musicbox.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.musicbox.MusicBox;
import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.client.audio.PairedFeed;
import com.musicbox.client.audio.RadioManager;
import com.musicbox.client.audio.RadioStream;
import com.musicbox.item.HeadphoneAccess;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.network.AddStationPayload;
import com.musicbox.network.PairedBoxPayload;
import com.musicbox.station.Station;
import com.musicbox.station.StationConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class MusicBoxScreen extends AbstractContainerScreen<MusicBoxMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MusicBox.MOD_ID, "textures/gui/music_box.png");

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

    /** While true the station list is covered by the add-a-station form. */
    private boolean addMode;
    private EditBox labelBox;
    private EditBox urlBox;
    private Button saveButton;
    private Button cancelButton;

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

        volumeSlider = new VolumeSlider(leftPos + 7, topPos + 170, 104, 18,
                MusicBoxMenu.VOLUME_STEPS, volume,
                step -> sendButton(MusicBoxMenu.VOLUME_BUTTON_BASE + step));
        addRenderableWidget(volumeSlider);

        playButton = addRenderableWidget(Button.builder(Component.translatable("gui.musicboxradio.stop"),
                        button -> sendButton(MusicBoxMenu.BUTTON_TOGGLE))
                .bounds(leftPos + 115, topPos + 170, 54, 18).build());

        pairButton = addRenderableWidget(Button.builder(Component.translatable("gui.musicboxradio.pair"),
                        button -> sendButton(MusicBoxMenu.BUTTON_PAIR))
                .bounds(leftPos + 113, topPos + 146, 56, 18).build());

        if (menu.canAddStations()) {
            addRenderableWidget(Button.builder(Component.literal("+"), button -> setAddMode(true))
                    .bounds(leftPos + 155, topPos + 3, 14, 14).build());
        }
        initAddForm();

        scrollToSelected();
    }

    private void initAddForm() {
        labelBox = new EditBox(font, leftPos + 12, topPos + 46, 152, 18,
                Component.translatable("gui.musicboxradio.add.label"));
        labelBox.setMaxLength(StationConfig.MAX_LABEL_LENGTH);
        addRenderableWidget(labelBox);

        urlBox = new EditBox(font, leftPos + 12, topPos + 86, 152, 18,
                Component.translatable("gui.musicboxradio.add.url"));
        urlBox.setMaxLength(StationConfig.MAX_URL_LENGTH);
        addRenderableWidget(urlBox);

        saveButton = addRenderableWidget(Button.builder(Component.translatable("gui.musicboxradio.add.save"),
                        button -> submitStation())
                .bounds(leftPos + 12, topPos + 116, 74, 18).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.musicboxradio.add.cancel"),
                        button -> setAddMode(false))
                .bounds(leftPos + 90, topPos + 116, 74, 18).build());

        setAddMode(false);
    }

    private void setAddMode(boolean value) {
        addMode = value;
        labelBox.setVisible(value);
        urlBox.setVisible(value);
        saveButton.visible = value;
        cancelButton.visible = value;

        // The controls behind the form would otherwise still take clicks through it.
        if (volumeSlider != null) {
            volumeSlider.visible = !value;
        }
        if (playButton != null) {
            playButton.visible = !value;
        }
        if (pairButton != null) {
            pairButton.visible = !value;
        }

        if (value) {
            labelBox.setValue("");
            urlBox.setValue("");
            setFocused(labelBox);
        } else {
            labelBox.setFocused(false);
            urlBox.setFocused(false);
            setFocused(null);
        }
    }

    /**
     * The server has the final say on all of this; the checks here only avoid sending an
     * obviously empty request and give instant feedback on the two easiest mistakes.
     */
    private void submitStation() {
        String label = labelBox.getValue().trim();
        String url = urlBox.getValue().trim();
        if (label.isEmpty() || url.isEmpty()) {
            return;
        }
        ClientNetwork.sendAddStation(new AddStationPayload(menu.pos(), label, url));
        setAddMode(false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshFromServer();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** The box is server-owned, so anyone else's changes have to show up here as they land. */
    private void refreshFromServer() {
        if (addMode) {
            return;
        }
        MusicBoxBlockEntity box = box();
        if (box != null && volumeSlider != null) {
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
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        if (addMode) {
            renderAddForm(graphics);
            return;
        }

        renderStationRows(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
        renderStatus(graphics);
    }

    private void renderAddForm(GuiGraphics graphics) {
        graphics.fill(leftPos + 7, topPos + 18, leftPos + 169, topPos + 140, 0xF0101010);

        graphics.drawString(font, Component.translatable("gui.musicboxradio.add.title"),
                leftPos + 12, topPos + 24, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("gui.musicboxradio.add.label"),
                leftPos + 12, topPos + 36, 0xA0A0A0, false);
        graphics.drawString(font, Component.translatable("gui.musicboxradio.add.url"),
                leftPos + 12, topPos + 76, 0xA0A0A0, false);

        Component hint = Component.translatable(menu.isGlobalScope()
                        ? "gui.musicboxradio.add.hint_global"
                        : "gui.musicboxradio.add.hint_block")
                .withStyle(ChatFormatting.DARK_GRAY);
        graphics.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(hint, 152)),
                leftPos + 12, topPos + 108, 0x808080, false);
    }

    private void renderStationRows(GuiGraphics graphics, int mouseX, int mouseY) {
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
                graphics.fill(rowX, rowY, rowX + LIST_WIDTH, rowY + ROW_HEIGHT, 0x803C7A3C);
            } else if (hovered) {
                graphics.fill(rowX, rowY, rowX + LIST_WIDTH, rowY + ROW_HEIGHT, 0x40FFFFFF);
            }

            int colour = selected ? 0xFFFFFF : (hovered ? 0xE0E0E0 : 0xA8A8A8);
            String label = font.plainSubstrByWidth(station.label(), LIST_WIDTH - 10);
            graphics.drawString(font, label, rowX + 4, rowY + 3, colour, false);
        }

        if (menu.stations().isEmpty()) {
            Component empty = Component.translatable("gui.musicboxradio.no_stations")
                    .withStyle(ChatFormatting.DARK_GRAY);
            graphics.drawString(font, empty, leftPos + LIST_X + 4, topPos + LIST_Y + 4, 0x808080, false);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int overflow = Math.max(0, menu.stations().size() - VISIBLE_ROWS);
        int trackX = leftPos + SCROLLBAR_X;
        int trackY = topPos + LIST_Y;
        int knobY = overflow == 0
                ? trackY
                : trackY + Math.round((LIST_HEIGHT - KNOB_HEIGHT) * (scrollRow / (float) overflow));
        int colour = overflow == 0 ? 0xFF555555 : 0xFFBFBFBF;
        graphics.fill(trackX, knobY, trackX + SCROLLBAR_WIDTH, knobY + KNOB_HEIGHT, colour);
        graphics.fill(trackX, knobY, trackX + SCROLLBAR_WIDTH - 1, knobY + KNOB_HEIGHT - 1, 0xFF8B8B8B);
    }

    private void renderStatus(GuiGraphics graphics) {
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

        graphics.drawString(font,
                Language.getInstance().getVisualOrder(font.substrByWidth(line, STATUS_WIDTH)),
                leftPos + 8, topPos + 152, 0xFFFFFF, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (addMode) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                setAddMode(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                boolean toUrl = labelBox.isFocused();
                setFocused(toUrl ? urlBox : labelBox);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitStation();
                return true;
            }
            if (labelBox.canConsumeInput() || urlBox.canConsumeInput()) {
                // Swallow everything else, or the inventory keybind would close the GUI the
                // moment its letter is typed into a field.
                if (getFocused() != null) {
                    getFocused().keyPressed(keyCode, scanCode, modifiers);
                }
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (addMode) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (addMode) {
            return true;
        }
        int overflow = Math.max(0, menu.stations().size() - VISIBLE_ROWS);
        if (overflow > 0) {
            scrollRow = clamp(scrollRow - (int) Math.signum(scrollY), overflow);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

}
