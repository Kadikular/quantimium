package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserMirrorBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.MirrorPlate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A Laser Mirror's plate: which way it faces (see {@link MirrorPlate}). Light isn't held here: a beam that reaches the
 * mirror bounces off the plate and goes on as the same beam, with its own light, so beams crossing one mirror never mix
 * (see {@link com.kadikular.quantimium.laser.LaserBeam#follow}). A Laser Splitter's plate is half-silvered: both sides
 * reflect, and light goes straight through as well. Off while a redstone signal reaches it: then it stops light.
 */
public class LaserMirrorBlockEntity extends BlockEntity implements com.kadikular.quantimium.flux.FluxMeterReadout {

    private MirrorPlate plate;
    /** Whether the plate's been read or set: a mirror from before plates takes its from its old sides. */
    private boolean plateKnown;

    public LaserMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_MIRROR_BE.get(), pos, state);
        plate = MirrorPlate.fromSides(state.getValue(LaserMirrorBlock.INPUT), state.getValue(LaserMirrorBlock.OUTPUT));
    }

    public MirrorPlate plate() {
        return plate;
    }

    /** Turns the plate to {@code plate}: the light through it is worked out again. */
    public void setPlate(MirrorPlate plate) {
        if (plate.equals(this.plate) && plateKnown) return;
        this.plate = plate;
        plateKnown = true;
        setChanged();
        if (level != null) {
            LaserNetworks.changed(level);
            if (!level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private boolean splits() {
        return getBlockState().getBlock() instanceof LaserMirrorBlock mirror && mirror.splits();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Plate", plate.index());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        int saved = in.getIntOr("Plate", -1);
        if (saved >= 0) {
            plate = MirrorPlate.byIndex(saved);
            plateKnown = true;
        } else {
            // Set the old way: its plate faces half way between the side light came in by and the one it left by.
            BlockState state = getBlockState();
            plate = MirrorPlate.fromSides(state.getValue(LaserMirrorBlock.INPUT), state.getValue(LaserMirrorBlock.OUTPUT));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Which way it faces, and whether it's off. */
    @Override
    public MutableComponent fluxMeterLine() {
        MutableComponent line = describe(plate);
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
