package com.musicbox.menu;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.blockentity.SpeakerBlockEntity;
import com.musicbox.registry.ModBlocks;
import com.musicbox.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Control panel for a placed speaker: which music box it follows, and how loud it is.
 * <p>
 * Pairing normally happens before the block is placed, by using the speaker item on a box.
 * This exists so a speaker already in a wall can be re-pointed without breaking it, which
 * means offering a list of candidates - the player has no way to click a distant box from
 * inside a screen. Like the music box menu it carries no slots and rides on vanilla's
 * button-click packet.
 */
public class SpeakerMenu extends AbstractContainerMenu {

    /** How far to look for boxes to offer. Generous, but still a bounded scan. */
    public static final int SEARCH_RADIUS = 24;

    public static final int MAX_BOX_BUTTONS = 64;

    /** Ids 100..120 set volume in 5% steps. */
    public static final int VOLUME_BUTTON_BASE = 100;
    public static final int VOLUME_STEPS = 20;

    public static final int BUTTON_UNPAIR = 121;

    /** One music box the player could point this speaker at. */
    public record Candidate(BlockPos pos, String label) {
    }

    private final BlockPos pos;
    private final List<Candidate> candidates;
    @Nullable
    private final BlockPos bound;
    private final float volume;
    private final ContainerLevelAccess access;

    public SpeakerMenu(int containerId, Inventory inventory, BlockPos pos,
                       List<Candidate> candidates, @Nullable BlockPos bound, float volume) {
        super(ModMenus.SPEAKER.get(), containerId);
        this.pos = pos;
        this.candidates = candidates;
        this.bound = bound;
        this.volume = volume;
        this.access = inventory.player.level == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(inventory.player.level, pos);
    }

    public SpeakerMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, buf.readBlockPos(), readCandidates(buf),
                buf.readBoolean() ? buf.readBlockPos() : null, buf.readFloat());
    }

    public static void writeOpenData(FriendlyByteBuf buf, BlockPos pos, List<Candidate> candidates,
                                     @Nullable BlockPos bound, float volume) {
        buf.writeBlockPos(pos);
        int count = Math.min(candidates.size(), MAX_BOX_BUTTONS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Candidate candidate = candidates.get(i);
            buf.writeBlockPos(candidate.pos());
            buf.writeUtf(candidate.label(), 128);
        }
        buf.writeBoolean(bound != null);
        if (bound != null) {
            buf.writeBlockPos(bound);
        }
        buf.writeFloat(volume);
    }

    private static List<Candidate> readCandidates(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Candidate> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Candidate(buf.readBlockPos(), buf.readUtf(128)));
        }
        return list;
    }

    /**
     * Music boxes near enough to be worth offering, nearest first.
     * <p>
     * Walks the block entity map of each loaded chunk in range rather than probing positions:
     * the search volume holds six figures of block positions, and all but a handful of them
     * are air.
     */
    public static List<Candidate> findNearbyBoxes(@Nullable Level level, BlockPos origin) {
        List<Candidate> found = new ArrayList<>();
        if (level == null) {
            return found;
        }

        int radiusSqr = SEARCH_RADIUS * SEARCH_RADIUS;
        int chunkRadius = (SEARCH_RADIUS >> 4) + 1;
        ChunkPos centre = new ChunkPos(origin);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x + dx, centre.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof MusicBoxBlockEntity box)) {
                        continue;
                    }
                    if (entry.getKey().distSqr(origin) > radiusSqr) {
                        continue;
                    }
                    found.add(new Candidate(entry.getKey().immutable(), box.getStationLabel()));
                }
            }
        }

        found.sort(Comparator.comparingDouble(candidate -> candidate.pos().distSqr(origin)));
        if (found.size() > MAX_BOX_BUTTONS) {
            found.subList(MAX_BOX_BUTTONS, found.size()).clear();
        }
        return found;
    }

    public BlockPos pos() {
        return pos;
    }

    public List<Candidate> candidates() {
        return candidates;
    }

    @Nullable
    public BlockPos bound() {
        return bound;
    }

    public float volume() {
        return volume;
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
        if (!(be instanceof SpeakerBlockEntity speaker) || !stillValid(player)) {
            return false;
        }

        if (id >= 0 && id < MAX_BOX_BUTTONS) {
            if (id >= candidates.size()) {
                return false;
            }
            // Re-check server side: the client's list is only for display, and a box may have
            // been broken since the menu opened.
            BlockPos target = candidates.get(id).pos();
            if (!(player.level.getBlockEntity(target) instanceof MusicBoxBlockEntity)) {
                return false;
            }
            speaker.bind(player.level.dimension().location(), target);
            return true;
        }
        if (id >= VOLUME_BUTTON_BASE && id <= VOLUME_BUTTON_BASE + VOLUME_STEPS) {
            speaker.setVolume((id - VOLUME_BUTTON_BASE) / (float) VOLUME_STEPS);
            return true;
        }
        if (id == BUTTON_UNPAIR) {
            speaker.unbind();
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
        return stillValid(access, player, ModBlocks.SPEAKER.get());
    }
}
