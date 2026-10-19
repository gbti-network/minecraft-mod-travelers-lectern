package com.gbti.travelerslectern.dialog;

import com.gbti.travelerslectern.Lectern;
import com.gbti.travelerslectern.LecternStore;
import com.gbti.travelerslectern.Lecterns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Teleport there: the operator lands where a reader stands, on the side the lectern faces, looking
 * at it. If that spot is blocked, they land on top of the lectern instead. Works across dimensions.
 */
final class Teleports {
    // Looking down at the lectern's top from standing height
    private static final float PITCH = 30.0F;

    private Teleports() {
    }

    static boolean toLectern(ServerPlayer player, Lectern lectern) {
        ServerLevel level = Lecterns.level(LecternStore.server(), lectern);
        if (level == null) {
            return false;
        }
        BlockPos pos = lectern.pos();
        level.getChunk(pos);
        BlockState state = level.getBlockState(pos);
        Direction facing = state.hasProperty(LecternBlock.FACING) ? state.getValue(LecternBlock.FACING) : Direction.NORTH;
        BlockPos stand = pos.relative(facing);
        if (open(level, stand) && open(level, stand.above())) {
            return player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(),
                    facing.getOpposite().toYRot(), PITCH, false);
        }
        return player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, Set.of(),
                facing.getOpposite().toYRot(), PITCH, false);
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
