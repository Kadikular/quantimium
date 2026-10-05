package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.QuantimiumAdvancements;
import com.kadikular.quantimium.block.ArrayPylonBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.item.SophonItem;
import com.kadikular.quantimium.menu.UnfoldingArrayMenu;
import com.kadikular.quantimium.superposition.SophonRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Unfolding Array: where a body double is made. A pad with a pylon at each corner; you stand on
 * the pad, and over {@value #UNFOLD_TICKS} ticks it unfolds a proton around your pattern and folds it
 * back up with a copy of you inside: a Sophon. It takes a Semi-Stable Tesseract to fold you into,
 * Anomaly Fragments and a Rift Residue, and a great deal of power, drawn as it goes.
 *
 * <p>Step off the pad and it waits for you, losing ground while you are gone. It refuses once you have
 * as many Sophons as the soul can bear ({@link Config#maxDoubles()}).
 */
public class UnfoldingArrayBlockEntity extends BlockEntity implements MenuProvider, QuantumEnergyHost, FluxMeterReadout {

    public static final int ENERGY_CAPACITY = 4_000_000;
    public static final int ENERGY_MAX_RECEIVE = 20_000;
    public static final int FE_PER_TICK = 5_000;
    public static final int UNFOLD_TICKS = 600;
    /** Ground lost each tick you are off the pad. */
    public static final int SLIP_PER_TICK = 3;

    public static final int TESSERACT_SLOT = 0;
    public static final int FRAGMENT_SLOT = 1;
    public static final int RESIDUE_SLOT = 2;
    public static final int OUTPUT_SLOT = 3;
    public static final int TESSERACTS = 1;
    public static final int FRAGMENTS = 4;
    public static final int RESIDUE = 1;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_UNFOLDING = 1;
    public static final int STATUS_STEP_ON = 2;
    public static final int STATUS_NO_PYLONS = 3;
    public static final int STATUS_MISSING = 4;
    public static final int STATUS_NO_POWER = 5;
    public static final int STATUS_AT_CAP = 6;
    public static final int STATUS_OUTPUT_FULL = 7;
    public static final int STATUS_READY = 8;

    public static final int DATA_COUNT = 9;
    public static final int BUTTON_UNFOLD = 0;

    private final ItemStackHandler inventory = new ItemStackHandler(4) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case TESSERACT_SLOT -> stack.is(ModItems.SEMI_STABLE_TESSERACT.get());
                case FRAGMENT_SLOT -> stack.is(ModItems.ANOMALY_FRAGMENT.get());
                case RESIDUE_SLOT -> stack.is(ModItems.RIFT_RESIDUE.get());
                default -> false;
            };
        }
    };

    private final QuantumEnergyStorage energyStorage = new QuantumEnergyStorage(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0) {
        @Override
        protected void onReceived() {
            setChanged();
        }
    };

    /** Who is being unfolded, or null. */
    @Nullable
    private UUID subject;
    private int progress;
    private int status = STATUS_IDLE;
    private long unemittedFe;
    /** The subject's Sophon count, for the screen. */
    private int sophons;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored() & 0xFFFF;
                case 1 -> (energyStorage.getEnergyStored() >>> 16) & 0xFFFF;
                case 2 -> energyStorage.getMaxEnergyStored() & 0xFFFF;
                case 3 -> (energyStorage.getMaxEnergyStored() >>> 16) & 0xFFFF;
                case 4 -> status;
                case 5 -> progress;
                case 6 -> sophons;
                case 7 -> Config.maxDoubles();
                case 8 -> subject != null ? 1 : 0;
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

    public UnfoldingArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNFOLDING_ARRAY_BE.get(), pos, state);
    }

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public int status() {
        return status;
    }

    @Nullable
    public UUID subject() {
        return subject;
    }

    public int progress() {
        return progress;
    }

    /** The pylons' positions: the pad's four diagonal neighbours. */
    public static BlockPos[] pylons(BlockPos pad) {
        return new BlockPos[]{pad.offset(-1, 0, -1), pad.offset(1, 0, -1), pad.offset(-1, 0, 1), pad.offset(1, 0, 1)};
    }

    public boolean pylonsStand() {
        if (level == null) return false;
        for (BlockPos pylon : pylons(worldPosition)) {
            if (!(level.getBlockState(pylon).getBlock() instanceof ArrayPylonBlock)) return false;
        }
        return true;
    }

    private boolean stocked() {
        return inventory.getStackInSlot(TESSERACT_SLOT).getCount() >= TESSERACTS
                && inventory.getStackInSlot(FRAGMENT_SLOT).getCount() >= FRAGMENTS
                && inventory.getStackInSlot(RESIDUE_SLOT).getCount() >= RESIDUE;
    }

    /** Whether {@code player} stands on the pad. */
    public boolean onPad(Player player) {
        return player.blockPosition().equals(worldPosition) && !player.isSpectator();
    }

    /** What stands between {@code player} and unfolding, as a status; {@link #STATUS_READY} if nothing. */
    private int blocker(Player player) {
        if (!pylonsStand()) return STATUS_NO_PYLONS;
        if (!stocked()) return STATUS_MISSING;
        if (!inventory.getStackInSlot(OUTPUT_SLOT).isEmpty()) return STATUS_OUTPUT_FULL;
        if (player instanceof ServerPlayer server
                && SophonRegistry.get(server.level().getServer()).count(player.getUUID()) >= Config.maxDoubles()) return STATUS_AT_CAP;
        return STATUS_READY;
    }

    /** The screen's button: begin unfolding {@code player}, or stop. */
    public void pressButton(ServerPlayer player) {
        if (subject != null) {
            if (subject.equals(player.getUUID())) stop();
            return;
        }
        sophons = SophonRegistry.get(player.level().getServer()).count(player.getUUID());
        int blocker = blocker(player);
        if (blocker != STATUS_READY) {
            status = blocker;
            if (blocker == STATUS_AT_CAP) {
                player.sendSystemMessage(Component.translatable("message.quantimium.array.at_cap")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            return;
        }
        subject = player.getUUID();
        progress = 0;
        status = onPad(player) ? STATUS_UNFOLDING : STATUS_STEP_ON;
        sync();
    }

    private void stop() {
        subject = null;
        progress = 0;
        status = STATUS_IDLE;
        sync();
    }

    // ---- ticking ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, UnfoldingArrayBlockEntity array) {
        if (!(level instanceof ServerLevel server)) return;
        array.work(server);
        if (server.getGameTime() % 20 == 0 && array.unemittedFe > 0) {
            QuantumFlux.emitFromEnergy(server, pos, array.unemittedFe);
            array.unemittedFe = 0;
        }
    }

    private void work(ServerLevel server) {
        if (subject == null) return;
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(subject);
        if (player == null && server.getPlayerByUUID(subject) instanceof ServerPlayer here) player = here;
        if (player == null || player.level() != server || player.isDeadOrDying()) {
            stop();
            return;
        }
        sophons = SophonRegistry.get(server.getServer()).count(subject);
        int previous = status;
        int blocker = blocker(player);
        if (blocker != STATUS_READY) {
            status = blocker;
        } else if (!onPad(player)) {
            status = STATUS_STEP_ON;
            progress = Math.max(0, progress - SLIP_PER_TICK);
        } else if (energyStorage.getEnergyStored() < FE_PER_TICK) {
            status = STATUS_NO_POWER;
        } else {
            status = STATUS_UNFOLDING;
            energyStorage.consume(FE_PER_TICK);
            unemittedFe += FE_PER_TICK;
            progress++;
            if (progress % 40 == 0) {
                server.playSound(null, worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.8f,
                        0.6f + 1.2f * progress / UNFOLD_TICKS);
            }
            if (progress >= UNFOLD_TICKS) {
                finish(server, player);
                return;
            }
            setChanged();
        }
        // The renderer runs the progress on by itself while unfolding; it only needs telling when that stops.
        if (status != previous || server.getGameTime() % 20 == 0) sync();
    }

    /** The proton folds back up with {@code player}'s double inside. */
    private void finish(ServerLevel server, ServerPlayer player) {
        inventory.extractItem(TESSERACT_SLOT, TESSERACTS, false);
        inventory.extractItem(FRAGMENT_SLOT, FRAGMENTS, false);
        inventory.extractItem(RESIDUE_SLOT, RESIDUE, false);
        SophonRegistry registry = SophonRegistry.get(server.getServer());
        UUID id = registry.create(player.getUUID());
        inventory.setStackInSlot(OUTPUT_SLOT, SophonItem.of(id, player.getUUID(), player.getGameProfile().name()));
        subject = null;
        progress = 0;
        status = STATUS_IDLE;
        sophons = registry.count(player.getUUID());

        server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 0.5f);
        server.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 1.4f);
        server.sendParticles(ParticleTypes.END_ROD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2,
                worldPosition.getZ() + 0.5, 60, 0.3, 0.6, 0.3, 0.12);
        QuantimiumAdvancements.award(player, "unfolded", "made_sophon");
        if (sophons >= 4) QuantimiumAdvancements.award(player, "fourfold", "four_doubles");
        sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, UnfoldingArrayBlockEntity array) {
        if (array.status == STATUS_UNFOLDING && array.progress < UNFOLD_TICKS) array.progress++;
    }

    // ---- menu, readouts, saving ----

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (player instanceof ServerPlayer server) sophons = SophonRegistry.get(server.level().getServer()).count(player.getUUID());
        if (subject == null && player instanceof ServerPlayer) status = blocker(player) == STATUS_READY ? STATUS_IDLE : blocker(player);
        return new UnfoldingArrayMenu(containerId, playerInventory, this, data);
    }

    @Override
    public MutableComponent fluxMeterLine() {
        return Component.translatable(subject != null ? "item.quantimium.flux_meter.array.unfolding"
                        : "item.quantimium.flux_meter.array.idle", progress * 100 / UNFOLD_TICKS)
                .withStyle(ChatFormatting.DARK_AQUA);
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
        tag.put("Inventory", inventory.serializeNBT(registries));
        writeShared(tag);
    }

    private void writeShared(CompoundTag tag) {
        if (subject != null) tag.store("Subject", net.minecraft.core.UUIDUtil.CODEC, subject);
        tag.putInt("Progress", progress);
        tag.putInt("Status", status);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Energy")) energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompoundOrEmpty("Inventory"));
        subject = com.kadikular.quantimium.util.NbtCompat.hasUUID(tag, "Subject") ? com.kadikular.quantimium.util.NbtCompat.getUUID(tag, "Subject") : null;
        progress = tag.getIntOr("Progress", 0);
        status = tag.getIntOr("Status", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeShared(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        for (int slot = 0; slot < getInventory().getSlots(); slot++) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), getInventory().getStackInSlot(slot));
        }
    }
}
