package net.epiac9.cobblemonnml.battle.action.network;

import io.netty.buffer.ByteBuf;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ActionBattleSwapPayload() implements CustomPacketPayload {
    public static final Type<ActionBattleSwapPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CobblemonNML.MOD_ID, "action_battle_swap"));
    public static final StreamCodec<ByteBuf, ActionBattleSwapPayload> STREAM_CODEC = StreamCodec.unit(new ActionBattleSwapPayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
