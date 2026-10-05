package com.kadikular.quantimium.block.entity;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.kadikular.quantimium.util.LegacyItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.entity.item.ItemEntity;
import com.kadikular.quantimium.block.QuantumFoundryStructure;
import com.kadikular.quantimium.block.multiblock.MultiblockHost;
import com.kadikular.quantimium.block.multiblock.PartClaim;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModRecipeTypes;
import com.kadikular.quantimium.menu.QuantumFoundryMenu;
import com.kadikular.quantimium.recipe.foundry.FoundryIngredient;
import com.kadikular.quantimium.recipe.foundry.FoundryRecipeInput;
import com.kadikular.quantimium.recipe.foundry.QuantumFoundryRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.crafting.RecipeManager;
import java.util.Collection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;

public class QuantumFoundryBlockEntity extends BlockEntity
        implements MenuProvider, MultiblockHost, QuantumEnergyHost, FluxMeterReadout {
    public static final int INPUT_SLOTS = 4;
    public static final int OUTPUT_SLOT = 4;
    public static final int TOTAL_SLOTS = 5;
    public static final int ENERGY_CAPACITY = 2_000_000;
    public static final int MAX_RECEIVE = 10_000;
    private static final int PROGRESS_SYNC_TICKS = 5;

    public static final int STATUS_UNFORMED = 0;
    public static final int STATUS_IDLE = 1;
    public static final int STATUS_NO_RECIPE = 2;
    public static final int STATUS_NO_POWER = 3;
    public static final int STATUS_LOW_FLUX = 4;
    public static final int STATUS_OUTPUT_FULL = 5;
    public static final int STATUS_WORKING = 6;

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            if (completingRecipe) return;
            if (slot < INPUT_SLOTS) resetIfRecipeChanged();
            setChangedAndSync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < INPUT_SLOTS && hasPillar(slot);
        }
    };

    private final QuantumEnergyStorage energyStorage =
            new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
                @Override
                protected void onReceived() {
                    setChanged();
                }
            };

    private final IItemHandler automation = new FoundryAutomationHandler();
    private int pillarMask;
    private int progress;
    private int duration;
    private int statusCode = STATUS_UNFORMED;
    private int syncedFluxBand;
    private int syncedAnomalyBand;
    /** The band the Foundry works in, read with hysteresis. Not saved. */
    private FluxBand gateBand = FluxBand.LOW;
    private int syncedRequiredBand;
    private int syncedFluxCost;
    private int currentPowerUse;
    private int averagePowerUse;
    private int syncedPassiveDrain;
    private final int[] powerSamples = new int[20];
    private int powerSampleIndex;
    @Nullable
    private Identifier activeRecipeId;
    private ItemStack renderResult = ItemStack.EMPTY;
    private boolean completingRecipe;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energyStorage.getEnergyStored() & 0xFFFF;
                case 1 -> energyStorage.getEnergyStored() >>> 16;
                case 2 -> energyStorage.getMaxEnergyStored() & 0xFFFF;
                case 3 -> energyStorage.getMaxEnergyStored() >>> 16;
                case 4 -> statusCode;
                case 5 -> progress;
                case 6 -> duration;
                case 7 -> pillarMask;
                case 8 -> syncedFluxBand;
                case 9 -> syncedAnomalyBand;
                case 10 -> syncedRequiredBand;
                case 11 -> currentPowerUse;
                case 12 -> averagePowerUse;
                case 13 -> syncedFluxCost;
                case 14 -> syncedPassiveDrain;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 15;
        }
    };

    public QuantumFoundryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_FOUNDRY_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, QuantumFoundryBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (level.getGameTime() % 20L == 0L) be.revalidateStructure();

        // Its own chunk holds the flux it spends; the band it works in is the field at the Foundry,
        // with hysteresis, so a field hovering at an edge does not switch recipes on and off.
        QuantumFlux.Neighbourhood field = QuantumFlux.chunk(level, pos);
        QuantumFlux.Neighbourhood here = QuantumFlux.sample(level, pos);
        be.gateBand = FluxBand.of(here.flux(), be.gateBand);
        FluxBand anomalyBand = here.anomalyBand();
        boolean fieldBandChanged = be.syncedFluxBand != be.gateBand.ordinal()
                || be.syncedAnomalyBand != anomalyBand.ordinal();
        be.syncedFluxBand = be.gateBand.ordinal();
        be.syncedAnomalyBand = anomalyBand.ordinal();
        if (fieldBandChanged) be.setChangedAndSync();

        int passive = AnomalyEffects.passiveDrainFePerTick(level, pos);
        be.syncedPassiveDrain = be.energyStorage.consume(passive);

        if (!be.isFormed()) {
            be.stop(STATUS_UNFORMED);
            be.recordPowerUse(be.syncedPassiveDrain);
            return;
        }

        RecipeHolder<QuantumFoundryRecipe> holder = be.findIngredientRecipe(field.flux());
        if (holder == null) {
            be.stop(be.inputsEmpty() ? STATUS_IDLE : STATUS_NO_RECIPE);
            be.recordPowerUse(be.syncedPassiveDrain);
            return;
        }

        QuantumFoundryRecipe recipe = holder.value();
        be.syncedRequiredBand = recipe.minimumFluxBand().ordinal();
        be.syncedFluxCost = (int) Math.ceil(recipe.fluxCost());
        be.duration = recipe.duration();
        be.renderResult = recipe.result().copy();

        if (be.gateBand.ordinal() < recipe.minimumFluxBand().ordinal()
                || field.flux() + 1.0E-9 < recipe.fluxCost()) {
            be.statusCode = STATUS_LOW_FLUX;
            be.recordPowerUse(be.syncedPassiveDrain);
            return;
        }
        if (!be.outputFits(recipe.result())) {
            be.statusCode = STATUS_OUTPUT_FULL;
            be.recordPowerUse(be.syncedPassiveDrain);
            return;
        }

        int perTick = (int) Math.ceil(recipe.fePerTick() * AnomalyEffects.feMultiplier(level, pos));
        if (be.energyStorage.getEnergyStored() < perTick) {
            be.statusCode = STATUS_NO_POWER;
            be.recordPowerUse(be.syncedPassiveDrain);
            return;
        }

        if (be.activeRecipeId != null && !be.activeRecipeId.equals(holder.id())) be.progress = 0;
        be.activeRecipeId = holder.id().identifier();
        int spent = be.energyStorage.consume(perTick);
        be.progress++;
        be.statusCode = STATUS_WORKING;

        if (be.progress >= recipe.duration()) {
            be.complete(serverLevel, recipe, field.flux());
            be.setChangedAndSync();
        } else if (be.progress % PROGRESS_SYNC_TICKS == 0) {
            // The renderer only needs progress accurately enough to grow the product, so a full
            // block-entity packet every tick would be a lot of traffic for no visible gain.
            be.setChangedAndSync();
        } else {
            be.setChanged();
        }
        be.recordPowerUse(spent + be.syncedPassiveDrain);
    }

    private void complete(ServerLevel level, QuantumFoundryRecipe recipe, double flux) {
        FoundryRecipeInput input = recipeInput(flux);
        List<Integer> matched = recipe.matchedSlots(input);
        if (matched.size() != recipe.ingredients().size() || !outputFits(recipe.result())) {
            stop(STATUS_NO_RECIPE);
            return;
        }
        if (!QuantumFlux.tryConsumeChunkFlux(level, worldPosition, recipe.fluxCost(), recipe.anomaly())) {
            statusCode = STATUS_LOW_FLUX;
            return;
        }
        completingRecipe = true;
        try {
            for (int i = 0; i < matched.size(); i++) {
                FoundryIngredient ingredient = recipe.ingredients().get(i);
                inventory.extractItem(matched.get(i), ingredient.count(), false);
            }
            ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
            inventory.setStackInSlot(OUTPUT_SLOT, output.isEmpty()
                    ? recipe.result().copy()
                    : output.copyWithCount(output.getCount() + recipe.result().getCount()));
        } finally {
            completingRecipe = false;
        }
        progress = 0;
        activeRecipeId = null;
    }

    private static Collection<RecipeHolder<QuantumFoundryRecipe>> foundryRecipes(Level level) {
        return level.recipeAccess() instanceof RecipeManager manager
                ? manager.recipeMap().byType(ModRecipeTypes.FOUNDRY_TYPE.get())
                : List.of();
    }

    @Nullable
    private RecipeHolder<QuantumFoundryRecipe> findIngredientRecipe(double flux) {
        if (level == null) return null;
        FoundryRecipeInput input = recipeInput(flux);
        for (RecipeHolder<QuantumFoundryRecipe> holder :
                foundryRecipes(level)) {
            QuantumFoundryRecipe recipe = holder.value();
            if (Integer.bitCount(pillarMask) < recipe.minimumPillars()) continue;
            if (recipe.matchedSlots(input).size() == recipe.ingredients().size()) return holder;
        }
        return null;
    }

    private FoundryRecipeInput recipeInput(double flux) {
        List<ItemStack> stacks = new ArrayList<>(INPUT_SLOTS);
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            stacks.add(hasPillar(slot) ? inventory.getStackInSlot(slot) : ItemStack.EMPTY);
        }
        return new FoundryRecipeInput(stacks, Integer.bitCount(pillarMask), flux);
    }

    private boolean outputFits(ItemStack result) {
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private boolean inputsEmpty() {
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private void resetIfRecipeChanged() {
        progress = 0;
        activeRecipeId = null;
    }

    private void stop(int status) {
        boolean changed = statusCode != status || progress != 0 || duration != 0
                || activeRecipeId != null || !renderResult.isEmpty()
                || syncedRequiredBand != 0 || syncedFluxCost != 0;
        statusCode = status;
        progress = 0;
        duration = 0;
        activeRecipeId = null;
        syncedRequiredBand = 0;
        syncedFluxCost = 0;
        renderResult = ItemStack.EMPTY;
        if (changed) setChangedAndSync();
    }

    private void recordPowerUse(int amount) {
        currentPowerUse = Math.max(0, amount);
        powerSamples[powerSampleIndex++ % powerSamples.length] = currentPowerUse;
        long total = 0;
        for (int sample : powerSamples) total += sample;
        averagePowerUse = (int) (total / powerSamples.length);
    }

    @Override
    public BlockPos controllerPos() {
        return worldPosition;
    }

    @Override
    public boolean coversPart(BlockPos part) {
        return level != null && QuantumFoundryStructure.coversPart(level, worldPosition, part);
    }

    @Override
    public void revalidateStructure() {
        // Part states are written from here, so this has to stay server-authoritative: setPlacedBy
        // runs on both sides.
        if (level == null || level.isClientSide()) return;
        PartClaim claim = PartClaim.of(level, worldPosition);
        int next = QuantumFoundryStructure.pillarMask(level, worldPosition, claim);
        // Runs even when the mask is unchanged: the part states are the only place the formed look
        // and the conduit axes live, so a rail edited around the controller still gets corrected.
        QuantumFoundryStructure.applyPartStates(level, worldPosition, next, claim);
        if (next == pillarMask) return;
        pillarMask = next;
        progress = 0;
        activeRecipeId = null;
        invalidateCapabilities();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getBlockEntity(worldPosition.offset(dx, 0, dz))
                        instanceof QuantumFoundryPartBlockEntity part) {
                    part.invalidateCapabilities();
                }
            }
        }
        setChangedAndSync();
    }

    /**
     * Forgets the structure and finds it again, as after an unfold: the arms are the same blocks in
     * new places, and an unchanged mask would skip the work of re-forming.
     */
    public void reform() {
        pillarMask = 0;
        revalidateStructure();
    }

    @Override
    public boolean isFormed() {
        return pillarMask != 0;
    }

    @Override
    public QuantumEnergyStorage energyPort() {
        return isFormed() ? energyStorage : null;
    }

    @Override
    public ResourceHandler<ItemResource> itemPort() {
        return isFormed() ? LegacyItems.of(automation, inventory) : null;
    }

    public boolean hasPillar(int slot) {
        return slot >= 0 && slot < INPUT_SLOTS && (pillarMask & 1 << slot) != 0;
    }

    public int getPillarMask() {
        return pillarMask;
    }

    public int getProgress() {
        return progress;
    }

    public int getDuration() {
        return duration;
    }

    /** Client-safe: progress only advances while a cycle is actually being paid for. */
    public boolean isWorking() {
        return progress > 0;
    }

    public int getAveragePowerUse() {
        return averagePowerUse;
    }

    public ItemStack getRenderResult() {
        return renderResult;
    }

    public int getFluxBandOrdinal() {
        return syncedFluxBand;
    }

    public int getAnomalyBandOrdinal() {
        return syncedAnomalyBand;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public ResourceHandler<ItemResource> getAutomationItemHandler() {
        return LegacyItems.of(automation, inventory);
    }

    public ContainerData getContainerData() {
        return data;
    }

    public List<ItemStack> removeAllItems() {
        List<ItemStack> removed = new ArrayList<>();
        for (int slot = 0; slot < TOTAL_SLOTS; slot++) {
            removed.add(inventory.extractItem(slot, Integer.MAX_VALUE, false));
        }
        return removed;
    }

    public boolean isUsableBy(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    private void setChangedAndSync() {
        setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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
        tag.putInt("PillarMask", pillarMask);
        tag.putInt("Progress", progress);
        tag.putInt("Duration", duration);
        tag.putInt("FluxBand", syncedFluxBand);
        tag.putInt("AnomalyBand", syncedAnomalyBand);
        if (!renderResult.isEmpty()) tag.put("RenderResult", NbtCompat.saveStack(registries, renderResult));
        if (activeRecipeId != null) tag.putString("ActiveRecipe", activeRecipeId.toString());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompoundOrEmpty("Inventory"));
        pillarMask = tag.getIntOr("PillarMask", 0);
        progress = tag.getIntOr("Progress", 0);
        duration = tag.getIntOr("Duration", 0);
        syncedFluxBand = tag.getIntOr("FluxBand", 0);
        syncedAnomalyBand = tag.getIntOr("AnomalyBand", 0);
        renderResult = tag.contains("RenderResult")
                ? NbtCompat.parseStack(registries, tag.getCompoundOrEmpty("RenderResult")) : ItemStack.EMPTY;
        activeRecipeId = tag.contains("ActiveRecipe")
                ? Identifier.tryParse(tag.getStringOr("ActiveRecipe", "")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveLegacy(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        handleUpdateLegacy(NbtCompat.read(input), NbtCompat.lookup(input));
    }

    private void handleUpdateLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        loadLegacy(tag, registries);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        CompoundTag tag = NbtCompat.read(input);
        if (!tag.isEmpty()) handleUpdateLegacy(tag, NbtCompat.lookup(input));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.quantum_foundry_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new QuantumFoundryMenu(containerId, playerInventory, this, data);
    }

    private final class FoundryAutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return TOTAL_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return slot < INPUT_SLOTS && hasPillar(slot)
                    ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == OUTPUT_SLOT ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < INPUT_SLOTS && hasPillar(slot) && inventory.isItemValid(slot, stack);
        }
    }

    @Override
    public MutableComponent fluxMeterLine() {
        return FluxMeterReadout.emitter(getAveragePowerUse());
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        QuantumFoundryStructure.applyPartStates(level, pos, 0);
        for (ItemStack stack : removeAllItems()) {
            if (!stack.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.0,
                        pos.getZ() + 0.5, stack));
            }
        }
    }
}
