package net.epiac9.cobblemonnml.registry;

import com.mojang.serialization.Codec;
import net.epiac9.cobblemonnml.CobblemonNML;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create( NeoForgeRegistries.ATTACHMENT_TYPES, CobblemonNML.MOD_ID );
    private static final Codec<BlockPos> BLOCK_POS_CODEC = Codec.LONG.xmap( BlockPos::of, BlockPos::asLong );
    public static final Supplier<AttachmentType<BlockPos>> RETURN_POSITION =
            ATTACHMENT_TYPES.register(
                    "return_position",
                    () -> AttachmentType.builder(() -> BlockPos.ZERO)
                            .serialize(BLOCK_POS_CODEC)
                            .build()
            );
    public static final Supplier<AttachmentType<BlockPos>> PORTAL_CENTER =
            ATTACHMENT_TYPES.register(
                    "portal_center",
                    () -> AttachmentType.builder(() -> BlockPos.ZERO)
                            .serialize(BLOCK_POS_CODEC)
                            .build()
            );
}
