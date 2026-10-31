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
 * holds down it to an Insert lens or another tank, or both. Pipes fill it while it sends and drain it, face by face as
 * set, and set faces push to or pull from what's beside them; a
 * player's bucket does either in any mode. Its filter lists fluids it catches (or lets go on up the light).
 */
@SuppressWarnings("deprecation")
public class LaserTankBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView, MenuProvider, SideConfigurable {

    public static final int TANKS = 4;
    public static final int CAPACITY = 16_000;

    public static final int FILTER_SLOTS = 9;

    private final FluidTanks tanks = new FluidTanks(TANKS, CAPACITY, this::changed);
    /** Fluids only: its faces have no items to set. */
    private static final com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile PROFILE =
            new com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile(0,
                    com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile.ALL_SIDES);
    private static final int AUTO_TRANSFER_INTERVAL = 20;
    /** The quickest it moves fluid in and out of what's beside it while busy, in ticks. */
    private static final int BUSIEST_INTERVAL = 2;
    /** What it moves in or out of one neighbour at once. */
    private static final int AUTO_TRANSFER_MB = 4_000;
    /** What a pipe on each face sees, as that face is set: it fills only while the tank sends. */
    private final IFluidHandler[] sided = new IFluidHandler[6];
    private List<com.kadikular.quantimium.block.entity.simulation.SideConfig> sideConfigs;
    private int transferIn = -1;
    private int transferInterval = AUTO_TRANSFER_INTERVAL;
    /** What a player's bucket sees: it fills and drains in any mode. */
    private final IFluidHandler hands = tanks.view(() -> true, () -> true);
    /** Fluid entries: what it catches, or with a blacklist, what it lets go on up the light. */
    private final net.minecraft.world.SimpleContainer filter = new net.minecraft.world.SimpleContainer(FILTER_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            LaserTankBlockEntity.this.setChanged();
        }
    };
    private boolean allow = true;
    private LaserPortBlockEntity.Mode mode = LaserPortBlockEntity.Mode.COLLECT;
    private List<LaserBeam> shownBeams = List.of();

    public LaserTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_TANK_BE.get(), pos, state);
        sideConfigs = PROFILE.sanitize(com.kadikular.quantimium.block.entity.simulation.SideConfig.defaults().stream()
                .map(config -> new com.kadikular.quantimium.block.entity.simulation.SideConfig(config.side(),
                        com.kadikular.quantimium.block.entity.simulation.SideMode.DISABLED, 0, 0, false, false,
                        com.kadikular.quantimium.block.entity.simulation.SideMode.BOTH,
                        com.kadikular.quantimium.block.entity.simulation.SideConfig.ALL_FLUID_INPUTS,
                        com.kadikular.quantimium.block.entity.simulation.SideConfig.ALL_FLUID_OUTPUTS, false, false))
                .toList());
        for (Direction side : Direction.values()) {
            sided[side.get3DDataValue()] = tanks.view(() -> sends() && config(side).fluidMode().allowsInput(),
                    () -> config(side).fluidMode().allowsOutput());
        }
    }

    private com.kadikular.quantimium.block.entity.simulation.SideConfig config(Direction side) {
        return sideConfigs.get(side.get3DDataValue());
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

    /** What a player's bucket fills from or empties into. */
    public ResourceHandler<FluidResource> forHands() {
        return tanks.handler(hands);
    }

    /**
     * Its tanks, whole: what a Logistics Receptacle on it works with, whatever its faces are set to. Inside whatever
     * transaction is open (a Logistics Materializer's), so one called off puts it back.
     */
    public IFluidHandler networkTanks() {
        if (network == null) network = com.kadikular.quantimium.util.LegacyFluids.legacy(tanks.handler(hands));
        return network;
    }

    @Nullable
    private IFluidHandler network;

    /** On a logistics network, what it's caught is offered: a passive provider, as a Laser Port is. */
    private final com.kadikular.quantimium.laser.LogisticsMember asProvider = new com.kadikular.quantimium.laser.LogisticsMember() {
        @Override
        public Role role() {
            return Role.PROVIDER;
        }

        @Override
        public boolean active() {
            return false;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public boolean lets(net.minecraft.world.item.ItemStack stack) {
            return false;
        }

        @Override
        public boolean lets(FluidStack fluid) {
            return true;
        }

        @Override
        @Nullable
        public net.neoforged.neoforge.items.IItemHandler inventory() {
            return null;
        }

        @Override
        public IFluidHandler fluids() {
            return networkTanks();
        }

        @Override
        public BlockPos inventoryPos() {
            return worldPosition;
        }
    };

    @Override
    @Nullable
    public com.kadikular.quantimium.laser.LogisticsMember logistics() {
        return collects() ? asProvider : null;
    }

    public net.minecraft.world.SimpleContainer getFilter() {
        return filter;
    }

    public boolean allows() {
        return allow;
    }

    public void toggleAllow() {
        allow = !allow;
        setChanged();
        sync();
    }

    /** Lists the fluid {@code stack} holds (or stands for) in entry {@code slot}; anything else clears it. */
    public void setFilterSlot(int slot, net.minecraft.world.item.ItemStack stack) {
        if (slot >= 0 && slot < FILTER_SLOTS) filter.setItem(slot, com.kadikular.quantimium.recipe.FluidEntry.asFluid(stack));
    }

    /** Whether its filter lets it catch {@code fluid} when it comes back up the light. */
    public boolean acceptsFluid(FluidStack fluid) {
        boolean any = false;
        boolean listed = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            net.minecraft.world.item.ItemStack entry = filter.getItem(i);
            if (entry.isEmpty()) continue;
            any = true;
            if (com.kadikular.quantimium.recipe.FluidEntry.sameFluid(entry, fluid)) listed = true;
        }
        return !any || listed == allow;
    }

    /** What a pipe on {@code side} sees, or null for a face that's off. With no side (Jade, say), every tank. */
    @Nullable
    public ResourceHandler<FluidResource> automation(@Nullable Direction side) {
        if (side == null) return tanks.handler(sided[Direction.UP.get3DDataValue()]);
        if (config(side).fluidMode() == com.kadikular.quantimium.block.entity.simulation.SideMode.DISABLED) return null;
        return tanks.handler(sided[side.get3DDataValue()]);
    }

    // ---- automation ----

    /** Staggered and paced as a Laser Port: quicker each time something moved, slower each time nothing did. */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, LaserTankBlockEntity tank) {
        if (tank.transferIn < 0) tank.transferIn = Math.floorMod(pos.hashCode(), AUTO_TRANSFER_INTERVAL);
        if (tank.transferIn-- > 0) return;
        boolean moved = false;
        for (Direction side : Direction.values()) {
            var config = tank.config(side);
            if (!config.autoFluidInput() && !config.autoFluidOutput()) continue;
            IFluidHandler neighbour = tank.neighbour(side);
            if (neighbour == null) continue;
            if (config.autoFluidInput() && config.fluidMode().allowsInput() && tank.sends()) moved |= tank.pull(neighbour);
            if (config.autoFluidOutput() && config.fluidMode().allowsOutput()) moved |= tank.push(neighbour);
        }
        tank.transferInterval = moved ? Math.max(BUSIEST_INTERVAL, tank.transferInterval / 2) : Math.min(AUTO_TRANSFER_INTERVAL, tank.transferInterval * 2);
        tank.transferIn = tank.transferInterval - 1;
    }

    @Nullable
    private IFluidHandler neighbour(Direction side) {
        if (level == null) return null;
        return com.kadikular.quantimium.util.LegacyFluids.legacy(level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                worldPosition.relative(side), side.getOpposite()));
    }

    /** Draws from {@code from} as far as its tanks take it; whether anything came. */
    private boolean pull(IFluidHandler from) {
        FluidStack offered = from.drain(AUTO_TRANSFER_MB, IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) return false;
        int room = tanks.fill(offered, true);
        if (room <= 0) return false;
        FluidStack drained = from.drain(offered.copyWithAmount(room), IFluidHandler.FluidAction.EXECUTE);
        return tanks.fill(drained, false) > 0;
    }

    /** Pours what its tanks hold into {@code into}, every tank, as far as it takes them; whether anything went. */
    private boolean push(IFluidHandler into) {
        boolean moved = false;
        for (int tank = 0; tank < TANKS; tank++) {
            FluidStack there = tanks.get(tank);
            if (there.isEmpty()) continue;
            int filled = into.fill(there.copyWithAmount(Math.min(AUTO_TRANSFER_MB, there.getAmount())), IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) {
                tanks.drain(tank, filled, false);
                moved = true;
            }
        }
        return moved;
    }

    /**
     * Pours {@code fluid} straight into what it exports to (its faces set to push out), so what comes up the light needn't
     * wait in its tanks for the next push; how much went.
     */
    private int passOn(FluidStack fluid, boolean simulate) {
        if (level == null || fluid.isEmpty()) return 0;
        int went = 0;
        for (Direction side : Direction.values()) {
            var config = config(side);
            if (!config.autoFluidOutput() || !config.fluidMode().allowsOutput()) continue;
            IFluidHandler neighbour = neighbour(side);
            if (neighbour == null) continue;
            went += neighbour.fill(fluid.copyWithAmount(fluid.getAmount() - went),
                    simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (went >= fluid.getAmount()) break;
        }
        if (!simulate && went > 0) {
            transferInterval = BUSIEST_INTERVAL;
            transferIn = Math.min(transferIn, BUSIEST_INTERVAL);
        }
        return went;
    }

    @Override
    public List<com.kadikular.quantimium.block.entity.simulation.SideConfig> getSideConfigs() {
        return List.copyOf(sideConfigs);
    }

    @Override
    public void applySideConfigs(List<com.kadikular.quantimium.block.entity.simulation.SideConfig> configs) {
        sideConfigs = PROFILE.sanitize(configs);
        MachineAccess.sidesChanged(this);
    }

    @Override
    public com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile sideAutomationProfile() {
        return PROFILE;
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
        // What it turns away goes on up the light, to the next; what it catches goes on to what it exports to first.
        if (!collects() || !acceptsFluid(fluid)) return 0;
        int passed = passOn(fluid, simulate);
        return passed + (passed < fluid.getAmount() ? tanks.fill(fluid.copyWithAmount(fluid.getAmount() - passed), simulate) : 0);
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

    /** The channel it serves ({@link com.kadikular.quantimium.laser.Channels}): 0 unless set. */
    private int channel;

    @Override
    public int channel() {
        return channel;
    }

    /** Sets its channel: the network works itself out again. */
    public void setChannel(int channel) {
        this.channel = com.kadikular.quantimium.laser.Channels.of(channel);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            com.kadikular.quantimium.laser.LaserNetworks.changed(level);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Channel", channel);
        out.store("Tanks", FluidStack.OPTIONAL_CODEC.listOf(), tanks.contents());
        out.putInt("RelayMode", mode.ordinal());
        List<net.minecraft.world.item.ItemStack> entries = new java.util.ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) entries.add(filter.getItem(i));
        out.store("Filter", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf(), entries);
        out.putBoolean("Allow", allow);
        CompoundTag sides = new CompoundTag();
        sides.put("List", com.kadikular.quantimium.block.entity.simulation.SideConfig.saveAll(sideConfigs));
        out.store("SideConfigs", CompoundTag.CODEC, sides);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        channel = com.kadikular.quantimium.laser.Channels.of(in.getIntOr("Channel", 0));
        tanks.load(in.read("Tanks", FluidStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        LaserPortBlockEntity.Mode[] modes = LaserPortBlockEntity.Mode.values();
        mode = modes[Math.floorMod(in.getIntOr("RelayMode", 0), modes.length)];
        List<net.minecraft.world.item.ItemStack> entries = in.read("Filter", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < FILTER_SLOTS; i++) filter.getItems().set(i, i < entries.size() ? entries.get(i) : net.minecraft.world.item.ItemStack.EMPTY);
        allow = in.getBooleanOr("Allow", true);
        in.read("SideConfigs", CompoundTag.CODEC).ifPresent(tag -> sideConfigs = PROFILE.sanitize(
                com.kadikular.quantimium.block.entity.simulation.SideConfig.loadAll(tag.getListOrEmpty("List"))));
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
        return MachineAccess.withinReach(this, player);
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
