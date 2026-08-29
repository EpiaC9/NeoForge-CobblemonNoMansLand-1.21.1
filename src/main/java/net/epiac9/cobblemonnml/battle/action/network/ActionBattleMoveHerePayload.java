package net.epiac9.cobblemonnml.battle.action.network;

import io.netty.buffer.ByteBuf;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ActionBattleMoveHerePayload(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<ActionBattleMoveHerePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CobblemonNML.MOD_ID, "action_battle_move_here"));
    public static final StreamCodec<ByteBuf, ActionBattleMoveHerePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ActionBattleMoveHerePayload::x,
            ByteBufCodecs.DOUBLE, ActionBattleMoveHerePayload::y,
            ByteBufCodecs.DOUBLE, ActionBattleMoveHerePayload::z,
            ActionBattleMoveHerePayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
