package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.MirrorPlate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Laser Mirror's plate: which way it faces (see {@link MirrorPlate}). Light isn't held here: a beam that reaches the
 * mirror bounces off the plate and goes on as the same beam, with its own light, so beams crossing one mirror never mix
 * (see {@link com.kadikular.quantimium.laser.LaserBeam#follow}). A Laser Splitter's plate is half-silvered: both sides
 * reflect, and light goes straight through as well. Off while a redstone signal reaches it: then it stops light.
 */
public class LaserMirrorBlockEntity extends BlockEntity implements com.kadikular.quantimium.flux.FluxMeterReadout {

    public LaserMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_MIRROR_BE.get(), pos, state);
    }

    private boolean splits() {
        return getBlockState().getBlock() instanceof LaserMirrorBlock mirror && mirror.splits();
    }

    /** Which way it faces, and whether it's off. */
    @Override
    public MutableComponent fluxMeterLine() {
        MutableComponent line = describe(LaserMirrorBlock.plate(getBlockState()));
        if (splits()) line = Component.translatable("message.quantimium.laser_splitter.plate", line);
        if (getBlockState().getValue(LaserMirrorBlock.POWERED)) line.append(Component.translatable("message.quantimium.laser_mirror.off"));
        return line.withStyle(getBlockState().getValue(LaserMirrorBlock.POWERED) ? ChatFormatting.GRAY : ChatFormatting.AQUA);
    }

    /** "Facing north", "Facing north, 22.5° towards up", "Facing between north and up". */
    public static MutableComponent describe(MirrorPlate plate) {
        Component face = Component.translatable("direction.quantimium." + plate.face().getSerializedName());
        if (plate.toward() == null) return Component.translatable("message.quantimium.laser_mirror.facing", face);
        Component toward = Component.translatable("direction.quantimium." + plate.toward().getSerializedName());
        return plate.steps() == 1 ? Component.translatable("message.quantimium.laser_mirror.tilted", face, toward)
                : Component.translatable("message.quantimium.laser_mirror.between", face, toward);
    }
}
