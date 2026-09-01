package net.epiac9.cobblemonnml.battle.action.network;

import io.netty.buffer.ByteBuf;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ActionBattleHudPayload(
        boolean visible,
        String playerPokemonName,
        String playerPokemonUuid,
        int playerPokemonLevel,
        int playerCurrentHp,
        int playerMaxHp,
        int playerPartySlot,
        String trainerPokemonName,
        String trainerPokemonUuid,
        int trainerPokemonLevel,
        int trainerCurrentHp,
        int trainerMaxHp,
        int trainerPartySlot,
        List<StatusState> playerStatuses,
        List<StatusState> trainerStatuses,
        StatStageState playerStatStages,
        StatStageState trainerStatStages,
        List<DamageState> playerDamageEvents,
        List<DamageState> trainerDamageEvents,
        long playerSwapCooldownRemainingTicks,
        long playerSwapCooldownDurationTicks,
        long playerMoveHereCooldownRemainingTicks,
        long playerMoveHereCooldownDurationTicks,
        MoveState move1,
        MoveState move2,
        MoveState move3,
        MoveState move4
) implements CustomPacketPayload {
    private static final int MAX_STATUS_ENTRIES = 16;
    private static final int MAX_DAMAGE_ENTRIES = 16;
    public static final Type<ActionBattleHudPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CobblemonNML.MOD_ID, "action_battle_hud"));
    public static final StreamCodec<ByteBuf, ActionBattleHudPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ActionBattleHudPayload decode(ByteBuf buf) {
            return new ActionBattleHudPayload(
                    buf.readBoolean(), readString(buf), readString(buf), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    readString(buf), readString(buf), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    readStatuses(buf), readStatuses(buf), readStatStages(buf), readStatStages(buf), readDamageEvents(buf), readDamageEvents(buf),
                    buf.readLong(), buf.readLong(), buf.readLong(), buf.readLong(),
                    readMove(buf), readMove(buf), readMove(buf), readMove(buf)
            );
        }

        @Override
        public void encode(ByteBuf buf, ActionBattleHudPayload value) {
            buf.writeBoolean(value.visible());
            writeString(buf, value.playerPokemonName());
            writeString(buf, value.playerPokemonUuid());
            buf.writeInt(value.playerPokemonLevel());
            buf.writeInt(value.playerCurrentHp());
            buf.writeInt(value.playerMaxHp());
            buf.writeInt(value.playerPartySlot());
            writeString(buf, value.trainerPokemonName());
            writeString(buf, value.trainerPokemonUuid());
            buf.writeInt(value.trainerPokemonLevel());
            buf.writeInt(value.trainerCurrentHp());
            buf.writeInt(value.trainerMaxHp());
            buf.writeInt(value.trainerPartySlot());
            writeStatuses(buf, value.playerStatuses());
            writeStatuses(buf, value.trainerStatuses());
            writeStatStages(buf, value.playerStatStages());
            writeStatStages(buf, value.trainerStatStages());
            writeDamageEvents(buf, value.playerDamageEvents());
            writeDamageEvents(buf, value.trainerDamageEvents());
            buf.writeLong(value.playerSwapCooldownRemainingTicks());
            buf.writeLong(value.playerSwapCooldownDurationTicks());
            buf.writeLong(value.playerMoveHereCooldownRemainingTicks());
            buf.writeLong(value.playerMoveHereCooldownDurationTicks());
            writeMove(buf, value.move1());
            writeMove(buf, value.move2());
            writeMove(buf, value.move3());
            writeMove(buf, value.move4());
        }
    };

    public ActionBattleHudPayload {
        playerPokemonUuid = playerPokemonUuid != null ? playerPokemonUuid : "";
        trainerPokemonUuid = trainerPokemonUuid != null ? trainerPokemonUuid : "";
        playerStatuses = playerStatuses != null ? List.copyOf(playerStatuses) : List.of();
        trainerStatuses = trainerStatuses != null ? List.copyOf(trainerStatuses) : List.of();
        playerStatStages = playerStatStages != null ? playerStatStages : StatStageState.neutral();
        trainerStatStages = trainerStatStages != null ? trainerStatStages : StatStageState.neutral();
        playerDamageEvents = playerDamageEvents != null ? List.copyOf(playerDamageEvents) : List.of();
        trainerDamageEvents = trainerDamageEvents != null ? List.copyOf(trainerDamageEvents) : List.of();
    }

    public static ActionBattleHudPayload hidden() {
        MoveState empty = MoveState.empty();
        return new ActionBattleHudPayload(false, "", "", 0, 0, 1, -1, "", "", 0, 0, 1, -1, List.of(), List.of(), StatStageState.neutral(), StatStageState.neutral(), List.of(), List.of(),
                0L, 0L, 0L, 0L, empty, empty, empty, empty);
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

    private static void writeStatuses(ByteBuf buf, List<StatusState> statuses) {
        List<StatusState> safe = statuses != null ? statuses : List.of();
        int size = Math.min(MAX_STATUS_ENTRIES, safe.size());
        buf.writeByte(size);
        for (int i = 0; i < size; i++) {
            StatusState status = safe.get(i);
            writeString(buf, status != null ? status.statusId() : "");
            buf.writeLong(status != null ? Math.max(0L, status.remainingTicks()) : 0L);
            buf.writeLong(status != null ? Math.max(0L, status.totalTicks()) : 0L);
        }
    }

    private static List<StatusState> readStatuses(ByteBuf buf) {
        int encodedSize = buf.readUnsignedByte();
        List<StatusState> statuses = new ArrayList<>(Math.min(MAX_STATUS_ENTRIES, encodedSize));
        for (int i = 0; i < encodedSize; i++) {
            StatusState status = new StatusState(readString(buf), buf.readLong(), buf.readLong());
            if (i < MAX_STATUS_ENTRIES) statuses.add(status);
        }
        return List.copyOf(statuses);
    }


    private static void writeStatStages(ByteBuf buf, StatStageState stages) {
        StatStageState value = stages != null ? stages : StatStageState.neutral();
        buf.writeByte(value.attack());
        buf.writeByte(value.defense());
        buf.writeByte(value.specialAttack());
        buf.writeByte(value.specialDefense());
        buf.writeByte(value.speed());
        buf.writeByte(value.accuracy());
    }

    private static StatStageState readStatStages(ByteBuf buf) {
        return new StatStageState(buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readByte());
    }

    private static void writeDamageEvents(ByteBuf buf, List<DamageState> events) {
        List<DamageState> safe = events != null ? events : List.of();
        int size = Math.min(MAX_DAMAGE_ENTRIES, safe.size());
        buf.writeByte(size);
        for (int i = 0; i < size; i++) {
            DamageState event = safe.get(i);
            buf.writeLong(event != null ? event.eventId() : 0L);
            buf.writeInt(event != null ? Math.max(0, event.damage()) : 0);
            writeString(buf, event != null ? event.category() : "NORMAL");
        }
    }

    private static List<DamageState> readDamageEvents(ByteBuf buf) {
        int encodedSize = buf.readUnsignedByte();
        List<DamageState> events = new ArrayList<>(Math.min(MAX_DAMAGE_ENTRIES, encodedSize));
        for (int i = 0; i < encodedSize; i++) {
            DamageState event = new DamageState(buf.readLong(), buf.readInt(), readString(buf));
            if (i < MAX_DAMAGE_ENTRIES) events.add(event);
        }
        return List.copyOf(events);
    }

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


    public record StatStageState(int attack, int defense, int specialAttack, int specialDefense, int speed, int accuracy) {
        public StatStageState {
            attack = clampStage(attack);
            defense = clampStage(defense);
            specialAttack = clampStage(specialAttack);
            specialDefense = clampStage(specialDefense);
            speed = clampStage(speed);
            accuracy = clampStage(accuracy);
        }

        public static StatStageState neutral() { return new StatStageState(0, 0, 0, 0, 0, 0); }
        public int stage(int index) {
            return switch (index) {
                case 0 -> attack;
                case 1 -> defense;
                case 2 -> specialAttack;
                case 3 -> specialDefense;
                case 4 -> speed;
                case 5 -> accuracy;
                default -> 0;
            };
        }
        private static int clampStage(int stage) { return Math.max(-6, Math.min(6, stage)); }
    }

    public record DamageState(long eventId, int damage, String category) {
        public DamageState {
            damage = Math.max(0, damage);
            category = category != null ? category : "NORMAL";
        }
    }

    public record StatusState(String statusId, long remainingTicks, long totalTicks) {
        public StatusState {
            statusId = statusId != null ? statusId : "";
            remainingTicks = Math.max(0L, remainingTicks);
            totalTicks = Math.max(0L, totalTicks);
        }
    }

    public record MoveState(String name, String type, int currentPp, int maxPp, boolean supported, long cooldownRemainingTicks, long cooldownDurationTicks) {
        public static MoveState empty() { return new MoveState("", "normal", 0, 0, false, 0L, 0L); }
    }
}
