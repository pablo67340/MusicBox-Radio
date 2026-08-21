package com.musicbox.network;

import com.musicbox.blockentity.MusicBoxBlockEntity;
import com.musicbox.station.Station;
import com.musicbox.station.StationConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A player asking to add a station from the music box GUI.
 * <p>
 * This is the one place a client can put a URL in front of other players, so everything it
 * carries is treated as hostile until {@link #apply(ServerPlayer)} has checked it: the sender
 * needs permission, needs to actually be standing at the box, and the URL has to survive
 * {@link StationConfig#rejectReason}. Playback itself still resolves by index against
 * server-side data, so nothing here widens that path.
 */
public record AddStationPayload(BlockPos pos, String label, String url) {

    /** Beyond this the sender cannot plausibly have the GUI open. */
    private static final double MAX_REACH_SQR = 64.0D;

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(label, StationConfig.MAX_LABEL_LENGTH);
        buf.writeUtf(url, StationConfig.MAX_URL_LENGTH);
    }

    public static AddStationPayload read(FriendlyByteBuf buf) {
        return new AddStationPayload(
                buf.readBlockPos(),
                buf.readUtf(StationConfig.MAX_LABEL_LENGTH),
                buf.readUtf(StationConfig.MAX_URL_LENGTH));
    }

    /**
     * Validates and applies the request.
     *
     * @return true if the list changed, so the caller should refresh the sender's menu
     */
    public boolean apply(ServerPlayer player) {
        if (!StationConfig.mayAddStations(player)) {
            deny(player, "message.musicboxradio.custom.no_permission");
            return false;
        }

        Level level = player.getLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_REACH_SQR) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof MusicBoxBlockEntity box)) {
            return false;
        }

        String cleanLabel = label.trim();
        String cleanUrl = url.trim();
        String reason = StationConfig.rejectReason(cleanLabel, cleanUrl);
        if (reason != null) {
            deny(player, reason);
            return false;
        }

        Station station = Station.of(cleanLabel, cleanUrl);
        boolean added = StationConfig.scope() == StationConfig.Scope.GLOBAL
                ? StationConfig.addGlobal(station)
                : box.addCustomStation(station);
        if (!added) {
            deny(player, "message.musicboxradio.custom.rejected");
            return false;
        }

        player.displayClientMessage(
                Component.translatable("message.musicboxradio.custom.added", station.label()), true);
        return true;
    }

    private static void deny(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }
}
