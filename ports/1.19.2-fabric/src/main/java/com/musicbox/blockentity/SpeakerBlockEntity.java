package com.musicbox.blockentity;

import com.musicbox.ClientHooks;
import com.musicbox.menu.SpeakerMenu;
import com.musicbox.registry.ModBlockEntities;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A remote speaker for a music box.
 * <p>
 * The speaker never decides what it is playing. It stores which box it is paired to, and the
 * server copies that box's current station onto it a few times a second; clients then treat
 * the speaker as an ordinary proximity source. Keeping the resolved station on the block
 * means a speaker still works when its box is far away or in another dimension, because the
 * answer travels with the block rather than being looked up by whoever is listening.
 */
public class SpeakerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    /** Four times a second. A station change is imperceptible at this rate. */
    private static final int RESOLVE_INTERVAL = 5;

    private String boundDimension = "";
    @Nullable
    private BlockPos boundPos;
    private float volume = 1.0F;

    // Copied from the paired box by the server; never authored here.
    private String stationLabel = "";
    private String stationUrl = "";
    private boolean sourcePlaying;

    private int sinceResolve;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEAKER, pos, state);
    }

    public boolean isBound() {
        return boundPos != null && !boundDimension.isEmpty();
    }

    @Nullable
    public BlockPos getBoundPos() {
        return boundPos;
    }

    public String getBoundDimension() {
        return boundDimension;
    }

    public String getStationLabel() {
        return stationLabel;
    }

    public String getStationUrl() {
        return stationUrl;
    }

    public boolean isPlaying() {
        return sourcePlaying && !stationUrl.isEmpty();
    }

    public float getVolume() {
        return volume;
    }

    public void bind(ResourceLocation dimension, BlockPos pos) {
        this.boundDimension = dimension.toString();
        this.boundPos = pos.immutable();
        sinceResolve = RESOLVE_INTERVAL;
        sync();
    }

    public void unbind() {
        this.boundDimension = "";
        this.boundPos = null;
        this.stationLabel = "";
        this.stationUrl = "";
        this.sourcePlaying = false;
        sync();
    }

    public void setVolume(float value) {
        this.volume = Math.max(0.0F, Math.min(1.0F, value));
        sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpeakerBlockEntity speaker) {
        if (++speaker.sinceResolve < RESOLVE_INTERVAL) {
            return;
        }
        speaker.sinceResolve = 0;
        speaker.resolve();
    }

    /** Copies the paired box's station across, if we can see it. */
    private void resolve() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!isBound()) {
            apply("", "", false);
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(boundDimension);
        ServerLevel target = id == null || level.getServer() == null
                ? null
                : level.getServer().getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY, id));
        if (target == null) {
            apply("", "", false);
            return;
        }
        if (!target.hasChunkAt(boundPos)) {
            // Nobody can have touched the box while its chunk is unloaded, so the last answer
            // is still the truth. Coasting keeps a remote speaker playing rather than stuttering
            // every time the box's chunk drops.
            return;
        }

        BlockEntity be = target.getBlockEntity(boundPos);
        if (be instanceof MusicBoxBlockEntity box && box.isPlaying()) {
            apply(box.getStationLabel(), box.getStationUrl(), true);
        } else {
            apply("", "", false);
        }
    }

    private void apply(String label, String url, boolean playing) {
        if (stationLabel.equals(label) && stationUrl.equals(url) && sourcePlaying == playing) {
            return;
        }
        stationLabel = label;
        stationUrl = url;
        sourcePlaying = playing;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.isClientSide) {
            ClientHooks.speakerLoaded(this);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            ClientHooks.speakerUnloaded(this);
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
        boundDimension = tag.getString("BoundDimension");
        boundPos = tag.contains("BoundPos") ? BlockPos.of(tag.getLong("BoundPos")) : null;
        volume = tag.contains("Volume") ? tag.getFloat("Volume") : 1.0F;
        stationLabel = tag.getString("StationLabel");
        stationUrl = tag.getString("StationUrl");
        sourcePlaying = tag.getBoolean("SourcePlaying");
    }

    private void writeState(CompoundTag tag) {
        tag.putString("BoundDimension", boundDimension);
        if (boundPos != null) {
            tag.putLong("BoundPos", boundPos.asLong());
        }
        tag.putFloat("Volume", volume);
        tag.putString("StationLabel", stationLabel);
        tag.putString("StationUrl", stationUrl);
        tag.putBoolean("SourcePlaying", sourcePlaying);
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
        return Component.translatable("block.musicboxradio.speaker");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SpeakerMenu(containerId, inventory, worldPosition,
                SpeakerMenu.findNearbyBoxes(inventory.player.level, worldPosition),
                boundPos, volume);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayer player, FriendlyByteBuf buf) {
        SpeakerMenu.writeOpenData(buf, worldPosition,
                SpeakerMenu.findNearbyBoxes(level, worldPosition), boundPos, volume);
    }
}
