package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.block.ObservationChamberBlock;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.menu.ObservationChamberMenu;
import com.kadikular.quantimium.unrealised.Form;
import com.kadikular.quantimium.unrealised.MatterHistory;
import com.kadikular.quantimium.unrealised.MatterSteps;
import com.kadikular.quantimium.unrealised.PackOres;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Collapses Unrealised Matter into real ores on a rising redstone edge. Trace byproduct is optional:
 * with {@link #voidExcessTrace} on (the default) Trace that does not fit is destroyed; with it off the
 * chamber waits for room instead.
 */
public class ObservationChamberBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOT = 0;
    public static final int TRACE_SLOT = 1;
    public static final int OUTPUT_START = 2;
    public static final int OUTPUT_END = 10;
    public static final int TOTAL_SLOTS = 11;

    public static final int STATUS_EMPTY = 0;
    public static final int STATUS_READY = 1;
    public static final int STATUS_OUTPUT_FULL = 2;
    public static final int STATUS_IDLE = 3;

    /** Chance per collapsed Matter that a Quantimium Trace is also produced. */
    public static final float TRACE_CHANCE = 0.50f;
    /** Matter collapsed per redstone pulse: a whole stack per click made observation free. */
    public static final int MEASUREMENT_BATCH = 4;

    /** Datapack-owned weighted output list; packs can replace or extend this loot table. */
    private static final ResourceKey<LootTable> COLLAPSE_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath("quantimium", "gameplay/unrealised_matter"));

    /** Exposed so recipe viewers can read the same table the chamber rolls against. */
    public static ResourceKey<LootTable> collapseLootKey() {
        return COLLAPSE_LOOT;
    }

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            refreshStatus();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == INPUT_SLOT) return com.kadikular.quantimium.unrealised.Matter.is(stack);
            if (slot == TRACE_SLOT) return stack.is(ModItems.QUANTIMIUM_TRACE.get());
            // Outputs accept anything from measure(); the menu refuses player placement.
            return slot >= OUTPUT_START && slot <= OUTPUT_END;
        }
    };

    private boolean wasPowered;
    private boolean voidExcessTrace = true;
    private int statusCode = STATUS_EMPTY;
    private int litTicks;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> statusCode;
                case 1 -> voidExcessTrace ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 1) {
                voidExcessTrace = value != 0;
                setChanged();
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public ObservationChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OBSERVATION_CHAMBER_BE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ContainerData getContainerData() {
        return data;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isVoidExcessTrace() {
        return voidExcessTrace;
    }

    public void setVoidExcessTrace(boolean voidExcessTrace) {
        this.voidExcessTrace = voidExcessTrace;
        setChanged();
    }

    public void toggleVoidExcessTrace() {
        setVoidExcessTrace(!voidExcessTrace);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ObservationChamberBlockEntity be) {
        if (level.isClientSide()) return;

        boolean powered = level.hasNeighborSignal(pos);
        if (powered && !be.wasPowered) {
            be.measure();
        }
        be.wasPowered = powered;

        if (be.litTicks > 0) {
            be.litTicks--;
            if (be.litTicks == 0 && state.getValue(ObservationChamberBlock.LIT)) {
                level.setBlock(pos, state.setValue(ObservationChamberBlock.LIT, false), Block.UPDATE_CLIENTS);
            }
        }

        be.refreshStatus();
    }

    /** One redstone measurement: collapse as much of the input stack as the outputs can take. */
    private void measure() {
        if (level == null) return;
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty() || !com.kadikular.quantimium.unrealised.Matter.is(input)) {
            refreshStatus();
            return;
        }

        RandomSource random = level.getRandom();
        int remaining = Math.min(input.getCount(), MEASUREMENT_BATCH);
        int consumed = 0;

        while (remaining > 0) {
            // Keeping excess Trace and nowhere to put it: wait, rather than lose any.
            if (!voidExcessTrace && !traceHasRoom(inventory, TRACE_SLOT)) {
                statusCode = STATUS_OUTPUT_FULL;
                break;
            }
            Collapse collapse = rollCollapse(MatterHistory.of(input));
            List<ItemStack> products = collapse.products();
            if (!fitsOutputs(products)) {
                statusCode = STATUS_OUTPUT_FULL;
                break;
            }
            for (ItemStack product : products) insertIntoOutputs(product, false);
            if (collapse.broke()) offerTrace();
            remaining--;
            consumed++;

            if (random.nextFloat() < TRACE_CHANCE) {
                offerTrace();
            }
        }

        if (consumed > 0) {
            inventory.extractItem(INPUT_SLOT, consumed, false);
            litTicks = 12;
            BlockState state = getBlockState();
            if (!state.getValue(ObservationChamberBlock.LIT)) {
                level.setBlock(worldPosition, state.setValue(ObservationChamberBlock.LIT, true), Block.UPDATE_CLIENTS);
            }
            setChanged();
        }
        refreshStatus();
    }

    /** A Trace into its slot; a full slot voids it, and with voiding off the loop above waits first. */
    private void offerTrace() {
        inventory.insertItem(TRACE_SLOT, new ItemStack(ModItems.QUANTIMIUM_TRACE.get()), false);
    }

    /** Whether one more Trace fits in {@code slot}. */
    public static boolean traceHasRoom(ItemStackHandler inventory, int slot) {
        return inventory.insertItem(slot, new ItemStack(ModItems.QUANTIMIUM_TRACE.get()), true).isEmpty();
    }

    private Collapse rollCollapse(MatterHistory history) {
        return level instanceof ServerLevel serverLevel ? rollCollapse(serverLevel, worldPosition, history)
                : new Collapse(List.of(new ItemStack(Items.COAL)), false);
    }

    /** Whether all of {@code products} fit the outputs together. */
    private boolean fitsOutputs(List<ItemStack> products) {
        ItemStackHandler trial = new ItemStackHandler(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) trial.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        for (ItemStack product : products) {
            ItemStack remaining = product.copy();
            for (int slot = OUTPUT_START; slot <= OUTPUT_END && !remaining.isEmpty(); slot++) {
                remaining = trial.insertItem(slot, remaining, false);
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    /**
     * One collapse of one Matter, as any chamber rolls it: an ore of the pack's, picked by rarity for
     * the flux band where the chamber stands and dropping what it would mined (see {@link PackOres}),
     * or one roll of the hand-made loot table where the pack says so.
     */
    public static List<ItemStack> rollCollapse(ServerLevel level, BlockPos pos) {
        return rollCollapse(level, pos, MatterHistory.NONE).products();
    }

    /** One collapse: what it made, and whether a step of its history broke, which leaves a Trace. */
    public record Collapse(List<ItemStack> products, boolean broke) {}

    /**
     * One collapse of one Matter with {@code history} (game plan E4, observe): an ore picked for the
     * band (above Low, paid for in flux from the chunk; see {@link Config#chamberFluxPerBand}), then
     * each step of the history in turn, each holding with a chance that rises with the band
     * ({@link Config#stepHoldChance}). It collapses into one of the forms the steps that held could
     * have made (see {@link MatterSteps}), picked at random; a step that doesn't hold leaves a Trace.
     */
    public static Collapse rollCollapse(ServerLevel level, BlockPos pos, MatterHistory history) {
        if (PackOres.inUse()) {
            FluxBand band = FluxBand.of(QuantumFlux.sample(level, pos).flux());
            // The better odds of a hot field are paid for in flux from the chamber's chunk.
            double price = (double) Config.chamberFluxPerBand() * band.ordinal();
            if (price > 0.0 && !QuantumFlux.tryConsumeChunkFlux(level, pos, price, 0.0)) band = FluxBand.LOW;
            PackOres.Ore ore = PackOres.roll(level.getRandom(), band);
            if (ore != null) {
                List<ItemStack> drops = PackOres.drops(level, ore, pos);
                if (!drops.isEmpty()) {
                    if (history.isEmpty()) return new Collapse(drops, false);
                    // Walk the history as far as it holds, then collapse into one of the forms reached.
                    double hold = Config.stepHoldChance(band);
                    int held = 0;
                    while (held < history.steps().size() && level.getRandom().nextDouble() < hold) held++;
                    MatterHistory reached = new MatterHistory(history.steps().subList(0, held));
                    List<Form> forms = MatterSteps.formsOfDrops(level, pos, reached, drops);
                    Form form = forms.get(level.getRandom().nextInt(forms.size()));
                    // A real roll's forms are whole: this ore's drops, times whole recipe outputs.
                    return new Collapse(List.of(form.item().copyWithCount((int) Math.round(form.amount()))),
                            held < history.steps().size());
                }
            }
        }
        return new Collapse(List.of(rollLootTable(level, pos)), false);
    }

    /** One roll of the hand-made collapse loot table. */
    private static ItemStack rollLootTable(ServerLevel level, BlockPos pos) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(COLLAPSE_LOOT);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params).stream()
                .filter(stack -> !stack.isEmpty())
                .findFirst()
                .orElseGet(() -> new ItemStack(Items.COAL));
    }

    private int insertIntoOutputs(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return 0;
        ItemStack remaining = stack.copy();
        for (int slot = OUTPUT_START; slot <= OUTPUT_END && !remaining.isEmpty(); slot++) {
            remaining = inventory.insertItem(slot, remaining, simulate);
        }
        return stack.getCount() - remaining.getCount();
    }

    private void refreshStatus() {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            statusCode = STATUS_EMPTY;
            return;
        }
        // Room check uses coal as a stand-in; real collapse rolls happen on measure.
        if (insertIntoOutputs(new ItemStack(Items.COAL), true) < 1) {
            statusCode = STATUS_OUTPUT_FULL;
        } else {
            statusCode = STATUS_READY;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.observation_chamber");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ObservationChamberMenu(containerId, playerInventory, this, data);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putBoolean("VoidExcessTrace", voidExcessTrace);
        tag.putBoolean("WasPowered", wasPowered);
        tag.putInt("StatusCode", statusCode);
        tag.putInt("LitTicks", litTicks);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompoundOrEmpty("Inventory"));
        }
        voidExcessTrace = !tag.contains("VoidExcessTrace") || tag.getBooleanOr("VoidExcessTrace", false);
        wasPowered = tag.getBooleanOr("WasPowered", false);
        statusCode = tag.getIntOr("StatusCode", 0);
        litTicks = tag.getIntOr("LitTicks", 0);
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
