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

public class MusicBoxBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    private String stationLabel = "";
    private String stationUrl = "";
    private boolean playing;
    private float volume = 1.0F;

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
    }

    private void writeState(CompoundTag tag) {
        tag.putString("StationLabel", stationLabel);
        tag.putString("StationUrl", stationUrl);
        tag.putBoolean("Playing", playing);
        tag.putFloat("Volume", volume);
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
        return new MusicBoxMenu(containerId, inventory, worldPosition, StationConfig.stations());
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        MusicBoxMenu.writeOpenData(buf, worldPosition, StationConfig.stations());
    }
}
