package net.epiac9.cobblemonnml.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class DungeonPortalItemEjector {
    // EJECTION SETTINGS
    private static final double EJECT_DISTANCE = 2.15D;
    private static final double EJECT_HEIGHT = 1.20D;
    private static final double HORIZONTAL_SPEED = 0.24D;
    private static final double UPWARD_SPEED = 0.34D;
    // EJECT ONE ITEM
    public static void eject( ItemEntity itemEntity, BlockPos portalCenter ) {
        if (itemEntity == null || portalCenter == null || itemEntity.isRemoved()) {
            return;
        }
        // PORTAL CENTER
        double centerX = portalCenter.getX() + 0.5D;
        double centerZ = portalCenter.getZ() + 0.5D;
        // ITEM OFFSET FROM CENTER
        double offsetX = itemEntity.getX() - centerX;
        double offsetZ = itemEntity.getZ() - centerZ;
        // CHOOSE NEAREST OUTWARD DIRECTION
        double directionX = 0.0D;
        double directionZ = 0.0D;
        if (Math.abs(offsetX) > Math.abs(offsetZ)) {
            directionX = offsetX >= 0.0D ? 1.0D : -1.0D;
        } else if (Math.abs(offsetZ) > 0.01D) {
            directionZ = offsetZ >= 0.0D ? 1.0D : -1.0D;
        } else {

            /*
             * Item is almost exactly in the center.
             * Pick a random cardinal direction so several rejected items don't all stack perfectly.
             */
            switch (itemEntity.getRandom().nextInt(4)) {
                case 0 ->
                        directionX = 1.0D;
                case 1 ->
                        directionX = -1.0D;
                case 2 ->
                        directionZ = 1.0D;
                default ->
                        directionZ = -1.0D;
            }
        }
        // MOVE OUTSIDE THE 3x3
        double targetX = centerX + directionX * EJECT_DISTANCE;
        double targetY = portalCenter.getY() + EJECT_HEIGHT;
        double targetZ = centerZ + directionZ * EJECT_DISTANCE;
        itemEntity.setPos( targetX, targetY, targetZ );
        // BOUNCE VELOCITY
        itemEntity.setDeltaMovement( directionX * HORIZONTAL_SPEED, UPWARD_SPEED, directionZ * HORIZONTAL_SPEED );
        itemEntity.hasImpulse = true;
    }
    // EJECT EVERY ITEM ON PORTAL
    public static void ejectAll( ServerLevel level, BlockPos portalCenter ) {
        if (level == null || portalCenter == null) {
            return;
        }
        // COMPLETE 3x3 PORTAL AREA
        AABB portalArea =
                new AABB(
                        portalCenter.getX()
                                - 1.0D,
                        portalCenter.getY()
                                - 0.25D,
                        portalCenter.getZ()
                                - 1.0D,

                        portalCenter.getX()
                                + 2.0D,
                        portalCenter.getY()
                                + 2.5D,
                        portalCenter.getZ()
                                + 2.0D
                );
        List<ItemEntity> items = level.getEntitiesOfClass( ItemEntity.class, portalArea );
        for (ItemEntity item : items) {
            eject( item, portalCenter );
        }
    }
}
