package com.kadikular.quantimium.util;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * The slot-indexed inventory the block entities were written against, on top of the current
 * transfer API. It is a {@link ItemStacksResourceHandler}, so it goes straight into the item
 * capability, and it keeps the old {@code ItemStackHandler} method names and the old save format
 * ({@code Size} plus {@code Items} of slot-tagged stacks) so existing worlds load unchanged.
 */
public class ItemStackHandler extends ItemStacksResourceHandler {
    public ItemStackHandler() {
        this(1);
    }

    public ItemStackHandler(int size) {
        super(size);
    }

    public ItemStackHandler(NonNullList<ItemStack> stacks) {
        super(stacks);
    }

    public int getSlots() {
        return size();
    }

    public ItemStack getStackInSlot(int slot) {
        return stacks.get(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        ItemStack previous = stacks.set(slot, stack);
        onContentsChanged(slot, previous);
    }

    public int getSlotLimit(int slot) {
        return 64;
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    protected void onContentsChanged(int slot) {}

    protected void onLoad() {}

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onContentsChanged(index);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return isItemValid(index, resource.toStack(1));
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        int limit = getSlotLimit(index);
        return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
    }

    /**
     * Puts {@code stack} into {@code slot}; returns what did not fit. Plain logic on the slots, with no transaction of its
     * own: it is called from inside other handlers' operations, which may already be in one.
     */
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;
        ItemStack existing = stacks.get(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(stack, existing)) return stack;
            limit -= existing.getCount();
        }
        if (limit <= 0) return stack;
        boolean reachedLimit = stack.getCount() > limit;
        if (!simulate) {
            ItemStack previous = existing;
            if (existing.isEmpty()) {
                stacks.set(slot, reachedLimit ? stack.copyWithCount(limit) : stack.copy());
            } else {
                stacks.set(slot, existing.copyWithCount(existing.getCount() + (reachedLimit ? limit : stack.getCount())));
            }
            onContentsChanged(slot, previous);
        }
        return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
    }

    /** Takes up to {@code amount} out of {@code slot}, without a transaction of its own. */
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack existing = stacks.get(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        int toExtract = Math.min(amount, Math.min(existing.getCount(), existing.getMaxStackSize()));
        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                stacks.set(slot, ItemStack.EMPTY);
                onContentsChanged(slot, existing);
            }
            return existing;
        }
        if (!simulate) {
            stacks.set(slot, existing.copyWithCount(existing.getCount() - toExtract));
            onContentsChanged(slot, existing);
        }
        return existing.copyWithCount(toExtract);
    }

    /** A copy of every slot, for rolling back a transaction that touched this inventory. */
    public NonNullList<ItemStack> snapshotStacks() {
        return copyToList().stream().map(ItemStack::copy).collect(NonNullList::create, NonNullList::add, NonNullList::addAll);
    }

    /** Puts a {@link #snapshotStacks} copy back without telling anyone: nothing happened. */
    public void restoreStacks(NonNullList<ItemStack> snapshot) {
        for (int slot = 0; slot < stacks.size() && slot < snapshot.size(); slot++) stacks.set(slot, snapshot.get(slot));
    }

    public void setSize(int size) {
        setStacks(NonNullList.withSize(size, ItemStack.EMPTY));
    }

    @Override
    public void serialize(ValueOutput output) {
        output.putInt("Size", stacks.size());
        ValueOutput.TypedOutputList<ItemStackWithSlot> items = output.list("Items", ItemStackWithSlot.CODEC);
        for (int slot = 0; slot < stacks.size(); slot++) {
            if (!stacks.get(slot).isEmpty()) items.add(new ItemStackWithSlot(slot, stacks.get(slot)));
        }
    }

    @Override
    public void deserialize(ValueInput input) {
        setStacks(NonNullList.withSize(input.getIntOr("Size", stacks.size()), ItemStack.EMPTY));
        for (ItemStackWithSlot entry : input.listOrEmpty("Items", ItemStackWithSlot.CODEC)) {
            if (entry.isValidInContainer(stacks.size())) stacks.set(entry.slot(), entry.stack());
        }
        onLoad();
    }

    /** The save format as a tag, for the block entities that still assemble their own {@link CompoundTag}. */
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        serialize(output);
        return output.buildResult();
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        deserialize(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }
}
