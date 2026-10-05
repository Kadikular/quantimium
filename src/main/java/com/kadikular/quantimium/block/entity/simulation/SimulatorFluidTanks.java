package com.kadikular.quantimium.block.entity.simulation;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.function.Supplier;

/**
 * Six tanks on the quantum simulator: three inputs (0–2) and three outputs (3–5).
 *
 * <p>Inserts into the input group follow Modern Industrialization's rule: a fluid only ever occupies
 * one tank. Fill prefers a tank already holding that fluid, otherwise the first empty tank. Outputs
 * never accept external fills through the sided capability — they are harvest targets only.
 */
public final class SimulatorFluidTanks {

    public static final int INPUT_TANKS = 3;
    public static final int OUTPUT_TANKS = 3;
    public static final int TANK_COUNT = INPUT_TANKS + OUTPUT_TANKS;
    public static final int CAPACITY = 32_000;
    public static final int ALL_INPUTS = 0b111;
    public static final int ALL_OUTPUTS = 0b111;

    private final FluidStack[] tanks = new FluidStack[TANK_COUNT];
    private final Runnable onChanged;

    public SimulatorFluidTanks(Runnable onChanged) {
        this.onChanged = onChanged;
        for (int i = 0; i < TANK_COUNT; i++) tanks[i] = FluidStack.EMPTY;
    }

    public int getTanks() {
        return TANK_COUNT;
    }

    /** A copy of every tank, for rolling back a transaction that touched them. */
    public FluidStack[] snapshot() {
        FluidStack[] copy = new FluidStack[TANK_COUNT];
        for (int i = 0; i < TANK_COUNT; i++) copy[i] = tanks[i].copy();
        return copy;
    }

    /** Puts a {@link #snapshot} back without telling anyone: nothing happened. */
    public void restore(FluidStack[] snapshot) {
        for (int i = 0; i < TANK_COUNT && i < snapshot.length; i++) tanks[i] = snapshot[i];
    }

    public FluidStack getFluid(int tank) {
        return isTank(tank) ? tanks[tank] : FluidStack.EMPTY;
    }

    public int getCapacity(int tank) {
        return isTank(tank) ? CAPACITY : 0;
    }

    public boolean isInput(int tank) {
        return tank >= 0 && tank < INPUT_TANKS;
    }

    public boolean isOutput(int tank) {
        return tank >= INPUT_TANKS && tank < TANK_COUNT;
    }

    public static int outputIndex(int local) {
        return INPUT_TANKS + local;
    }

    public void setFluid(int tank, FluidStack stack) {
        if (!isTank(tank)) return;
        tanks[tank] = stack.isEmpty() ? FluidStack.EMPTY : stack.copy();
        onChanged.run();
    }

    /** Internal fill that ignores sided masks; used by simulation harvest and bucket helpers. */
    public int fillInternal(int tank, FluidStack resource, IFluidHandler.FluidAction action) {
        if (!isTank(tank) || resource.isEmpty()) return 0;
        FluidStack existing = tanks[tank];
        if (!existing.isEmpty() && !FluidStack.isSameFluidSameComponents(existing, resource)) return 0;

        int space = CAPACITY - existing.getAmount();
        int filled = Math.min(space, resource.getAmount());
        if (filled <= 0) return 0;
        if (action.execute()) {
            if (existing.isEmpty()) {
                tanks[tank] = resource.copyWithAmount(filled);
            } else {
                existing.grow(filled);
            }
            onChanged.run();
        }
        return filled;
    }

    /** Internal drain that ignores sided masks. */
    public FluidStack drainInternal(int tank, int maxDrain, IFluidHandler.FluidAction action) {
        if (!isTank(tank) || maxDrain <= 0) return FluidStack.EMPTY;
        FluidStack existing = tanks[tank];
        if (existing.isEmpty()) return FluidStack.EMPTY;
        int drained = Math.min(maxDrain, existing.getAmount());
        FluidStack result = existing.copyWithAmount(drained);
        if (action.execute()) {
            existing.shrink(drained);
            if (existing.isEmpty()) tanks[tank] = FluidStack.EMPTY;
            onChanged.run();
        }
        return result;
    }

    public FluidStack drainInternal(int tank, FluidStack resource, IFluidHandler.FluidAction action) {
        if (resource.isEmpty() || !isTank(tank)) return FluidStack.EMPTY;
        FluidStack existing = tanks[tank];
        if (existing.isEmpty() || !FluidStack.isSameFluidSameComponents(existing, resource)) {
            return FluidStack.EMPTY;
        }
        return drainInternal(tank, resource.getAmount(), action);
    }

