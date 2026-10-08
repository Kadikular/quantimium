package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LaserEmitterBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.menu.LaserEmitterMenu;
import com.kadikular.quantimium.util.ItemStackHandler;
import com.kadikular.quantimium.util.RedstoneMode;
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
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
 * A laser emitter: power in, light out of each of its output faces, sending only what the blocks its
 * beams reach ask for, up to its tier's rate. The flash emitter can also burn furnace fuel to fill
 * itself, for power before anything else makes any. See {@link LaserNetworks}.
 */
public class LaserEmitterBlockEntity extends BlockEntity implements LaserNode, MenuProvider, FluxMeterReadout, LaserBeamView {

    public static final int DATA_COUNT = 9;

    private final LaserTuning.Tier tier;
    private final QuantumEnergyStorage energy;
    /** The flash emitter's fuel; unused by the others. */
    private final ItemStackHandler fuel = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return burnTime(stack) > 0;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final Set<Direction> outputs = EnumSet.noneOf(Direction.class);
    private RedstoneMode redstone = RedstoneMode.IGNORED;
    private int burnLeft;
    private int burnTotal;
    /** The beams as the network last showed them, on both sides; the client draws these. */
    private List<LaserBeam> shownBeams = List.of();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() & 0xFFFF;
                case 1 -> (energy.getEnergyStored() >>> 16) & 0xFFFF;
                case 2 -> energy.getMaxEnergyStored() & 0xFFFF;
                case 3 -> (energy.getMaxEnergyStored() >>> 16) & 0xFFFF;
                case 4 -> redstone.ordinal();
                case 5 -> burnLeft;
                case 6 -> burnTotal;
                case 7 -> (int) Math.min(Integer.MAX_VALUE, sending()) & 0xFFFF;
                case 8 -> ((int) Math.min(Integer.MAX_VALUE, sending()) >>> 16) & 0xFFFF;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public LaserEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_EMITTER_BE.get(), pos, state);
        this.tier = state.getBlock() instanceof LaserEmitterBlock block ? block.tier() : LaserTuning.Tier.FLASH;
        this.energy = new QuantumEnergyStorage(tier.capacity, tier.rate * 4, 0) {
            @Override
            protected void onReceived() {
                setChanged();
            }
        };
        if (state.hasProperty(LaserEmitterBlock.FACING)) outputs.add(state.getValue(LaserEmitterBlock.FACING));
    }

    public LaserTuning.Tier tier() {
        return tier;
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energy;
    }

    public ItemStackHandler getFuel() {
        return fuel;
    }

    public ContainerData getData() {
        return data;
    }

    public RedstoneMode redstone() {
        return redstone;
    }

    public void cycleRedstone() {
        redstone = redstone.next();
        setChanged();
    }

    /** What its beams sent a tick, on average over the last second. */
    public long sending() {
        long total = 0;
        for (LaserBeam beam : shownBeams) total += beam.flow();
        return total;
    }

    private int burnTime(ItemStack stack) {
        if (tier != LaserTuning.Tier.FLASH || stack.isEmpty() || level == null) return 0;
        return stack.getBurnTime(null, level.fuelValues());
    }

    // ---- outputs ----

    /**
     * Turns light out of {@code face} on or off. The last output stays: an emitter always shines somewhere.
     * Whether it changed.
     */
    public boolean toggleOutput(Direction face) {
        if (outputs.contains(face)) {
            if (outputs.size() == 1) return false;
            outputs.remove(face);
        } else {
            outputs.add(face);
        }
        BlockState state = getBlockState();
        if (!outputs.contains(state.getValue(LaserEmitterBlock.FACING)) && level != null) {
            level.setBlock(worldPosition, state.setValue(LaserEmitterBlock.FACING, outputs.iterator().next()), Block.UPDATE_ALL);
        }
        setChanged();
        sync();
        if (level != null) LaserNetworks.changed(level);
        return true;
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
        return tier.range;
    }

    @Override
    public boolean relays() {
        return false;
    }

    @Override
    public boolean enabled() {
        return level != null && redstone.allows(level, worldPosition);
    }

    @Override
    public long available() {
        return Math.min(tier.rate, energy.getEnergyStored());
    }

    @Override
    public void draw(long fe) {
        energy.consume(fe);
        setChanged();
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        if (LaserBeamView.sameLook(shownBeams, beams)) {
            shownBeams = beams;
            return;
        }
        shownBeams = beams;
        boolean lit = sending() > 0 || beams.stream().anyMatch(LaserBeam::live);
        BlockState state = getBlockState();
        if (level != null && state.getValue(LaserEmitterBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(LaserEmitterBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        sync();
    }

    @Override
    public List<LaserBeam> syncedBeams() {
        return shownBeams;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserEmitterBlockEntity emitter) {
        emitter.burn();
        // Ready to send costs a little, while it's on and has light to send somewhere.
        if (emitter.enabled() && emitter.tier.idle > 0 && emitter.energy.getEnergyStored() > 0) {
            emitter.energy.consume(emitter.tier.idle);
            emitter.setChanged();
        }
    }

    /** The flash emitter burns fuel while it has room for what that makes. */
    private void burn() {
        if (tier != LaserTuning.Tier.FLASH) return;
        if (burnLeft <= 0) {
            ItemStack stack = fuel.getStackInSlot(0);
            int time = burnTime(stack);
            if (time <= 0 || energy.getEnergyStored() + LaserTuning.FLASH_FE_PER_BURN_TICK > energy.getMaxEnergyStored()) return;
            ItemStack left = stack.getItem().getCraftingRemainder(stack) == null ? ItemStack.EMPTY
                    : stack.getItem().getCraftingRemainder(stack).create();
            fuel.extractItem(0, 1, false);
            if (!left.isEmpty() && fuel.getStackInSlot(0).isEmpty()) fuel.setStackInSlot(0, left);
            burnLeft = burnTotal = time;
        }
        burnLeft--;
        energy.setEnergy(energy.getEnergyStored() + LaserTuning.FLASH_FE_PER_BURN_TICK);
        setChanged();
    }

    // ---- life ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) LaserNetworks.add(level, this);
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
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), fuel.getStackInSlot(0));
        fuel.setStackInSlot(0, ItemStack.EMPTY);
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Energy", energy.getEnergyStored());
        fuel.serialize(out.child("Fuel"));
        int mask = 0;
        for (Direction face : outputs) mask |= 1 << face.get3DDataValue();
        out.putInt("Outputs", mask);
        out.putInt("Redstone", redstone.ordinal());
        out.putInt("BurnLeft", burnLeft);
        out.putInt("BurnTotal", burnTotal);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.setEnergy(in.getIntOr("Energy", 0));
        in.child("Fuel").ifPresent(fuel::deserialize);
        int mask = in.getIntOr("Outputs", 0);
        if (mask != 0) {
            outputs.clear();
            for (Direction face : Direction.values()) if ((mask & 1 << face.get3DDataValue()) != 0) outputs.add(face);
        }
        redstone = RedstoneMode.byOrdinal(in.getIntOr("Redstone", 0));
        burnLeft = in.getIntOr("BurnLeft", 0);
        burnTotal = in.getIntOr("BurnTotal", 0);
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    /** Clients get the beams to draw, and the outputs. */
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
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new LaserEmitterMenu(containerId, inventory, this);
    }

    /** What it's sending, or why it isn't. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        if (!enabled()) return Component.translatable("message.quantimium.laser.off").withStyle(ChatFormatting.GRAY);
        long sending = sending();
        if (sending <= 0) {
            return Component.translatable(energy.getEnergyStored() <= 0 ? "message.quantimium.laser.empty"
                    : "message.quantimium.laser.idle").withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("message.quantimium.laser.sending", String.format(java.util.Locale.ROOT, "%,d", sending))
                .withStyle(ChatFormatting.DARK_AQUA);
    }
}
