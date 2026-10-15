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
 * A Logistics Controller: a relay that marks the light it passes on, and gives out light of its own, as a Solar
 * Emitter does. Everything that light reaches, through mirrors, splitters and relays, joined with other light or
 * not, but not past an Isolator, is one logistics network. It fills its own store from light ending on it, or from
 * cables, and pays from it only for what its receptacles move: it gives no power, so lens work past it is paid
 * for by the light's other sources, as ever. Up the beam from a lens, it is the network: Pickup and Extract put into it, Insert, Placement and
 * Interact take from it. See {@code Logistics}.
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
    /** What the network found last time it ran: its members, and whether another controller's light joins this one's. */
    private List<com.kadikular.quantimium.laser.LogisticsMember> members = List.of();
    private boolean clash;

    public LogisticsControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_CONTROLLER_BE.get(), pos, state);
        if (state.hasProperty(LogisticsControllerBlock.FACING)) outputs.add(state.getValue(LogisticsControllerBlock.FACING));
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energy;
    }

    // ---- outputs ----

    /** The face light comes in by to go on through: behind the way it was placed facing. Never an output. */
    public Direction back() {
        BlockState state = getBlockState();
        return state.hasProperty(LogisticsControllerBlock.FACING) ? state.getValue(LogisticsControllerBlock.FACING).getOpposite() : Direction.SOUTH;
    }

    /** Turns light out of {@code face} on or off; never its back, and the last output stays. Whether it changed. */
    public boolean toggleOutput(Direction face) {
        if (face == back()) return false;
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

    /** The block shows which faces light leaves by; its facing, and so its back, stays as placed. */
    private void showOutputs() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(LogisticsControllerBlock.OUTPUTS.get(Direction.NORTH))) return;
        BlockState shown = state;
        for (Direction face : Direction.values()) shown = shown.setValue(LogisticsControllerBlock.OUTPUTS.get(face), outputs.contains(face));
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
    public void report(List<com.kadikular.quantimium.laser.LogisticsMember> members, boolean clash) {
        this.members = List.copyOf(members);
        boolean changed = this.clash != clash;
        this.clash = clash;
        if (changed) showLit();
    }

    /** The members still standing, or none while another controller's light stops the network. */
    @Override
    public List<com.kadikular.quantimium.laser.LogisticsMember> members() {
        if (clash) return List.of();
        return members.stream().filter(member -> !(member instanceof BlockEntity entity) || !entity.isRemoved()).toList();
    }

    // ---- up the beam from a lens, it is the network ----

    @Override
    public boolean collects() {
        return true;
    }

    @Override
    public net.minecraft.world.item.ItemStack collect(net.minecraft.world.item.ItemStack stack, boolean simulate) {
        return com.kadikular.quantimium.laser.Logistics.insert(members(), stack, simulate);
    }

    @Override
    public boolean sends() {
        return true;
    }

    @Override
    public net.minecraft.world.item.ItemStack send(java.util.function.Predicate<net.minecraft.world.item.ItemStack> wants, int max, boolean simulate) {
        return com.kadikular.quantimium.laser.Logistics.take(members(), wants, max, simulate);
    }

    @Override
    public net.minecraft.world.item.ItemStack takeBack(net.minecraft.world.item.ItemStack stack) {
        return com.kadikular.quantimium.laser.Logistics.insert(members(), stack, false);
    }

    /** How its network stands, for a click or the Flux Meter. */
    public MutableComponent status() {
        if (clash) return Component.translatable("message.quantimium.logistics_controller.clash").withStyle(ChatFormatting.RED);
        if (!powered()) return Component.translatable("message.quantimium.logistics_controller.unpowered", members.size()).withStyle(ChatFormatting.GOLD);
        return Component.translatable("message.quantimium.logistics_controller.status", members.size(), String.format(java.util.Locale.ROOT, "%,d", energy.getEnergyStored()))
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

    /** Light in by its back goes on out of its outputs, marked as the network's. */
    @Override
    public boolean relays() {
        return true;
    }

    /** Only light in by its back goes on through; light on any other side only charges it. */
    @Override
    public boolean accepts(Direction travel) {
        return travel.getOpposite() == back();
    }

    /** And it gives out light of its own, which joins what it passes on. */
    @Override
    public boolean generates() {
        return true;
    }

    @Override
    public Light emits() {
        return Light.from(worldPosition, range()).controlledBy(worldPosition);
    }

    @Override
    public boolean chargedByLight() {
        return true;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (same) return;
        showLit();
        sync();
    }

    /** Lit while its light is: dark while another controller's light stops its network. */
    private void showLit() {
        boolean lit = !clash && shownBeams.stream().anyMatch(LaserBeam::live);
        BlockState state = getBlockState();
        if (level != null && !level.isClientSide() && state.hasProperty(LogisticsControllerBlock.LIT) && state.getValue(LogisticsControllerBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(LogisticsControllerBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
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
        // From before it had a back: light out of there goes.
        outputs.remove(back());
        if (outputs.isEmpty()) outputs.add(back().getOpposite());
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
