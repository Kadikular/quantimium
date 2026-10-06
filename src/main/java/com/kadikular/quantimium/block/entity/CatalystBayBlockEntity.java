package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.menu.CatalystBayMenu;
import com.kadikular.quantimium.recipe.FilterEntry;
import com.kadikular.quantimium.recipe.RecipeFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/**
 * Up to {@link #SLOTS} catalysts in a window of void, one in each quarter, and a filter on what they
 * may make and use up, as the ME Superposition Crafter has. The Horizon Core reads them each time it
 * looks over its reactor, so a change takes effect within a second.
 */
public class CatalystBayBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOTS = 4;
    /** Entries in each of the filter's two lists; the filter container holds outputs first, then inputs. */
    public static final int FILTER_SLOTS = 27;
    public static final int INPUT_FILTER_START = FILTER_SLOTS;

    private final SimpleContainer catalysts = new SimpleContainer(SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            changed();
        }
    };

    /** Ghost stacks, never real items: outputs to allow or deny, then inputs to allow or deny. */
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS * 2) {
        @Override
        public void setChanged() {
            super.setChanged();
            changed();
        }
    };

    private boolean outputsAllow = true;
    /** The input list starts as a blacklist: "never use these" is what it's usually for. */
    private boolean inputsAllow = false;

    public CatalystBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CATALYST_BAY_BE.get(), pos, state);
    }

    /** The centre of a slot's quarter, in block-local x and z. */
    public static double slotX(int slot) {
        return (slot & 1) == 0 ? 0.34 : 0.66;
    }

    public static double slotZ(int slot) {
        return (slot & 2) == 0 ? 0.34 : 0.66;
    }

    public SimpleContainer getCatalystContainer() {
        return catalysts;
    }

    public SimpleContainer getFilter() {
        return filter;
    }

    public ItemStack getCatalyst(int slot) {
        return catalysts.getItem(slot);
    }

    /** Every slot, empty ones too, so a catalyst keeps its index while the others change. */
    public List<ItemStack> getCatalysts() {
        List<ItemStack> all = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) all.add(catalysts.getItem(i));
        return all;
    }

    public void setCatalyst(int slot, ItemStack stack) {
        catalysts.setItem(slot, stack.copyWithCount(Math.min(1, stack.getCount())));
    }

    public boolean outputsAllow() {
        return outputsAllow;
    }

    public boolean inputsAllow() {
        return inputsAllow;
    }

    public void toggleOutputMode() {
        outputsAllow = !outputsAllow;
        changed();
    }

    public void toggleInputMode() {
        inputsAllow = !inputsAllow;
        changed();
    }

    /** Sets entry {@code slot} (outputs 0-26, inputs 27-53) to plain {@code stack}, or clears it. */
    public void setFilterSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < filter.getContainerSize()) filter.setItem(slot, FilterEntry.of(stack));
    }

    /** Shift-click: steps the entry through its item's tags and back. */
    public void cycleFilterTag(int slot) {
        if (slot < 0 || slot >= filter.getContainerSize()) return;
        ItemStack entry = filter.getItem(slot);
        if (!entry.isEmpty()) filter.setItem(slot, FilterEntry.cycle(entry));
    }

    /** What the bay's catalysts may make and use up. */
    public RecipeFilter recipeFilter() {
        return RecipeFilter.of(filter, FILTER_SLOTS, outputsAllow, inputsAllow);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) Containers.dropContents(level, pos, catalysts);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Catalysts", ItemStack.OPTIONAL_CODEC.listOf(), getCatalysts());
        List<ItemStack> entries = new ArrayList<>();
        for (int i = 0; i < filter.getContainerSize(); i++) entries.add(filter.getItem(i));
        // Slot by slot, empties too, so entries never slide from one list into the other.
        out.store("Filter", ItemStack.OPTIONAL_CODEC.listOf(), entries);
        out.putBoolean("OutputsAllow", outputsAllow);
        out.putBoolean("InputsAllow", inputsAllow);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        List<ItemStack> read = new ArrayList<>(in.read("Catalysts", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        // A raised bay, from before bays sank into the floor, held one.
        if (read.isEmpty()) in.read("Catalyst", ItemStack.OPTIONAL_CODEC).ifPresent(read::add);
        for (int i = 0; i < SLOTS; i++) catalysts.getItems().set(i, i < read.size() ? read.get(i) : ItemStack.EMPTY);
        List<ItemStack> entries = in.read("Filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < filter.getContainerSize(); i++) {
            filter.getItems().set(i, i < entries.size() ? entries.get(i) : ItemStack.EMPTY);
        }
        outputsAllow = in.getBooleanOr("OutputsAllow", true);
        inputsAllow = in.getBooleanOr("InputsAllow", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.catalyst_bay");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CatalystBayMenu(containerId, playerInventory, this);
    }
}