    /**
     * MI-style insert across a range of tanks: same fluid stacks into its existing tank, otherwise
     * the first empty tank in range. Never spreads one fluid across multiple tanks.
     */
    public int fillUnique(int fromInclusive, int toExclusive, int mask, FluidStack resource,
                          IFluidHandler.FluidAction action) {
        if (resource.isEmpty()) return 0;

        int match = -1;
        int empty = -1;
        for (int tank = fromInclusive; tank < toExclusive; tank++) {
            int bit = tank - fromInclusive;
            if ((mask & (1 << bit)) == 0) continue;
            FluidStack existing = tanks[tank];
            if (existing.isEmpty()) {
                if (empty < 0) empty = tank;
            } else if (FluidStack.isSameFluidSameComponents(existing, resource)) {
                match = tank;
                break;
            }
        }

        int target = match >= 0 ? match : empty;
        if (target < 0) return 0;
        return fillInternal(target, resource, action);
    }

    public FluidStack drainFromRange(int fromInclusive, int toExclusive, int mask, FluidStack resource,
                                     IFluidHandler.FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        for (int tank = fromInclusive; tank < toExclusive; tank++) {
            int bit = tank - fromInclusive;
            if ((mask & (1 << bit)) == 0) continue;
            FluidStack drained = drainInternal(tank, resource, action);
            if (!drained.isEmpty()) return drained;
        }
        return FluidStack.EMPTY;
    }

    public FluidStack drainFromRange(int fromInclusive, int toExclusive, int mask, int maxDrain,
                                     IFluidHandler.FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        for (int tank = fromInclusive; tank < toExclusive; tank++) {
            int bit = tank - fromInclusive;
            if ((mask & (1 << bit)) == 0) continue;
            FluidStack drained = drainInternal(tank, maxDrain, action);
            if (!drained.isEmpty()) return drained;
        }
        return FluidStack.EMPTY;
    }

    public int insertIntoOutputs(FluidStack resource, IFluidHandler.FluidAction action) {
        return fillUnique(INPUT_TANKS, TANK_COUNT, ALL_OUTPUTS, resource, action);
    }

    public int insertIntoInputs(FluidStack resource, IFluidHandler.FluidAction action) {
        return fillUnique(0, INPUT_TANKS, ALL_INPUTS, resource, action);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int tank = 0; tank < TANK_COUNT; tank++) {
            CompoundTag entry = new CompoundTag();
            entry.putByte("Tank", (byte) tank);
            if (!tanks[tank].isEmpty()) {
                entry.put("Fluid", NbtCompat.saveWith(FluidStack.CODEC, registries, tanks[tank]));
            }
            list.add(entry);
        }
        tag.put("Tanks", list);
        return tag;
    }

    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        for (int i = 0; i < TANK_COUNT; i++) tanks[i] = FluidStack.EMPTY;
        if (!tag.contains("Tanks")) return;
        ListTag list = tag.getListOrEmpty("Tanks");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            int tank = entry.getByteOr("Tank", (byte) 0);
            if (!isTank(tank)) continue;
            tanks[tank] = entry.contains("Fluid")
                    ? NbtCompat.parseWith(FluidStack.CODEC, registries, entry.getCompoundOrEmpty("Fluid")).orElse(FluidStack.EMPTY)
                    : FluidStack.EMPTY;
        }
    }

    public IFluidHandler sidedView(Supplier<SideConfig> configSupplier) {
        return new SidedView(configSupplier);
    }

    private static boolean isTank(int tank) {
        return tank >= 0 && tank < TANK_COUNT;
    }

    private final class SidedView implements IFluidHandler {
        private final Supplier<SideConfig> configSupplier;

        private SidedView(Supplier<SideConfig> configSupplier) {
            this.configSupplier = configSupplier;
        }

        private SideConfig config() {
            return configSupplier.get();
        }

        @Override
        public int getTanks() {
            return TANK_COUNT;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return getFluid(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return getCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (stack.isEmpty() || !isTank(tank)) return false;
            SideConfig config = config();
            if (isInput(tank)) return config.allowsFluidInputTank(tank);
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            SideConfig config = config();
            if (!config.fluidMode().allowsInput() || resource.isEmpty()) return 0;
            return fillUnique(0, INPUT_TANKS, config.fluidInputMask(), resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            SideConfig config = config();
            if (!config.fluidMode().allowsOutput() || resource.isEmpty()) return FluidStack.EMPTY;
            return drainFromRange(INPUT_TANKS, TANK_COUNT, config.fluidOutputMask(), resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            SideConfig config = config();
            if (!config.fluidMode().allowsOutput() || maxDrain <= 0) return FluidStack.EMPTY;
            return drainFromRange(INPUT_TANKS, TANK_COUNT, config.fluidOutputMask(), maxDrain, action);
        }
    }

    /** Unrestricted handler used when a null face capability is requested. */
    public IFluidHandler unsidedView() {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return TANK_COUNT;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return getFluid(tank);
            }

            @Override
            public int getTankCapacity(int tank) {
                return getCapacity(tank);
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return isInput(tank);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return fillUnique(0, INPUT_TANKS, ALL_INPUTS, resource, action);
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return drainFromRange(INPUT_TANKS, TANK_COUNT, ALL_OUTPUTS, resource, action);
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return drainFromRange(INPUT_TANKS, TANK_COUNT, ALL_OUTPUTS, maxDrain, action);
            }
        };
    }
}
