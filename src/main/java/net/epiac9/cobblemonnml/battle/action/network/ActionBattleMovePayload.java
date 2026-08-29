package net.epiac9.cobblemonnml.battle.action.network;

import io.netty.buffer.ByteBuf;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ActionBattleMovePayload(int moveSlot) implements CustomPacketPayload {
    public static final Type<ActionBattleMovePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CobblemonNML.MOD_ID, "action_battle_move"));
    public static final StreamCodec<ByteBuf, ActionBattleMovePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ActionBattleMovePayload::moveSlot,
            ActionBattleMovePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
