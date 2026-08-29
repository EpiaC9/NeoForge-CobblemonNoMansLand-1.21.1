package net.epiac9.cobblemonnml.battle.action.network;

import io.netty.buffer.ByteBuf;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ActionBattleHudPayload(
        boolean visible,
        String playerPokemonName,
        int playerPokemonLevel,
        int playerCurrentHp,
        int playerMaxHp,
        int playerPartySlot,
        String trainerPokemonName,
        int trainerPokemonLevel,
        int trainerCurrentHp,
        int trainerMaxHp,
        int trainerPartySlot,
        MoveState move1,
        MoveState move2,
        MoveState move3,
        MoveState move4
) implements CustomPacketPayload {
    public static final Type<ActionBattleHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CobblemonNML.MOD_ID, "action_battle_hud"));
    public static final StreamCodec<ByteBuf, ActionBattleHudPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ActionBattleHudPayload decode(ByteBuf buf) {
            return new ActionBattleHudPayload(
                    buf.readBoolean(), readString(buf), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    readString(buf), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    readMove(buf), readMove(buf), readMove(buf), readMove(buf)
            );
        }

        @Override
        public void encode(ByteBuf buf, ActionBattleHudPayload value) {
            buf.writeBoolean(value.visible());
            writeString(buf, value.playerPokemonName());
            buf.writeInt(value.playerPokemonLevel());
            buf.writeInt(value.playerCurrentHp());
            buf.writeInt(value.playerMaxHp());
            buf.writeInt(value.playerPartySlot());
            writeString(buf, value.trainerPokemonName());
            buf.writeInt(value.trainerPokemonLevel());
            buf.writeInt(value.trainerCurrentHp());
            buf.writeInt(value.trainerMaxHp());
            buf.writeInt(value.trainerPartySlot());
            writeMove(buf, value.move1());
            writeMove(buf, value.move2());
            writeMove(buf, value.move3());
            writeMove(buf, value.move4());
        }
    };

    public static ActionBattleHudPayload hidden() {
        MoveState empty = MoveState.empty();
        return new ActionBattleHudPayload(false, "", 0, 0, 1, -1, "", 0, 0, 1, -1, empty, empty, empty, empty);
    }

    public MoveState move(int slot) {
        return switch (slot) {
            case 0 -> move1;
            case 1 -> move2;
            case 2 -> move3;
            case 3 -> move4;
            default -> MoveState.empty();
        };
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void writeString(ByteBuf buf, String value) { ByteBufCodecs.STRING_UTF8.encode(buf, value != null ? value : ""); }
    private static String readString(ByteBuf buf) { return ByteBufCodecs.STRING_UTF8.decode(buf); }

    private static void writeMove(ByteBuf buf, MoveState move) {
        MoveState value = move != null ? move : MoveState.empty();
        writeString(buf, value.name());
        writeString(buf, value.type());
        buf.writeInt(value.currentPp());
        buf.writeInt(value.maxPp());
        buf.writeBoolean(value.supported());
        buf.writeLong(value.cooldownRemainingTicks());
        buf.writeLong(value.cooldownDurationTicks());
    }

    private static MoveState readMove(ByteBuf buf) {
        return new MoveState(readString(buf), readString(buf), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readLong(), buf.readLong());
    }

    public record MoveState(String name, String type, int currentPp, int maxPp, boolean supported, long cooldownRemainingTicks, long cooldownDurationTicks) {
        public static MoveState empty() { return new MoveState("", "normal", 0, 0, false, 0L, 0L); }
    }
}
