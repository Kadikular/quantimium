package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.LogisticsMember;
import com.kadikular.quantimium.util.LegacyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * The Reactor Logistics Port: a Reactor port that joins the Reactor to the logistics network whose controller's light
 * reaches it, as storage. What the network puts in goes into the horizon, as through an Input port; what it takes
 * comes from everything the Reactor holds or could make, as through a Materialiser port, so a terminal shows all of
 * it. Light ending on it powers the Reactor.
 */
public class ReactorLogisticsPortBlockEntity extends ReactorPortBlockEntity implements LaserNode, LogisticsMember {

    /** The horizon in, what the Reactor can give out: one inventory to the network. */
    private final IItemHandler both = new IItemHandler() {
        private IItemHandler in() {
            return LegacyItems.legacy(inputHandler());
        }

        private IItemHandler out() {
            return LegacyItems.legacy(materialiserHandler());
        }

        /** At least one, so there's somewhere to put things in while nothing is held. */
        @Override
        public int getSlots() {
            return core() == null ? 0 : Math.max(1, out().getSlots());
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler out = out();
            return slot < out.getSlots() ? out.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        /** Everything goes in at once, whichever slot it's offered to. */
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return ItemHandlerHelper.insertItem(in(), stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler out = out();
            return slot < out.getSlots() ? out.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    };

    public ReactorLogisticsPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REACTOR_LOGISTICS_PORT_BE.get(), pos, state);
    }

    /** The Reactor's power, for light ending on it. */
    @Nullable
    public EnergyHandler beamEnergy() {
        HorizonCoreBlockEntity horizon = core();
        return horizon == null ? null : horizon.getEnergyStorage();
    }

    // ---- logistics ----

    @Override
    public LogisticsMember logistics() {
        return this;
    }

    @Override
    public Role role() {
        return Role.STORAGE;
    }

    @Override
    public boolean active() {
        return false;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean lets(ItemStack stack) {
        return true;
    }

    @Override
    @Nullable
    public IItemHandler inventory() {
        return core() == null ? null : both;
    }

    @Override
    public BlockPos inventoryPos() {
        return worldPosition;
    }

    // ---- the network: light ends here ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return Set.of();
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    /** It passes nothing on; a relay only so the light reaching it is known. */
    @Override
    public boolean relays() {
        return true;
    }

    @Override
    public double tax() {
        return 0;
    }

    @Override
    public boolean chargedByLight() {
        return true;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {}

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) LaserNetworks.add(level, this);
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
}
