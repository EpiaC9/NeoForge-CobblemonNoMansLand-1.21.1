package net.epiac9.cobblemonnml.battle.action.network;

import io.netty.buffer.ByteBuf;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ActionBattleGroundVisualPayload(Operation operation, String sessionId,
                                              String pokemonId, int depthPercent)
        implements CustomPacketPayload {
    public static final Type<ActionBattleGroundVisualPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CobblemonNML.MOD_ID, "action_battle_ground_visual"));
    public static final StreamCodec<ByteBuf, ActionBattleGroundVisualPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ActionBattleGroundVisualPayload decode(ByteBuf buf) {
            int ordinal = buf.readUnsignedByte();
            Operation operation = ordinal < Operation.values().length ? Operation.values()[ordinal] : null;
            return new ActionBattleGroundVisualPayload(operation, ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, ActionBattleGroundVisualPayload value) {
            buf.writeByte(value.operation() != null ? value.operation().ordinal() : 255);
            ByteBufCodecs.STRING_UTF8.encode(buf, value.sessionId());
            ByteBufCodecs.STRING_UTF8.encode(buf, value.pokemonId());
            buf.writeInt(value.depthPercent());
        }
    };

    public ActionBattleGroundVisualPayload {
        sessionId = sessionId != null ? sessionId : "";
        pokemonId = pokemonId != null ? pokemonId : "";
    }

    public static ActionBattleGroundVisualPayload update(UUID sessionId, UUID pokemonId, int depthPercent) {
        return new ActionBattleGroundVisualPayload(Operation.UPDATE, string(sessionId), string(pokemonId), depthPercent);
    }

    public static ActionBattleGroundVisualPayload clearSession(UUID sessionId) {
        return new ActionBattleGroundVisualPayload(Operation.CLEAR_SESSION, string(sessionId), "", 0);
    }

    public static ActionBattleGroundVisualPayload clearAll() {
        return new ActionBattleGroundVisualPayload(Operation.CLEAR_ALL, "", "", 0);
    }

    private static String string(UUID value) { return value != null ? value.toString() : ""; }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Operation { UPDATE, CLEAR_SESSION, CLEAR_ALL }
}
