package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.FoldCoreBlock;
import com.kadikular.quantimium.block.QuantumFoundryStructure;
import com.kadikular.quantimium.fold.FoldChamber;
import com.kadikular.quantimium.fold.FoldedStructure;
import com.kadikular.quantimium.fold.Folding;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.item.FoldedTesseractItem;
import com.kadikular.quantimium.item.TesseractItem;
import com.kadikular.quantimium.menu.FoldCoreMenu;
import com.kadikular.quantimium.util.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Fold Chamber's core: a socket for the Tesseract, an energy buffer, and the fold itself.
 *
 * <p>Whatever sits in the socket is checked against the chamber twice a second, and the result is the
 * {@link Status} and message the screen shows: what's wrong, or what a fold would take. Nothing moves
 * until the Fold button is pressed. Then the chamber seals for {@link #SEAL_TICKS}, checks again, and
 * folds or unfolds in a single tick, paying {@link Folding#FE_PER_BLOCK} for every block it folds;
 * unfolding is free. The socket is locked while it seals.
 *
 * <p>Reading the chamber also lights its frame, or darkens one that broke.
 */
public class FoldCoreBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SEAL_TICKS = 60;
    public static final int ENERGY_CAPACITY = 10_000_000;
    public static final int MAX_RECEIVE = 100_000;
    public static final int BUTTON_FOLD = 0;
    public static final int DATA_COUNT = 8;
    private static final int STATUS_TICKS = 10;
    private static final double MESSAGE_RANGE = 16.0;

    /** What the screen says about the socket. Ordinal is synced, so only ever add at the end. */
    public enum Status { EMPTY, NO_CHAMBER, REFUSED, NO_POWER, READY, SEALING }

    private enum Mode { IDLE, FOLDING, UNFOLDING, COLLAPSING }

    private final ItemStackHandler socket = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof TesseractItem || stack.getItem() instanceof FoldedTesseractItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        /** Locked while it seals: the fold is already counting on what's in there. */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return mode == Mode.IDLE ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        /** The socket shows in the world, so clients hear of every change. */
        @Override
        protected void onContentsChanged(int slot) {
            statusStale = true;
            sync();
        }
    };

    private final QuantumEnergyStorage energy = new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };

    private Mode mode = Mode.IDLE;
    private int sealed;
    @Nullable
    private UUID operator;
    /** The frame last lit, so a broken one can be darkened: null for none. */
    @Nullable
    private FoldChamber lit;

    private Status status = Status.EMPTY;
    private Component message = Component.empty();
    private Component chamberLine = Component.empty();
    private int cost;
    private boolean statusStale = true;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy.getEnergyStored() & 0xFFFF;
                case 1 -> energy.getEnergyStored() >>> 16;
                case 2 -> energy.getMaxEnergyStored() & 0xFFFF;
                case 3 -> energy.getMaxEnergyStored() >>> 16;
                case 4 -> status.ordinal();
                case 5 -> sealed;
                case 6 -> cost & 0xFFFF;
                case 7 -> cost >>> 16;
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

    public FoldCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FOLD_CORE_BE.get(), pos, state);
    }

    public ItemStackHandler getSocket() {
        return socket;
    }

    public ItemStack getHeld() {
        return socket.getStackInSlot(0);
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energy;
    }

    public ContainerData getContainerData() {
        return data;
    }

    public boolean isSealing() {
        return mode != Mode.IDLE;
    }

    public Status getStatus() {
        return status;
    }

    /** The status line's detail: the refusal, or what the fold will take. Synced to the client. */
    public Component getMessage() {
        return message;
    }

    /** The chamber's size and front, or why there isn't one. Synced to the client. */
    public Component getChamberLine() {
        return chamberLine;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FoldCoreBlockEntity core) {
        if (!(level instanceof ServerLevel server)) return;
        if (core.mode == Mode.IDLE) {
            if (core.statusStale || level.getGameTime() % STATUS_TICKS == 0) core.refreshStatus(server);
            return;
        }
        core.sealed++;
        FoldChamber.Detection detection = FoldChamber.detect(server, pos);
        if (!detection.found()) {
            core.stop(server, detection.problem());
            return;
        }
        core.shimmer(server, detection.chamber());
        if (core.sealed < SEAL_TICKS) return;
        core.complete(server, detection.chamber());
    }

    /** The Fold button: starts sealing if the chamber can take what's in the socket, else says why. */
    public void start(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || mode != Mode.IDLE) return;
        refreshStatus(server);
        if (status != Status.READY) {
            player.sendOverlayMessage(message);
            return;
        }
        FoldChamber.Detection detection = FoldChamber.detect(server, worldPosition);
        mode = getHeld().getItem() instanceof FoldedTesseractItem ? Mode.UNFOLDING
                : detection.found() && Folding.collapses(server, detection.chamber(), getHeld()) ? Mode.COLLAPSING
                : Mode.FOLDING;
        sealed = 0;
        operator = player.getUUID();
        server.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.6f);
        setStatus(Status.SEALING, Component.translatable(mode == Mode.FOLDING
                ? "message.quantimium.fold.sealing" : "message.quantimium.fold.unsealing"), cost);
        setChanged();
    }

    /**
     * Reads the chamber and what's in the socket, and says what would happen: the refusal, a shortfall
     * of power, or what a fold would take.
     */
    private void refreshStatus(ServerLevel server) {
        statusStale = false;
        FoldChamber.Detection detection = FoldChamber.detect(server, worldPosition);
        lightFrame(server, detection);
        Component chamber = detection.found()
                ? Component.translatable("gui.quantimium.fold_core.chamber", detection.chamber().worldDimensions())
                : Component.translatable("gui.quantimium.fold_core.no_chamber");
        if (!chamber.equals(chamberLine)) {
            chamberLine = chamber;
            sync();
        }

        ItemStack held = getHeld();
        if (held.isEmpty()) {
            setStatus(Status.EMPTY, Component.translatable("gui.quantimium.fold_core.hint"), 0);
            return;
        }
        if (!detection.found()) {
            setStatus(Status.NO_CHAMBER, detection.problem(), 0);
            return;
        }
        int needed;
        Component ready;
        if (held.getItem() instanceof FoldedTesseractItem) {
            Optional<Component> problem = Folding.checkUnfold(server, detection.chamber(), held);
            if (problem.isPresent()) {
                setStatus(Status.REFUSED, problem.get(), 0);
                return;
            }
            FoldedStructure folded = held.get(ModDataComponents.FOLDED.get());
            needed = Folding.unfoldEnergy(folded);
            ready = Component.translatable("gui.quantimium.fold_core.ready_unfold", folded.cells().size());
        } else if (Folding.collapses(server, detection.chamber(), held)) {
            needed = Folding.SINGULARITY_FE;
            ready = Component.translatable("gui.quantimium.fold_core.ready_collapse");
        } else {
            Folding.FoldCheck check = Folding.planFold(server, detection.chamber(), held);
            if (check.plan() == null) {
                setStatus(Status.REFUSED, check.problem(), 0);
                return;
            }
            needed = check.plan().energy();
            ready = Component.translatable("gui.quantimium.fold_core.ready_fold",
                    server.getBlockState(check.plan().controller()).getBlock().getName(), check.plan().blocks());
        }
        if (energy.getEnergyStored() < needed) {
            setStatus(Status.NO_POWER, Component.translatable("gui.quantimium.fold_core.no_power", needed), needed);
            return;
        }
        setStatus(Status.READY, ready, needed);
    }

    private void setStatus(Status next, Component detail, int needed) {
        cost = needed;
        if (next == status && detail.equals(message)) return;
        status = next;
        message = detail;
        sync();
    }

    private void complete(ServerLevel server, FoldChamber chamber) {
        ItemStack held = getHeld();
        int needed = switch (mode) {
            case FOLDING -> Optional.ofNullable(Folding.planFold(server, chamber, held).plan())
                    .map(Folding.FoldPlan::energy).orElse(0);
            case COLLAPSING -> Folding.SINGULARITY_FE;
            default -> Folding.unfoldEnergy(held.get(ModDataComponents.FOLDED.get()));
        };
        if (energy.getEnergyStored() < needed) {
            stop(server, Component.translatable("gui.quantimium.fold_core.no_power", needed));
            return;
        }
        ItemStack result;
        if (mode == Mode.COLLAPSING) {
            result = Folding.collapse(server, chamber, held);
            if (result.isEmpty()) {
                stop(server, Component.translatable("message.quantimium.fold.collapse_failed"));
                return;
            }
        } else if (mode == Mode.FOLDING) {
            Folding.FoldResult folded = Folding.fold(server, chamber, held);
            if (!folded.folded()) {
                stop(server, folded.problem());
                return;
            }
            result = folded.stack();
        } else {
            Optional<Component> problem = Folding.checkUnfold(server, chamber, held);
            if (problem.isPresent()) {
                stop(server, problem.get());
                return;
            }
            result = Folding.unfold(server, chamber, held);
        }
        energy.consume(needed);
        Mode done = mode;
        mode = Mode.IDLE;
        sealed = 0;
        socket.setStackInSlot(0, result);
        server.playSound(null, worldPosition, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 1.0f,
                done == Mode.UNFOLDING ? 1.4f : done == Mode.COLLAPSING ? 0.3f : 0.6f);
        tell(server, Component.translatable(switch (done) {
            case UNFOLDING -> "message.quantimium.fold.unfolded";
            case COLLAPSING -> "message.quantimium.fold.collapsed";
            default -> "message.quantimium.fold.folded";
        }));
        burst(server, chamber);
        refreshStatus(server);
        setChanged();
    }

    /** Gives up on a fold, keeping whatever went in, and says why. */
    private void stop(ServerLevel server, Component problem) {
        server.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.4f);
        mode = Mode.IDLE;
        sealed = 0;
        tell(server, problem);
        setStatus(Status.REFUSED, problem, cost);
        statusStale = true;
        setChanged();
    }

    private void tell(ServerLevel server, Component message) {
        if (operator != null && server.getPlayerByUUID(operator) instanceof ServerPlayer player
                && player.distanceToSqr(worldPosition.getCenter()) < MESSAGE_RANGE * MESSAGE_RANGE * 4) {
            player.sendOverlayMessage(message);
            return;
        }
        List<ServerPlayer> near = server.getPlayers(p -> p.distanceToSqr(worldPosition.getCenter())
                < MESSAGE_RANGE * MESSAGE_RANGE);
        for (ServerPlayer player : near) player.sendOverlayMessage(message);
    }

    /** The field closing in: sparks drawn in from the volume towards the core. */
    private void shimmer(ServerLevel server, FoldChamber chamber) {
        if (sealed % 2 != 0) return;
        int h = chamber.half();
        var random = server.getRandom();
        for (int i = 0; i < 6; i++) {
            BlockPos cell = chamber.at(random.nextInt(2 * h + 1) - h, random.nextInt(chamber.height()),
                    random.nextInt(chamber.depth()));
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, cell.getX() + 0.5, cell.getY() + 0.5,
                    cell.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private void burst(ServerLevel server, FoldChamber chamber) {
        BlockPos middle = chamber.at(0, chamber.height() / 2, chamber.depth() / 2);
        double spreadX = chamber.across().getAxis() == Direction.Axis.X ? chamber.half() : chamber.depth() / 2.0;
        double spreadZ = chamber.across().getAxis() == Direction.Axis.Z ? chamber.half() : chamber.depth() / 2.0;
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, middle.getX() + 0.5, middle.getY() + 0.5,
                middle.getZ() + 0.5, 160, spreadX, chamber.height() / 2.0, spreadZ, 0.1);
    }

    /** Lights a whole frame and darkens one that broke. */
    private void lightFrame(ServerLevel server, FoldChamber.Detection detection) {
        if (detection.found()) {
            light(server, detection.chamber());
            return;
        }
        if (lit == null) return;
        setFrameFormed(server, lit, false);
        lit = null;
        setChanged();
    }

    private void light(ServerLevel server, FoldChamber chamber) {
        if (chamber.equals(lit)) return;
        if (lit != null) setFrameFormed(server, lit, false);
        setFrameFormed(server, chamber, true);
        lit = chamber;
        setChanged();
    }

    private static void setFrameFormed(ServerLevel server, FoldChamber chamber, boolean formed) {
        chamber.forEachFrameCell((pos, part, axis) -> {
            if (!server.isLoaded(pos)) return;
            BlockState state = server.getBlockState(pos);
            if (state.hasProperty(QuantumFoundryStructure.FORMED) && state.getValue(QuantumFoundryStructure.FORMED) != formed
                    && (part != FoldChamber.Part.CORE || pos.equals(chamber.core()))) {
                server.setBlock(pos, state.setValue(QuantumFoundryStructure.FORMED, formed), Block.UPDATE_CLIENTS);
            }
        });
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server && lit != null) setFrameFormed(server, lit, false);
        ItemStack held = getHeld();
        if (level != null && !held.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), held);
        socket.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.fold_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FoldCoreMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Socket", ItemStack.OPTIONAL_CODEC, getHeld());
        out.putInt("Energy", energy.getEnergyStored());
        out.putString("Mode", mode.name());
        out.putInt("Sealed", sealed);
        if (operator != null) out.store("Operator", UUIDUtil.CODEC, operator);
        if (lit != null) {
            out.putInt("LitWidth", lit.width());
            out.putInt("LitHeight", lit.height());
            out.putInt("LitDepth", lit.depth());
        }
        out.putString("Status", status.name());
        out.store("Message", ComponentSerialization.CODEC, message);
        out.store("Chamber", ComponentSerialization.CODEC, chamberLine);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        // "Held" is where the first build kept it, before the core had a screen.
        socket.setStackInSlot(0, in.read("Socket", ItemStack.OPTIONAL_CODEC)
                .or(() -> in.read("Held", ItemStack.CODEC)).orElse(ItemStack.EMPTY));
        energy.setEnergy(in.getIntOr("Energy", 0));
        mode = enumOr(Mode.class, in.getStringOr("Mode", ""), Mode.IDLE);
        sealed = in.getIntOr("Sealed", 0);
        operator = in.read("Operator", UUIDUtil.CODEC).orElse(null);
        int width = in.getIntOr("LitWidth", 0);
        lit = width == 0 ? null : new FoldChamber(worldPosition, getBlockState().getValue(FoldCoreBlock.FACING),
                width, in.getIntOr("LitHeight", 0), in.getIntOr("LitDepth", 0));
        status = enumOr(Status.class, in.getStringOr("Status", ""), Status.EMPTY);
        message = in.read("Message", ComponentSerialization.CODEC).orElse(Component.empty());
        chamberLine = in.read("Chamber", ComponentSerialization.CODEC).orElse(Component.empty());
        statusStale = true;
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
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
}
