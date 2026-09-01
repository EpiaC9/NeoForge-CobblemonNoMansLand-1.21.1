package net.epiac9.cobblemonnml.battle.action.network;

import io.netty.buffer.ByteBuf;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ActionBattleFlinchVisualPayload(int entityId, String pokemonUuid, String visualType) implements CustomPacketPayload {
    public static final Type<ActionBattleFlinchVisualPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CobblemonNML.MOD_ID, "action_battle_flinch_visual"));
    public static final StreamCodec<ByteBuf, ActionBattleFlinchVisualPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ActionBattleFlinchVisualPayload decode(ByteBuf buf) {
            return new ActionBattleFlinchVisualPayload(buf.readInt(), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, ActionBattleFlinchVisualPayload value) {
            buf.writeInt(value.entityId());
            ByteBufCodecs.STRING_UTF8.encode(buf, value.pokemonUuid() != null ? value.pokemonUuid() : "");
            ByteBufCodecs.STRING_UTF8.encode(buf, value.visualType() != null ? value.visualType() : "NORMAL");
        }
    };

    public ActionBattleFlinchVisualPayload {
        pokemonUuid = pokemonUuid != null ? pokemonUuid : "";
        visualType = visualType != null ? visualType : "NORMAL";
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
