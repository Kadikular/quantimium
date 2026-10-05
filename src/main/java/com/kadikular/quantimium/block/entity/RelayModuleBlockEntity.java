package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModItems;
import com.kadikular.quantimium.menu.RelayModuleMenu;
import com.kadikular.quantimium.recipe.EntangledLinks;
import com.kadikular.quantimium.superposition.Relay;
import com.kadikular.quantimium.superposition.SophonRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Relay module's hold: six Tesseracts, each bound to one of its owner's pods, and a Sophon slot. The
 * pod it sits in is a hub: from it, the bound pods can be reached whether or not a double waits there
 * (see {@link Relay}), and a double in one can be recalled into the Sophon slot, or a Sophon in the slot
 * sent out to an empty one.
 */
public class RelayModuleBlockEntity extends BlockEntity implements MenuProvider {

    public static final int LINKS = 6;
    public static final int SOPHON_SLOT = LINKS;

    /** What a Tesseract slot leads to, for the screen. */
    public static final int LINK_NONE = 0;
    public static final int LINK_EMPTY_POD = 1;
    public static final int LINK_DOUBLE = 2;
    public static final int LINK_NOT_A_POD = 3;

    public static final int DATA_COUNT = LINKS;

    private final ItemStackHandler inventory = new ItemStackHandler(LINKS + 1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SOPHON_SLOT) return stack.is(ModItems.SOPHON.get());
            return EntangledLinks.fitsStabilizer(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    /** Filled in for whoever has the screen open, so each slot can say where it leads. */
    private final int[] states = new int[LINKS];

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index < LINKS ? states[index] : 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public RelayModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY_MODULE_BE.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /**
     * The pod a Tesseract slot leads to, if it is bound to one: either half of a Superposition Pod
     * counts, and is taken as its lower half, where the pod keeps its state.
     */
    @Nullable
    public GlobalPos link(int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        GlobalPos bound = EntangledLinks.boundPos(stack);
        if (bound == null) return null;
        Identifier block = stack.get(ModDataComponents.BOUND_BLOCK.get());
        if (block == null || !block.equals(ModBlocks.SUPERPOSITION_POD.getId())) return null;
        return bound;
    }

    /** Works out what each slot leads to, for {@code player}'s screen. Called while it is open. */
    public void refresh(ServerLevel level, UUID player) {
        SophonRegistry registry = SophonRegistry.get(level.getServer());
        for (int slot = 0; slot < LINKS; slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                states[slot] = LINK_NONE;
                continue;
            }
            GlobalPos pod = link(slot);
            if (pod == null) {
                states[slot] = LINK_NOT_A_POD;
            } else {
                states[slot] = Relay.doubleAt(registry, player, pod) != null ? LINK_DOUBLE : LINK_EMPTY_POD;
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (level instanceof ServerLevel server) refresh(server, player.getUUID());
        return new RelayModuleMenu(containerId, playerInventory, this, data);
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
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompoundOrEmpty("Inventory"));
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
