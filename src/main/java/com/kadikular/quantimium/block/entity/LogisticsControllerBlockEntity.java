package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LogisticsControllerBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.Light;
import com.kadikular.quantimium.laser.LogisticsController;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A Logistics Controller: a source whose light carries no power to what it reaches, only the network. Everything
 * its light reaches, through mirrors, splitters and relays but not past an Isolator, is one logistics network,
 * and it pays, from the FE it's given, for every item the network moves. See {@code Logistics}.
 */
public class LogisticsControllerBlockEntity extends BlockEntity implements LaserNode, LogisticsController, FluxMeterReadout, LaserBeamView {

    private final QuantumEnergyStorage energy = new QuantumEnergyStorage(LaserTuning.LOGISTICS_CAPACITY, LaserTuning.LOGISTICS_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };
    private final Set<Direction> outputs = EnumSet.noneOf(Direction.class);
    private List<LaserBeam> shownBeams = List.of();
    /** What the network found last time it ran: how many members, and whether another controller's light joins this one's. */
    private int members;
    private boolean clash;

    public LogisticsControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_CONTROLLER_BE.get(), pos, state);
        if (state.hasProperty(LogisticsControllerBlock.FACING)) outputs.add(state.getValue(LogisticsControllerBlock.FACING));
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energy;
    }

    // ---- outputs ----

    /** Turns light out of {@code face} on or off; the last output stays. Whether it changed. */
    public boolean toggleOutput(Direction face) {
        if (outputs.contains(face)) {
            if (outputs.size() == 1) return false;
            outputs.remove(face);
        } else {
            outputs.add(face);
        }
        showOutputs();
        setChanged();
        sync();
        if (level != null) LaserNetworks.changed(level);
        return true;
    }

    /** The block shows which faces light leaves by, and faces one of them. */
    private void showOutputs() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH))) return;
        BlockState shown = state;
        for (Direction face : Direction.values()) shown = shown.setValue(LogisticsControllerBlock.OUTPUTS.get(face), outputs.contains(face));
        if (!outputs.contains(shown.getValue(LogisticsControllerBlock.FACING))) shown = shown.setValue(LogisticsControllerBlock.FACING, outputs.iterator().next());
        if (shown != state) level.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
    }

    // ---- logistics ----

    @Override
    public boolean powered() {
        return energy.getEnergyStored() >= LaserTuning.LOGISTICS_FE_PER_MOVE;
    }

    @Override
    public boolean pay(long fe) {
        if (energy.getEnergyStored() < fe) return false;
        energy.consume(fe);
        setChanged();
        return true;
    }

    @Override
    public void report(int members, boolean clash) {
        this.members = members;
        this.clash = clash;
    }

    /** How its network stands, for a click or the Flux Meter. */
    public MutableComponent status() {
        if (clash) return Component.translatable("message.quantimium.logistics_controller.clash").withStyle(ChatFormatting.RED);
        if (!powered()) return Component.translatable("message.quantimium.logistics_controller.unpowered", members).withStyle(ChatFormatting.GOLD);
        return Component.translatable("message.quantimium.logistics_controller.status", members, String.format(java.util.Locale.ROOT, "%,d", energy.getEnergyStored()))
                .withStyle(ChatFormatting.DARK_AQUA);
    }

    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        return status();
    }

    // ---- the network ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return outputs;
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    @Override
    public boolean relays() {
        return false;
    }

    /** Its light carries the network, and no power. */
    @Override
    public Light emits() {
        return Light.from(worldPosition, range()).controlledBy(worldPosition);
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (same) return;
        boolean lit = beams.stream().anyMatch(LaserBeam::live);
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(LogisticsControllerBlock.LIT) && state.getValue(LogisticsControllerBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(LogisticsControllerBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        sync();
    }

    @Override
    public List<LaserBeam> syncedBeams() {
        return shownBeams;
    }

    // ---- life ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            showOutputs();
            LaserNetworks.add(level, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) LaserNetworks.remove(level, this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) LaserNetworks.remove(level, this);
    }

    private void sync() {
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Energy", energy.getEnergyStored());
        int mask = 0;
        for (Direction face : outputs) mask |= 1 << face.get3DDataValue();
        out.putInt("Outputs", mask);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.setEnergy(in.getIntOr("Energy", 0));
        int mask = in.getIntOr("Outputs", 0);
        if (mask != 0) {
            outputs.clear();
            for (Direction face : Direction.values()) if ((mask & 1 << face.get3DDataValue()) != 0) outputs.add(face);
        }
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
