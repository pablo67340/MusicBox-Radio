package com.musicbox.blockentity;

import com.musicbox.ClientHooks;
import com.musicbox.block.MusicBoxBlock;
import com.musicbox.menu.MusicBoxMenu;
import com.musicbox.registry.ModBlockEntities;
import com.musicbox.station.Station;
import com.musicbox.station.StationConfig;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MusicBoxBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    private String stationLabel = "";
    private String stationUrl = "";
    private boolean playing;
    private float volume = 1.0F;

    /** Stations added to this box alone, when customStations.scope is BLOCK. */
    private final List<Station> customStations = new ArrayList<>();

    public MusicBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUSIC_BOX, pos, state);
    }

    public String getStationLabel() {
        return stationLabel;
    }

    public String getStationUrl() {
        return stationUrl;
    }

    public boolean isPlaying() {
        return playing && !stationUrl.isEmpty();
    }

    public float getVolume() {
        return volume;
    }

    public List<Station> getCustomStations() {
        return Collections.unmodifiableList(customStations);
    }

    /** @return false if the box is full or already offers that label */
    public boolean addCustomStation(Station station) {
        if (customStations.size() >= StationConfig.maxPerBlock()) {
            return false;
        }
        for (Station existing : StationConfig.combined(customStations)) {
            if (existing.label().equalsIgnoreCase(station.label())) {
                return false;
            }
        }
        customStations.add(station);
        sync();
        return true;
    }

    public void selectStation(Station station) {
        this.stationLabel = station.label();
        this.stationUrl = station.url();
        this.playing = true;
        sync();
    }

    public void setPlaying(boolean value) {
        this.playing = value && !stationUrl.isEmpty();
        sync();
    }

    public void togglePlaying() {
        setPlaying(!playing);
    }

    public void setVolume(float value) {
        this.volume = Math.max(0.0F, Math.min(1.0F, value));
        sync();
    }

    private void sync() {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState current = getBlockState();
        BlockState desired = current.setValue(MusicBoxBlock.PLAYING, isPlaying());
        if (current != desired) {
            // A state change already carries a block update to every tracking client.
            level.setBlock(worldPosition, desired, Block.UPDATE_ALL);
        } else {
            level.sendBlockUpdated(worldPosition, current, current, Block.UPDATE_ALL);
        }
    }

    // setLevel/setRemoved are the vanilla attach and detach points, so the client audio
    // layer is wired up identically on Forge and Fabric. Chunk unloading routes through
    // setRemoved too, via LevelChunk#clearAllBlockEntities.

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.isClientSide) {
            ClientHooks.boxLoaded(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            ClientHooks.boxUnloaded(this);
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeState(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stationLabel = tag.getString("StationLabel");
        stationUrl = tag.getString("StationUrl");
        playing = tag.getBoolean("Playing");
        volume = tag.contains("Volume") ? tag.getFloat("Volume") : 1.0F;

        customStations.clear();
        ListTag custom = tag.getList("CustomStations", Tag.TAG_COMPOUND);
        for (int i = 0; i < custom.size(); i++) {
            CompoundTag entry = custom.getCompound(i);
            Station station = Station.of(entry.getString("Label"), entry.getString("Url"));
            if (station.isValid()) {
                customStations.add(station);
            }
        }
    }

    private void writeState(CompoundTag tag) {
        tag.putString("StationLabel", stationLabel);
        tag.putString("StationUrl", stationUrl);
        tag.putBoolean("Playing", playing);
        tag.putFloat("Volume", volume);

        ListTag custom = new ListTag();
        for (Station station : customStations) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Label", station.label());
            entry.putString("Url", station.url());
            custom.add(entry);
        }
        tag.put("CustomStations", custom);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeState(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.musicboxradio.music_box");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MusicBoxMenu(containerId, inventory, worldPosition,
                StationConfig.combined(customStations),
                StationConfig.mayAddStations(player),
                StationConfig.scope() == StationConfig.Scope.GLOBAL);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        MusicBoxMenu.writeOpenData(buf, worldPosition,
                StationConfig.combined(customStations),
                StationConfig.mayAddStations(player),
                StationConfig.scope() == StationConfig.Scope.GLOBAL);
    }
}
