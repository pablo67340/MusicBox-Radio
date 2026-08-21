package com.musicbox.menu;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.item.HeadphoneAccess;
import com.musicbox.registry.ModBlocks;
import com.musicbox.registry.ModMenus;
import com.musicbox.station.Station;
import com.musicbox.station.StationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Control panel for a music box. It holds no slots - every interaction rides on vanilla's
 * {@code ServerboundContainerButtonClickPacket}, so the mod needs no custom networking at all.
 * Button ids are packed into the small range that packet allows.
 */
public class MusicBoxMenu extends AbstractContainerMenu {

    /** Ids 0..99 select the station at that index. */
    public static final int MAX_STATION_BUTTONS = 100;

    /** Ids 100..120 set volume in 5% steps. */
    public static final int VOLUME_BUTTON_BASE = 100;
    public static final int VOLUME_STEPS = 20;

    public static final int BUTTON_STOP = 121;
    public static final int BUTTON_TOGGLE = 122;
    public static final int BUTTON_PAIR = 123;

    private final BlockPos pos;
    private final List<Station> stations;
    private final boolean canAddStations;
    private final boolean globalScope;
    private final ContainerLevelAccess access;

    public MusicBoxMenu(int containerId, Inventory inventory, BlockPos pos, List<Station> stations,
                        boolean canAddStations, boolean globalScope) {
        super(ModMenus.MUSIC_BOX.get(), containerId);
        this.pos = pos;
        this.stations = stations;
        this.canAddStations = canAddStations;
        this.globalScope = globalScope;
        this.access = inventory.player.level == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(inventory.player.level, pos);
    }

    public MusicBoxMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), readStations(buf),
                buf.readBoolean(), buf.readBoolean());
    }

    public static void writeOpenData(FriendlyByteBuf buf, BlockPos pos, List<Station> stations,
                                     boolean canAddStations, boolean globalScope) {
        buf.writeBlockPos(pos);
        int count = Math.min(stations.size(), MAX_STATION_BUTTONS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Station station = stations.get(i);
            buf.writeUtf(station.label(), 128);
            buf.writeUtf(station.url(), 512);
        }
        buf.writeBoolean(canAddStations);
        buf.writeBoolean(globalScope);
    }

    private static List<Station> readStations(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Station> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Station(buf.readUtf(128), buf.readUtf(512)));
        }
        return list;
    }

    public BlockPos pos() {
        return pos;
    }

    public List<Station> stations() {
        return stations;
    }

    /** Whether this player may use the + button, decided by the server when the menu opened. */
    public boolean canAddStations() {
        return canAddStations;
    }

    /** True when an added station lands in stations.json for everyone rather than on this box. */
    public boolean isGlobalScope() {
        return globalScope;
    }

    /** Lets the screen show a station the server accepted without waiting for a menu reopen. */
    public void appendStation(Station station) {
        if (stations.size() < MAX_STATION_BUTTONS) {
            stations.add(station);
        }
    }

    public static int volumeButton(float volume) {
        int step = Math.round(Math.max(0.0F, Math.min(1.0F, volume)) * VOLUME_STEPS);
        return VOLUME_BUTTON_BASE + step;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level.isClientSide) {
            return true;
        }
        BlockEntity be = player.level.getBlockEntity(pos);
        if (!(be instanceof MusicBoxBlockEntity box) || !stillValid(player)) {
            return false;
        }

        if (id >= 0 && id < MAX_STATION_BUTTONS) {
            // The client's copy of the list is only for display; resolve against the server
            // config plus this box's own saved additions, so a spoofed packet cannot make the
            // box play a URL that is not already server-side data.
            Station station = StationConfig.resolve(id, box.getCustomStations());
            if (station == null) {
                return false;
            }
            box.selectStation(station);
            return true;
        }
        if (id >= VOLUME_BUTTON_BASE && id <= VOLUME_BUTTON_BASE + VOLUME_STEPS) {
            box.setVolume((id - VOLUME_BUTTON_BASE) / (float) VOLUME_STEPS);
            return true;
        }
        if (id == BUTTON_STOP) {
            box.setPlaying(false);
            return true;
        }
        if (id == BUTTON_TOGGLE) {
            box.togglePlaying();
            return true;
        }
        if (id == BUTTON_PAIR) {
            HeadphoneAccess.announce(player,
                    HeadphoneAccess.togglePairing(player, player.level.dimension().location(), pos), pos);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.MUSIC_BOX.get());
    }
}
