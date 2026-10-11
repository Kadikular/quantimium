package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.recipe.FilterEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Collector: light passes through it, out of its face, as through a mirror; and what a Pickup lens
 * further along brings back up the beam stops here, in its nine slots, as its filter allows, for pipes
 * and hoppers to take, or for its faces to push out. A Sender up the beam can send to it too. Nothing
 * can be put in from outside.
 */
public class CollectorBlockEntity extends ItemRelayBlockEntity {

    public static final int FILTER_SLOTS = 9;

    /** Ghost entries, never real items: what it catches, or with a blacklist, what it lets go on up the beam. */
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            CollectorBlockEntity.this.setChanged();
        }
    };
    /** Whitelist (catch only these) or blacklist (catch all but these). An empty filter catches everything. */
    private boolean allow = true;

    public CollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COLLECTOR_BE.get(), pos, state);
    }

    @Override
    protected SideMode defaultMode() {
        return SideMode.OUTPUT;
    }

    @Override
    protected Mode startingMode() {
        return Mode.COLLECT;
    }

    @Override
    protected boolean acceptsItem(ItemStack stack) {
        return accepts(stack);
    }

    /** Whether its filter lets it catch {@code stack}. */
    public boolean accepts(ItemStack stack) {
        boolean listed = false;
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(i);
            if (entry.isEmpty()) continue;
            any = true;
            if (FilterEntry.matches(entry, stack)) listed = true;
        }
        return !any || listed == allow;
    }

    public SimpleContainer getFilter() {
        return filter;
    }

    public boolean allows() {
        return allow;
    }

    public void toggleMode() {
        allow = !allow;
        setChanged();
    }

    public void setFilterSlot(int slot, ItemStack stack) {
        if (slot >= 0 && slot < FILTER_SLOTS) filter.setItem(slot, FilterEntry.of(stack));
    }

    public void cycleFilterTag(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        ItemStack entry = filter.getItem(slot);
        if (!entry.isEmpty()) filter.setItem(slot, FilterEntry.cycle(entry));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.collector");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.kadikular.quantimium.menu.CollectorMenu(containerId, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        List<ItemStack> entries = new ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) entries.add(filter.getItem(i));
        out.store("Filter", ItemStack.OPTIONAL_CODEC.listOf(), entries);
        out.putBoolean("Allow", allow);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        List<ItemStack> entries = in.read("Filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < FILTER_SLOTS; i++) filter.getItems().set(i, i < entries.size() ? entries.get(i) : ItemStack.EMPTY);
        allow = in.getBooleanOr("Allow", true);
    }

    /** How full it is. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        int stacks = 0;
        for (int slot = 0; slot < SLOTS; slot++) if (!items.getStackInSlot(slot).isEmpty()) stacks++;
        return Component.translatable("message.quantimium.collector.holds", stacks, SLOTS).withStyle(ChatFormatting.DARK_AQUA);
    }
}
