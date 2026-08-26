package net.epiac9.cobblemonnml.dimension.network;

import io.netty.buffer.ByteBuf;

import net.epiac9.cobblemonnml.CobblemonNML;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record DungeonTimerPayload(
        boolean visible,
        int remainingSeconds,
        int durationSeconds,
        int themeIndex,
        int tierIndex
) implements CustomPacketPayload {
    // PAYLOAD TYPE
    public static final Type<DungeonTimerPayload> TYPE =
            new Type<>( ResourceLocation.fromNamespaceAndPath( CobblemonNML.MOD_ID, "dungeon_timer" ) );
    // CODEC
    public static final StreamCodec<ByteBuf, DungeonTimerPayload>
            STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    DungeonTimerPayload::visible,
                    ByteBufCodecs.VAR_INT,
                    DungeonTimerPayload::remainingSeconds,
                    ByteBufCodecs.VAR_INT,
                    DungeonTimerPayload::durationSeconds,
                    ByteBufCodecs.VAR_INT,
                    DungeonTimerPayload::themeIndex,
                    ByteBufCodecs.VAR_INT,
                    DungeonTimerPayload::tierIndex,
                    DungeonTimerPayload::new
            );
    // TYPE
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {

        return TYPE;
    }
}
