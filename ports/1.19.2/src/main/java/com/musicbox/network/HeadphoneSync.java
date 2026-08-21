package com.musicbox.network;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.item.HeadphoneAccess;
import com.musicbox.item.HeadphonesItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Keeps every headphone wearer's client told what their paired box is playing.
 * <p>
 * Runs on the server, so the paired listener and the people standing next to the box are
 * working from the same answer, and a client cannot decide for itself what to stream.
 * Loader-independent: the transport is passed in.
 */
public final class HeadphoneSync {

    /** Four times a second is imperceptible for a station change and costs almost nothing. */
    public static final int TICK_INTERVAL = 5;

    private static final Map<UUID, PairedBoxPayload> SENT = new HashMap<>();

    private HeadphoneSync() {
    }

    public static void tick(MinecraftServer server, BiConsumer<ServerPlayer, PairedBoxPayload> sender) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            online.add(id);
            PairedBoxPayload last = SENT.get(id);
            PairedBoxPayload payload = resolve(server, player, last);
            if (payload == null) {
                // Paired box is in an unloaded chunk, so nobody can have changed it. The last
                // state we sent is still the truth; leave the listener playing.
                continue;
            }
            if (!payload.equals(last)) {
                SENT.put(id, payload);
                sender.accept(player, payload);
            }
        }
        SENT.keySet().retainAll(online);
    }

    /** Forces the next tick to re-send, e.g. after a player rejoins. */
    public static void forget(UUID player) {
        SENT.remove(player);
    }

    public static void clear() {
        SENT.clear();
    }

    /** Null means "unknowable right now" - distinct from {@link PairedBoxPayload#NONE}. */
    private static PairedBoxPayload resolve(MinecraftServer server, ServerPlayer player,
                                            PairedBoxPayload last) {
        ItemStack worn = HeadphoneAccess.findWorn(player);
        if (worn.isEmpty()) {
            return PairedBoxPayload.NONE;
        }
        ResourceLocation dimension = HeadphonesItem.boundDimension(worn);
        BlockPos pos = HeadphonesItem.boundPos(worn);
        if (dimension == null || pos == null) {
            return PairedBoxPayload.NONE;
        }

        ServerLevel level = server.getLevel(ResourceKey.create(Registry.DIMENSION_REGISTRY, dimension));
        if (level == null) {
            return PairedBoxPayload.silent(dimension.toString(), pos);
        }
        if (!level.hasChunkAt(pos)) {
            // Only coast on the previous answer if it was about this same box. Otherwise the
            // client has never been told about this pairing and would fall back to the nearest
            // box, which is not what the player asked for.
            return describes(last, dimension, pos) ? null : PairedBoxPayload.silent(dimension.toString(), pos);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MusicBoxBlockEntity box) || !box.isPlaying()) {
            return PairedBoxPayload.silent(dimension.toString(), pos);
        }
        return new PairedBoxPayload(true, dimension.toString(), pos,
                box.getStationLabel(), box.getStationUrl(), true, box.getVolume());
    }

    private static boolean describes(PairedBoxPayload payload, ResourceLocation dimension, BlockPos pos) {
        return payload != null && payload.paired()
                && payload.pos().equals(pos)
                && payload.dimension().equals(dimension.toString());
    }
}
