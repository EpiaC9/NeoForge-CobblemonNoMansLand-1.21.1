package net.epiac9.cobblemonnml.dimension.network;

import io.netty.buffer.ByteBuf;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record DungeonCleanupToastPayload(String slotName, String status) implements CustomPacketPayload {
    public static final Type<DungeonCleanupToastPayload> TYPE =
            new Type<>( ResourceLocation.fromNamespaceAndPath( CobblemonNML.MOD_ID, "dungeon_cleanup_toast" ) );
    public static final StreamCodec<ByteBuf, DungeonCleanupToastPayload>
            STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    DungeonCleanupToastPayload::slotName,

                    ByteBufCodecs.STRING_UTF8,
                    DungeonCleanupToastPayload::status,

                    DungeonCleanupToastPayload::new
            );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
