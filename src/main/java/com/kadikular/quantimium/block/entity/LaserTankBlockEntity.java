package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserTankBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.util.FluidTanks;
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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * A Laser Tank: the Laser Port's twin for fluids. {@link #TANKS} tanks of {@link #CAPACITY} mB, one fluid in each.
 * Light passes through it and out of its face; it catches what an Extract lens sends back up the light, sends what it
 * holds down it to an Insert lens or another tank, or both. Pipes fill it while it sends, and drain it any time.
 */
@SuppressWarnings("deprecation")
public class LaserTankBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView, MenuProvider {

    public static final int TANKS = 4;
    public static final int CAPACITY = 16_000;

    private final FluidTanks tanks = new FluidTanks(TANKS, CAPACITY, this::changed);
    private final IFluidHandler pipes = tanks.view(this::sends, () -> true);
    private LaserPortBlockEntity.Mode mode = LaserPortBlockEntity.Mode.COLLECT;
    private List<LaserBeam> shownBeams = List.of();

    public LaserTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_TANK_BE.get(), pos, state);
    }

    private void changed() {
        setChanged();
        sync();
    }

    public FluidTanks tanks() {
        return tanks;
    }

    public LaserPortBlockEntity.Mode mode() {
        return mode;
    }

    /** Steps to the next of collect, send, both. */
    public void cycleMode() {
        mode = mode.next();
        setChanged();
        showMode();
        sync();
        invalidateCapabilities();
        if (level != null) LaserNetworks.changed(level);
    }

    /** What a pipe on any face sees. */
    public ResourceHandler<FluidResource> automation(@Nullable Direction side) {
        return tanks.handler(pipes);
    }

    @Override
    public boolean collects() {
        return mode != LaserPortBlockEntity.Mode.SEND;
    }

    @Override
    public boolean sends() {
        return mode != LaserPortBlockEntity.Mode.COLLECT;
    }

    @Override
    public int collectFluid(FluidStack fluid, boolean simulate) {
        return collects() ? tanks.fill(fluid, simulate) : 0;
    }

    @Override
    public FluidStack sendFluid(java.util.function.Predicate<FluidStack> wants, int max, boolean simulate) {
        if (!sends()) return FluidStack.EMPTY;
        for (int tank = 0; tank < TANKS; tank++) {
            FluidStack there = tanks.get(tank);
            if (!there.isEmpty() && wants.test(there)) return tanks.drain(tank, max, simulate);
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int takeBackFluid(FluidStack fluid) {
        return tanks.fill(fluid, false);
    }

    public Direction facing() {
        return getBlockState().getValue(LaserTankBlock.FACING);
    }

    // ---- the network ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of(facing());
    }

    /** Light gets in only by its back, straight: travelling the way it faces. */
    @Override
    public boolean accepts(Direction travel) {
        return travel == facing();
    }

    @Override
    public boolean accepts(com.kadikular.quantimium.laser.Ray ray) {
        return ray.straight() && accepts(ray.face());
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    @Override
    public boolean relays() {
        return true;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (!same) sync();
    }

    @Override
    public List<LaserBeam> syncedBeams() {
        return shownBeams;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            showMode();
            LaserNetworks.add(level, this);
        }
    }

    /** The block shows its mode. */
    private void showMode() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (state.hasProperty(LaserTankBlock.MODE) && state.getValue(LaserTankBlock.MODE) != mode) {
            level.setBlock(worldPosition, state.setValue(LaserTankBlock.MODE, mode), Block.UPDATE_CLIENTS);
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

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Tanks", FluidStack.OPTIONAL_CODEC.listOf(), tanks.contents());
        out.putInt("RelayMode", mode.ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tanks.load(in.read("Tanks", FluidStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        LaserPortBlockEntity.Mode[] modes = LaserPortBlockEntity.Mode.values();
        mode = modes[Math.floorMod(in.getIntOr("RelayMode", 0), modes.length)];
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    /** What the screen shows (the mode, the tanks) to the clients watching, at once. */
    private void sync() {
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.laser_tank");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.kadikular.quantimium.menu.LaserTankMenu(containerId, inventory, this);
    }

    public boolean isUsableBy(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    /** What it's set to, and how many tanks hold something. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        int used = 0;
        for (int tank = 0; tank < TANKS; tank++) if (!tanks.get(tank).isEmpty()) used++;
        return Component.translatable("message.quantimium.laser_tank.holds",
                Component.translatable("message.quantimium.laser_port.mode." + mode.name().toLowerCase(java.util.Locale.ROOT)),
                used, TANKS).withStyle(ChatFormatting.DARK_AQUA);
    }
}
