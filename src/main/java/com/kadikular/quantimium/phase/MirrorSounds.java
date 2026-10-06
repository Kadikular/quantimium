package com.kadikular.quantimium.phase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sounds and effects of things only the mirror sees, such as Anomalite crystals growing and breaking:
 * heard by phased players nearby, never by the real world.
 */
public final class MirrorSounds {

    /** How far a phased player hears them, in blocks. */
    private static final double RANGE = 32.0;

    private MirrorSounds() {}

    public static void play(ServerLevel level, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : phasedNear(level, pos)) {
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, volume, pitch, seed));
        }
    }

    /** Removes the block at {@code pos} without the real world hearing or seeing it break; the mirror does. */
    public static void breakQuietly(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        int id = Block.getId(state);
        for (ServerPlayer player : phasedNear(level, pos)) {
            player.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, id, false));
        }
    }

    private static java.util.List<ServerPlayer> phasedNear(ServerLevel level, BlockPos pos) {
        return level.players().stream()
                .filter(MirrorPhase::isPhased)
                .filter(player -> player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= RANGE * RANGE)
                .toList();
    }
}
