package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.LegacyEnergy;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.item.DockBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Entangled Dock: superposes a charged item with itself. Used with an item that holds FE, it binds
 * to it and keeps a copy of it on show, and the item stays in the player's hand; from then on the dock
 * charges that item wherever its owner carries it, from its own buffer, while the dock's chunk is
 * loaded. Every second it finds the item (by the binding's id, anywhere in the owner's inventory) and
 * tops it up by {@value #CHARGE_PER_TICK} FE/t's worth, emitting flux for it like any machine.
 *
 * <p>Held in a chunk at Critical anomaly the entanglement decoheres: the binding is dropped and the
 * item simply stops being charged. Sneaking with an empty hand lets it go on purpose.
 */
public class EntangledDockBlockEntity extends BlockEntity implements QuantumEnergyHost, FluxMeterReadout {

    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final int ENERGY_MAX_RECEIVE = 20_000;
    public static final int CHARGE_PER_TICK = 2_000;
    public static final int INTERVAL = 20;
    /** From this band of anomaly in its chunk, the entanglement does not hold. */
    public static final FluxBand DECOHERES_AT = FluxBand.CRITICAL;

    public static final int STATUS_UNBOUND = 0;
    public static final int STATUS_CHARGING = 1;
    public static final int STATUS_FULL = 2;
    public static final int STATUS_AWAY = 3;
    public static final int STATUS_NO_POWER = 4;

    private final QuantumEnergyStorage energyStorage = new QuantumEnergyStorage(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };

    @Nullable
    private UUID bindingId;
    @Nullable
    private UUID owner;
    /** A copy of the bound item, for show: the ghost the dock holds. */
    private ItemStack shown = ItemStack.EMPTY;
    private int status = STATUS_UNBOUND;
    private int lastMoved;

    public EntangledDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ENTANGLED_DOCK_BE.get(), pos, state);
    }

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ItemStack shown() {
        return shown;
    }

    public int status() {
        return status;
    }

    public boolean isBound() {
        return bindingId != null;
    }

    /** Whether {@code stack} can be entangled with a dock: anything that holds FE. */
    public static boolean canBind(ItemStack stack) {
        return !stack.isEmpty() && LegacyEnergy.item(stack) != null;
    }

    /**
     * Superposes {@code stack} with this dock: it keeps the item as it is, marked with a fresh binding,
     * and shows a copy. Any item bound before stops matching and is left alone.
     */
    public void bind(Player player, ItemStack stack) {
        if (level == null || !canBind(stack)) return;
        bindingId = UUID.randomUUID();
        owner = player.getUUID();
        stack.set(ModDataComponents.DOCK_BINDING.get(), new DockBinding(GlobalPos.of(level.dimension(), worldPosition), bindingId));
        shown = stack.copyWithCount(1);
        shown.remove(ModDataComponents.DOCK_BINDING.get());
        status = STATUS_AWAY;
        sync();
    }

    /** Lets the entanglement go: the item keeps its mark, but nothing charges it any more. */
    public void release() {
        bindingId = null;
        owner = null;
        shown = ItemStack.EMPTY;
        status = STATUS_UNBOUND;
        sync();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EntangledDockBlockEntity dock) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % INTERVAL != 0) return;
        if (dock.bindingId == null) return;
        if (QuantumFlux.chunk(server, pos).anomalyBand().ordinal() >= DECOHERES_AT.ordinal()) {
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    30, 0.25, 0.25, 0.25, 0.1);
            dock.release();
            return;
        }
        dock.charge(server);
    }

    /** Finds the bound item on its owner and tops it up from the buffer. */
    private void charge(ServerLevel level) {
        int previous = status;
        lastMoved = 0;
        ItemStack item = find(level);
        if (item == null) {
            status = STATUS_AWAY;
        } else {
            IEnergyStorage energy = LegacyEnergy.item(item);
            int want = energy == null ? 0 : energy.receiveEnergy(CHARGE_PER_TICK * INTERVAL, true);
            if (energy == null || want <= 0) {
                status = STATUS_FULL;
            } else if (energyStorage.getEnergyStored() <= 0) {
                status = STATUS_NO_POWER;
            } else {
                int moved = energy.receiveEnergy(Math.min(want, energyStorage.getEnergyStored()), false);
                energyStorage.consume(moved);
                lastMoved = moved;
                status = STATUS_CHARGING;
                // Like any machine, it emits flux for the power it spends.
                QuantumFlux.emitFromEnergy(level, worldPosition, moved);
                setChanged();
            }
        }
        if (status != previous) sync();
    }

    /** The bound item in its owner's inventory, if they are about and still carry it. */
    @Nullable
    private ItemStack find(ServerLevel level) {
        if (owner == null) return null;
        Player player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) player = level.getPlayerByUUID(owner);
        if (player == null) return null;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            DockBinding binding = stack.get(ModDataComponents.DOCK_BINDING.get());
            if (binding != null && binding.id().equals(bindingId)) return stack;
        }
        return null;
    }

    @Override
    public MutableComponent fluxMeterLine() {
        if (bindingId == null) return Component.translatable("item.quantimium.flux_meter.dock.unbound");
        return Component.translatable("item.quantimium.flux_meter.dock.bound", shown.getHoverName(),
                FluxMeterReadout.fe(lastMoved / INTERVAL));
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Energy", energyStorage.getEnergyStored());
        if (bindingId != null) tag.store("Binding", net.minecraft.core.UUIDUtil.CODEC, bindingId);
        if (owner != null) tag.store("Owner", net.minecraft.core.UUIDUtil.CODEC, owner);
        if (!shown.isEmpty()) tag.put("Shown", NbtCompat.saveStack(registries, shown));
        tag.putInt("Status", status);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        bindingId = com.kadikular.quantimium.util.NbtCompat.hasUUID(tag, "Binding") ? com.kadikular.quantimium.util.NbtCompat.getUUID(tag, "Binding") : null;
        owner = com.kadikular.quantimium.util.NbtCompat.hasUUID(tag, "Owner") ? com.kadikular.quantimium.util.NbtCompat.getUUID(tag, "Owner") : null;
        shown = tag.contains("Shown") ? NbtCompat.parseStack(registries, tag.getCompoundOrEmpty("Shown")) : ItemStack.EMPTY;
        status = tag.getIntOr("Status", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (!shown.isEmpty()) tag.put("Shown", NbtCompat.saveStack(registries, shown));
        tag.putInt("Status", status);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
