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
        if (!this.members.equals(members)) {
            offersStale = true;
            spots = null;
        }
        this.members = List.copyOf(members);
        boolean changed = this.clash != clash;
        this.clash = clash;
        if (changed) showLit();
    }

    /** The offers as last worked out, when, and whether something has moved since. */
    private com.kadikular.quantimium.laser.OfferTable offers = com.kadikular.quantimium.laser.OfferTable.PENDING;
    private long offersTick = Long.MIN_VALUE;
    private boolean offersStale = true;
    /** The fewest ticks between workings out of what the network offers, after a move a player made: none. */
    private static final int OFFERS_SOONEST = 0;
    /** The fewest ticks between workings out of what the network offers while someone's watching it change by itself. */
    private static final int OFFERS_WATCHED = 4;
    /** How long a network goes unlooked-at before a look counts as a player just opening a screen on it. */
    private static final int WATCH_LAPSE = 40;
    private boolean workingOut;

    /**
     * The offers as last worked out. They're worked out again at most once a second, sooner after a move it saw (but
     * not more than every few ticks, however busy the network), a step at a time in the background (see
     * {@link com.kadikular.quantimium.work.WorkQueue}): till that's done, what it had stands. Never read while being
     * worked out (a network that reads another's offers which read this one back gets what this one last had).
     */
    @Override
    public com.kadikular.quantimium.laser.OfferTable offers() {
        if (level == null || level.isClientSide()) return offers;
        startBuildIfDue();
        if (workingOut) return offers;
        return live();
    }

    /**
     * The offers with what's really there looked at now, at most once a tick: a deposit or a take shows at once,
     * though what could be made from it waits for the offers to be worked out again.
     */
    private com.kadikular.quantimium.laser.OfferTable live() {
        long now = level == null ? 0 : level.getGameTime();
        // Read again while it's being looked at (a network reading another that reads this one back): what it had.
        if (looking) return liveOffers != null ? liveOffers : offers;
        if (liveOffers == null || liveTick != now || liveFrom != offers) {
            java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> stored;
            looking = true;
            try {
                stored = com.kadikular.quantimium.laser.OfferTable.storedNow(this, worldPosition, members());
            } finally {
                looking = false;
            }
            // What's there has moved since the offers were worked out (a hopper, a pipe, a hand in a chest), in a way
            // that could change what could be made.
            drifted = matters(stored, offers.stored(), offers);
            // Nothing moved since last tick: the same table, so what's read from it (a terminal's list) is kept too.
            if (liveOffers == null || liveFrom != offers || !stored.equals(liveOffers.stored())) liveOffers = offers.withStored(stored);
            liveTick = now;
            liveFrom = offers;
        }
        return liveOffers;
    }

    /**
     * Whether what's there now ({@code now}) differs from what {@code table} was worked out from ({@code was}) in a way
     * that could change what could be made: something new, or more or less of something that goes into what's made.
     * A quarry filling the network with stone no machine on it uses changes only what's there, which shows at once
     * anyway. Every few seconds any change counts: more of something could make a recipe possible.
     */
    private boolean matters(java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> now,
                            java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, Long> was,
                            com.kadikular.quantimium.laser.OfferTable table) {
        if (now.equals(was)) return false;
        if (level == null || offersTick == Long.MIN_VALUE || level.getGameTime() - offersTick >= ANY_CHANGE_TICKS) return true;
        java.util.Set<net.minecraft.world.item.Item> inputs = table.inputs();
        for (var entry : now.entrySet()) {
            Long before = was.get(entry.getKey());
            if (before == null) return true;
            if (!before.equals(entry.getValue()) && inputs.contains(entry.getKey().getItem())) return true;
        }
        for (var item : was.keySet()) {
            if (!now.containsKey(item) && inputs.contains(item.getItem())) return true;
        }
        return false;
    }

    /** How long since the offers were last worked out before any change at all is worth working them out again for. */
    private static final int ANY_CHANGE_TICKS = 100;

    @org.jetbrains.annotations.Nullable
    private com.kadikular.quantimium.laser.OfferTable liveOffers;
    @org.jetbrains.annotations.Nullable
    private com.kadikular.quantimium.laser.OfferTable liveFrom;
    private long liveTick = Long.MIN_VALUE;
    /** Whether what's really there differs from what the offers were worked out from. */
    private boolean drifted;
    /** Whether what's really there is being looked at now. */
    private boolean looking;

    /** Starts working the offers out again, in the background, if it's time. */
    private void startBuildIfDue() {
        if (level == null || workingOut || building != null) return;
        long now = level.getGameTime();
        // At once after a move a player made; soon after one anything made (a pipe, a hopper) while someone's watching,
        // though not every tick on a busy network; else once a second.
        int wait = offersStale ? OFFERS_SOONEST : drifted && watched ? OFFERS_WATCHED : 20;
        if (offersTick != Long.MIN_VALUE && now - offersTick < wait) return;
        offersTick = now;
        offersStale = false;
        drifted = false;
        building = new com.kadikular.quantimium.laser.OfferTable.Builder(this, worldPosition, members());
        com.kadikular.quantimium.work.WorkQueue.submit(this, urgent ? com.kadikular.quantimium.work.WorkQueue.URGENT
                : com.kadikular.quantimium.work.WorkQueue.NORMAL, this::buildStep);
        watched = false;
        urgent = false;
    }

    /** A step of working the offers out; whether they're done. */
    private boolean buildStep() {
        var builder = building;
        if (builder == null || isRemoved()) {
            building = null;
            return true;
        }
        workingOut = true;
        try {
            if (!builder.step()) return false;
        } catch (RuntimeException failed) {
            // Let go, so it's worked out afresh next time rather than waiting on a build that's been dropped.
            building = null;
            throw failed;
        } finally {
            workingOut = false;
        }
        offers = builder.result();
        building = null;
        // Compared afresh with what's there now, next time it's read: not with the offers this replaced.
        drifted = false;
        liveOffers = null;
        // It read another network before that one had worked its offers out: soon again, to catch them.
        if (builder.early()) offersStale = true;
        // Its Crafters show what it now says they make.
        builder.handOut();
        // A Crafter changed what it could make while this was worked out: again, whether or not anything's reading.
        if (offersStale) startBuildIfDue();
        return true;
    }

    /** The offers being worked out, a step at a time; null when they're not. */
    @org.jetbrains.annotations.Nullable
    private com.kadikular.quantimium.laser.OfferTable.Builder building;
    /** Whether a player is looking at what it offers (a terminal open): it's worked out again sooner as it changes. */
    private boolean watched;
    /**
     * Whether a player is waiting on the next working out (they just opened a screen on it, or moved something): that
     * one goes first, with more of the tick. Only that one: a screen left open on a busy network doesn't keep the
     * server working out its offers flat out.
     */
    private boolean urgent;
    private long watchedTick = Long.MIN_VALUE;

    @Override
    public void watched() {
        long now = level == null ? 0 : level.getGameTime();
        if (watchedTick == Long.MIN_VALUE || now - watchedTick > WATCH_LAPSE) hurry();
        watchedTick = now;
        watched = true;
    }

    /** The next working out, or the one under way, goes first. */
    private void hurry() {
        urgent = true;
        com.kadikular.quantimium.work.WorkQueue.hurry(this, com.kadikular.quantimium.work.WorkQueue.URGENT);
    }

    @Override
    public void stockMoved() {
        offersStale = true;
        shifted = true;
        // Where things are stands for the tick: each take checks the slot still holds it, and looks again if short.
        liveOffers = null;
        watched = true;
        // A player moved it: what can be made from what's left comes first. Not every click of one taking over and over,
        // though: what's stored shows at once anyway, and the server isn't held at the most it'll spend for them.
        long now = level == null ? 0 : level.getGameTime();
        if (hurriedTick == Long.MIN_VALUE || now - hurriedTick >= HURRY_EVERY) {
            hurriedTick = now;
            hurry();
        }
    }

    /** The fewest ticks between a player's moves making the network's working out urgent. */
    private static final int HURRY_EVERY = 10;
    private long hurriedTick = Long.MIN_VALUE;

    private long pokedTick = Long.MIN_VALUE;

    @Override
    public void poke() {
        long now = level == null ? 0 : level.getGameTime();
        if (pokedTick != Long.MIN_VALUE && now - pokedTick < 20) return;
        pokedTick = now;
        offers();
    }

    @Override
    public void makerChanged() {
        offersStale = true;
        // Whether or not anything reads its offers soon: the Crafter shows what they say it makes.
        startBuildIfDue();
    }

    @Override
    public void movedQuietly() {
        liveOffers = null;
    }

    /** Where each thing on the network is, and the tick it was looked over. */
    @org.jetbrains.annotations.Nullable
    private java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, java.util.List<com.kadikular.quantimium.laser.Logistics.Spot>> spots;
    private long spotsTick = Long.MIN_VALUE;

    @Override
    public java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, java.util.List<com.kadikular.quantimium.laser.Logistics.Spot>> spots() {
        long now = level == null ? 0 : level.getGameTime();
        // Where things are doesn't depend on who's asking (a Crafter's previews are what it shows, whoever asks; whether
        // one could make it now is asked of the slot when it's taken): kept for the tick, mid-craft too.
        if (spots == null || spotsTick != now) {
            spots = com.kadikular.quantimium.laser.Logistics.locate(members());
            spotsTick = now;
        }
        return spots;
    }

    /** The tick spots were last looked over again because a take came up short, and whether things have moved since. */
    private long relocatedTick = Long.MIN_VALUE;
    private boolean shifted;

    @Override
    public void stockShifted() {
        shifted = true;
    }

    @Override
    public java.util.Map<net.neoforged.neoforge.transfer.item.ItemResource, java.util.List<com.kadikular.quantimium.laser.Logistics.Spot>> spotsAgain() {
        long now = level == null ? 0 : level.getGameTime();
        // Once a tick at most, unless something's been put somewhere new since: what's been put in is found.
        if (relocatedTick == now && !shifted) return null;
        relocatedTick = now;
        shifted = false;
        spots = com.kadikular.quantimium.laser.Logistics.locate(members());
        spotsTick = now;
        return spots;
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

    @Override
    public int collectFluid(net.neoforged.neoforge.fluids.FluidStack fluid, boolean simulate) {
        return com.kadikular.quantimium.laser.Logistics.insertFluid(members(), fluid, simulate, true);
    }

    @Override
    public net.neoforged.neoforge.fluids.FluidStack sendFluid(java.util.function.Predicate<net.neoforged.neoforge.fluids.FluidStack> wants, int max, boolean simulate) {
        return com.kadikular.quantimium.laser.Logistics.takeFluid(members(), wants, max, simulate);
    }

    @Override
    public int takeBackFluid(net.neoforged.neoforge.fluids.FluidStack fluid) {
        return com.kadikular.quantimium.laser.Logistics.insertFluid(members(), fluid, false, true);
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
