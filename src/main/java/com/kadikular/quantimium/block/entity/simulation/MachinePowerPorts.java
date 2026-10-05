// Path: src/main/java/com/kadikular/quantimium/block/entity/simulation/MachinePowerPorts.java
package com.kadikular.quantimium.block.entity.simulation;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every way we know of to push power into a contained machine.
 *
 * <p>The simulator has no cables, so the machine's buffer is topped up directly through whichever
 * energy interface it publishes. Three are tried, because a machine that cannot be charged simply
 * never runs: many tech machines refuse to even look for a recipe until a token amount of power can
 * be drawn.
 *
 * <ol>
 *   <li>NeoForge {@code IEnergyStorage} (Forge Energy), the common case.</li>
 *   <li>Mekanism {@code IStrictEnergyHandler} (Joules), when FE conversion is off.</li>
 *   <li>Long-precision energy capabilities, resolved by name so no mod has to be on the classpath.
 *       This covers GrandPower and Modern Industrialization's EU, which is what a cable would use.</li>
 *   <li>Modern Industrialization's {@code EnergyComponentHolder}, used when the machine's own
 *       storage refuses external insertion (an unconfigured face, for instance).</li>
 * </ol>
 */
public final class MachinePowerPorts {

    /** Named block capabilities that expose long-precision energy, tried when Forge Energy fails. */
    private static final String[][] LONG_ENERGY_CAPS = {
            {"dev.technici4n.grandpower.api.ILongEnergyStorage", "BLOCK"},
            {"aztech.modern_industrialization.api.energy.EnergyApi", "SIDED"},
    };

    private static final String MI_ENERGY_HOLDER = "aztech.modern_industrialization.api.machine.holder.EnergyComponentHolder";
    private static final String MI_ENERGY_LIST_HOLDER = "aztech.modern_industrialization.api.machine.holder.EnergyListComponentHolder";
    private static final String MI_SIMULATION = "aztech.modern_industrialization.util.Simulation";

    /**
     * Optional-mod lookups resolved once. A missing class costs a classloader miss and a thrown
     * {@code ClassNotFoundException} every time it is attempted, which is far too expensive for a
     * path the Anomalite Crystal hazard walks on a per-block basis.
     */
    private static final Object MI_SIMULATION_ACT = enumConstant(MI_SIMULATION, "ACT");
    private static final Class<?> MI_ENERGY_HOLDER_TYPE = type(MI_ENERGY_HOLDER);
    private static final Class<?> MI_ENERGY_LIST_HOLDER_TYPE = type(MI_ENERGY_LIST_HOLDER);
    private static final BlockCapability<?, Direction> MEKANISM_STRICT_ENERGY = mekanismStrictEnergyCapability();
    private static final Class<?> MEKANISM_HANDLER_TYPE = type("mekanism.api.energy.IStrictEnergyHandler");
    private static final Object MEKANISM_EXECUTE = enumConstant("mekanism.api.Action", "EXECUTE");
    private static final List<LongEnergyCap> LONG_ENERGY_PORTS = resolveLongEnergyCaps();

    /**
     * A resolved long-precision energy capability. The FE ratio stays lazy because Modern
     * Industrialization's server config is not readable this early.
     */
    private record LongEnergyCap(BlockCapability<?, Direction> capability, boolean modernIndustrialization) {
        long fePerUnit() {
            return modernIndustrialization ? miFePerEu() : 1;
        }
    }

    private static final MachinePowerPorts NONE = new MachinePowerPorts(List.of());

    /** Shared empty instance for callers that bill a buffer directly and never need discovery. */
    public static MachinePowerPorts none() {
        return NONE;
    }

    /** Reflective ports throw, so failures are declared here and swallowed per port by the caller. */
    private interface Port {
        /** Fills the buffer and returns how much went in. */
        long charge() throws Exception;

        long stored() throws Exception;

        long drain(long amount) throws Exception;

        long fePerUnit();

        default boolean canDrain() {
            return true;
        }
    }

    private final List<Port> ports;
    private final long[] originalStored;
    private long[] sampleStored;

    private MachinePowerPorts(List<Port> ports) {
        this.ports = ports;
        this.originalStored = snapshotUnits();
        this.sampleStored = new long[ports.size()];
    }

    public static MachinePowerPorts discover(Level level, BlockPos pos, BlockState state, BlockEntity be) {
        List<Port> ports = new ArrayList<>();
        Map<Object, Boolean> seen = new IdentityHashMap<>();

        collectMachineComponents(ports, seen, be);
        if (ports.isEmpty()) collectMekanismEnergy(ports, seen, level, pos, state, be);
        if (ports.isEmpty()) collectForgeEnergy(ports, seen, level, pos, state, be);
        if (ports.isEmpty()) collectLongEnergy(ports, seen, level, pos, state, be);
        return new MachinePowerPorts(ports);
    }

    public boolean isEmpty() {
        return ports.isEmpty();
    }

    /** Tops every buffer up. Returns how much was accepted in total. */
    public long charge() {
        long inserted = 0;
        for (Port port : ports) {
            try {
                inserted += port.charge();
            } catch (Throwable ignored) {
                // A read-only or broken buffer just stays where it is.
            }
        }
        return inserted;
    }

    /** Whether any buffer actually holds a charge, which is how we spot a machine we cannot power. */
    public boolean hasCharge() {
        for (Port port : ports) {
            try {
                if (port.stored() > 0) return true;
            } catch (Throwable ignored) {
                // Unreadable buffers are ignored rather than reported as empty.
            }
        }
        return false;
    }

    /** Total charge represented as FE, with saturation for long-precision buffers. */
    public long totalStoredFe() {
        long total = 0;
        for (Port port : ports) {
            try {
                total = saturatingAdd(total, saturatingMultiply(port.stored(), port.fePerUnit()));
            } catch (Throwable ignored) {
                // Broken optional-mod ports do not invalidate the other buffers.
            }
        }
        return total;
    }

    public boolean canExtract() {
        for (Port port : ports) {
            try {
                if (port.canDrain() && port.stored() > 0) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * Extracts up to roughly {@code maxFe} from the discovered buffers and returns the FE removed.
     * A buffer counted in coarser units than FE (EU, Joules) rounds the request up to one whole
     * unit, overshooting by less than a unit: rounding down instead would make every machine whose
     * buffer is smaller than one unit's worth of the request immune.
     */
    public long extractFe(long maxFe) {
        if (maxFe <= 0) return 0;
        long removedFe = 0;
        for (Port port : ports) {
            if (removedFe >= maxFe) break;
            try {
                long ratio = Math.max(1, port.fePerUnit());
                long units = ceilDiv(maxFe - removedFe, ratio);
                if (units <= 0) continue;
                long removedUnits = Math.max(0, port.drain(units));
                removedFe = saturatingAdd(removedFe, saturatingMultiply(removedUnits, ratio));
            } catch (Throwable ignored) {
                // Read-only buffers are normal; try the next independent port.
            }
        }
        return removedFe;
    }

    /** Starts an exact consumption sample after the virtual buffers have been charged. */
    public void beginSample() {
        this.sampleStored = snapshotUnits();
    }

    /** Positive buffer decrease since {@link #beginSample()}, converted to FE with saturation. */
    public long finishSampleFe() {
        long total = 0;
        for (int i = 0; i < ports.size(); i++) {
            try {
                long used = Math.max(0, sampleStored[i] - ports.get(i).stored());
                total = saturatingAdd(total, saturatingMultiply(used, ports.get(i).fePerUnit()));
            } catch (Throwable ignored) {
            }
        }
        return total;
    }

    /** Removes power injected only for simulation before the real machine is restored or serialized. */
    public void restoreOriginalEnergy() {
        for (int i = 0; i < ports.size(); i++) {
            try {
                long surplus = Math.max(0, ports.get(i).stored() - originalStored[i]);
                if (surplus > 0) ports.get(i).drain(surplus);
            } catch (Throwable ignored) {
            }
        }
    }

    private long[] snapshotUnits() {
        long[] values = new long[ports.size()];
        for (int i = 0; i < ports.size(); i++) {
            try {
                values[i] = ports.get(i).stored();
            } catch (Throwable ignored) {
            }
        }
        return values;
    }

    // --- Discovery ----------------------------------------------------------------------------

    private static void collectMekanismEnergy(List<Port> ports, Map<Object, Boolean> seen, Level level,
                                             BlockPos pos, BlockState state, BlockEntity be) {
        if (MEKANISM_HANDLER_TYPE == null || MEKANISM_EXECUTE == null) return;
        int before = ports.size();
        addMekanismHandler(ports, seen, be);
        if (ports.size() > before) return;
        BlockCapability<?, Direction> capability = MEKANISM_STRICT_ENERGY;
        if (capability == null) return;
        addMekanismHandler(ports, seen, resolve(level, pos, state, be, capability, null));
        for (Direction face : Direction.values()) {
            addMekanismHandler(ports, seen, resolve(level, pos, state, be, capability, face));
        }
    }

    private static void addMekanismHandler(List<Port> ports, Map<Object, Boolean> seen, Object handler) {
        if (handler == null) return;
        Port port = StrictEnergyPort.of(handler);
        if (port == null) return;
        if (seen.putIfAbsent(handler, Boolean.TRUE) != null) return;
        ports.add(port);
    }

    @SuppressWarnings("unchecked")
    private static BlockCapability<?, Direction> mekanismStrictEnergyCapability() {
        try {
            Class<?> caps = Class.forName("mekanism.common.capabilities.Capabilities");
            Object multi = caps.getField("STRICT_ENERGY").get(null);
            Method block = multi.getClass().getMethod("block");
            Object value = block.invoke(multi);
            return value instanceof BlockCapability<?, ?> capability
                    ? (BlockCapability<?, Direction>) capability
                    : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void collectForgeEnergy(List<Port> ports, Map<Object, Boolean> seen, Level level,
                                          BlockPos pos, BlockState state, BlockEntity be) {
        add(ports, seen, resolve(level, pos, state, be, Capabilities.Energy.BLOCK, null));
        for (Direction face : Direction.values()) {
            add(ports, seen, resolve(level, pos, state, be, Capabilities.Energy.BLOCK, face));
        }
    }

    private static void collectLongEnergy(List<Port> ports, Map<Object, Boolean> seen, Level level,
                                          BlockPos pos, BlockState state, BlockEntity be) {
        for (LongEnergyCap cap : LONG_ENERGY_PORTS) {
            BlockCapability<?, Direction> capability = cap.capability();
            long ratio = cap.fePerUnit();
            addLong(ports, seen, resolve(level, pos, state, be, capability, null), ratio);
            for (Direction face : Direction.values()) {
                addLong(ports, seen, resolve(level, pos, state, be, capability, face), ratio);
            }
        }
    }

    private static List<LongEnergyCap> resolveLongEnergyCaps() {
        List<LongEnergyCap> resolved = new ArrayList<>(LONG_ENERGY_CAPS.length);
        for (String[] coordinates : LONG_ENERGY_CAPS) {
            BlockCapability<?, Direction> capability = lookupCapability(coordinates[0], coordinates[1]);
            if (capability == null) continue;
            resolved.add(new LongEnergyCap(capability, coordinates[0].contains("modern_industrialization")));
        }
        return List.copyOf(resolved);
    }

    /**
     * Machines whose external ports are face-configured can still be charged through their own
     * energy component, which is where a cable's power would have ended up anyway.
     */
    private static void collectMachineComponents(List<Port> ports, Map<Object, Boolean> seen, BlockEntity be) {
        Object simulationAct = MI_SIMULATION_ACT;
        if (simulationAct == null) return;

        for (Object component : machineEnergyComponents(be)) {
            if (component == null || seen.putIfAbsent(component, Boolean.TRUE) != null) continue;
            Port port = MachineComponentPort.of(component, simulationAct);
            if (port != null) ports.add(port);
        }
    }

    private static List<Object> machineEnergyComponents(BlockEntity be) {
        if (!isInstance(MI_ENERGY_HOLDER_TYPE, be) && !isInstance(MI_ENERGY_LIST_HOLDER_TYPE, be)) {
            return List.of();
        }
        List<Object> components = new ArrayList<>(2);
        Object single = invokeInterfaceMethod(MI_ENERGY_HOLDER_TYPE, be, "getEnergyComponent");
        if (single != null) components.add(single);

        Object list = invokeInterfaceMethod(MI_ENERGY_LIST_HOLDER_TYPE, be, "getEnergyComponents");
        if (list instanceof List<?> many) components.addAll(many);
        return components;
    }

    private static boolean isInstance(@Nullable Class<?> type, Object target) {
        return type != null && type.isInstance(target);
    }

    private static void add(List<Port> ports, Map<Object, Boolean> seen, Object storage) {
        if (storage instanceof EnergyHandler handler) {
            if (seen.putIfAbsent(handler, Boolean.TRUE) != null) return;
            ports.add(new ForgeEnergyPort(IEnergyStorage.of(handler)));
            return;
        }
        if (!(storage instanceof IEnergyStorage energy)) return;
        if (seen.putIfAbsent(storage, Boolean.TRUE) != null) return;
        ports.add(LongCapabilityPort.of(storage, energy, 1));
    }

    private static void addLong(List<Port> ports, Map<Object, Boolean> seen, Object storage, long ratio) {
        if (!(storage instanceof IEnergyStorage energy)) return;
        if (seen.putIfAbsent(storage, Boolean.TRUE) != null) return;
        ports.add(LongCapabilityPort.of(storage, energy, ratio));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object resolve(Level level, BlockPos pos, BlockState state, BlockEntity be,
                                 BlockCapability<?, Direction> capability, Direction face) {
        try {
            return level.getCapability((BlockCapability) capability, pos, state, be, face);
        } catch (Throwable t) {
            return null;
        }
    }

    // --- Ports --------------------------------------------------------------------------------

    /**
     * A capability port. Long-precision storages are driven through their own {@code receive} so an
     * EU buffer larger than an {@code int} still fills, and fall back to Forge Energy otherwise.
     */
    private record LongCapabilityPort(IEnergyStorage energy, Object storage, Method receive, Method extract,
                                      Method amount, Method capacity, long fePerUnit) implements Port {

        static Port of(Object storage, IEnergyStorage energy, long ratio) {
            Method receive = method(storage.getClass(), "receive", long.class, boolean.class);
            Method extract = method(storage.getClass(), "extract", long.class, boolean.class);
            Method amount = method(storage.getClass(), "getAmount");
            Method capacity = method(storage.getClass(), "getCapacity");
            if (receive == null || amount == null || capacity == null) {
                return new ForgeEnergyPort(energy);
            }
            return new LongCapabilityPort(energy, storage, receive, extract, amount, capacity, ratio);
        }

        @Override
        public long charge() throws Exception {
            long room = (long) capacity.invoke(storage) - (long) amount.invoke(storage);
            if (room <= 0) return 0;
            return (long) receive.invoke(storage, room, false);
        }

        @Override
        public long stored() throws Exception {
            return (long) amount.invoke(storage);
        }

        @Override
        public long drain(long amount) throws Exception {
            if (extract != null) return (long) extract.invoke(storage, amount, false);
            return energy.extractEnergy((int) Math.min(Integer.MAX_VALUE, amount), false);
        }

        @Override
        public boolean canDrain() {
            return extract != null || energy.canExtract();
        }
    }

    private record StrictEnergyPort(Object handler, Method insert, Method extract, Method energy,
                                    Method count, Object execute) implements Port {

        static Port of(Object handler) {
            try {
                Class<?> type = MEKANISM_HANDLER_TYPE;
                if (type == null || !type.isInstance(handler)) return null;
                Object execute = MEKANISM_EXECUTE;
                if (execute == null) return null;
                Method insert = type.getMethod("insertEnergy", long.class, execute.getClass());
                Method extract = type.getMethod("extractEnergy", long.class, execute.getClass());
                Method energy = type.getMethod("getEnergy", int.class);
                Method count = type.getMethod("getEnergyContainerCount");
                if (((Number) count.invoke(handler)).intValue() <= 0) return null;
                return new StrictEnergyPort(handler, insert, extract, energy, count, execute);
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public long charge() throws Exception {
            long room = Long.MAX_VALUE / 4;
            long remainder = (long) insert.invoke(handler, room, execute);
            return Math.max(0, room - remainder);
        }

        @Override
        public long stored() throws Exception {
            int containers = ((Number) count.invoke(handler)).intValue();
            long total = 0;
            for (int i = 0; i < containers; i++) {
                total = saturatingAdd(total, (long) energy.invoke(handler, i));
            }
            return total;
        }

        @Override
        public long drain(long amount) throws Exception {
            return (long) extract.invoke(handler, amount, execute);
        }

        @Override
        public long fePerUnit() {
            return 1;
        }
    }

    private record ForgeEnergyPort(IEnergyStorage energy) implements Port {
        @Override
        public long charge() {
            return energy.receiveEnergy(Integer.MAX_VALUE, false);
        }

        @Override
        public long stored() {
            return energy.getEnergyStored();
        }

        @Override
        public long drain(long amount) {
            return energy.extractEnergy((int) Math.min(Integer.MAX_VALUE, amount), false);
        }

        @Override
        public long fePerUnit() { return 1; }

        @Override
        public boolean canDrain() { return energy.canExtract(); }
    }

    /** Modern Industrialization's energy component, filled through its own {@code insertEu}. */
    private record MachineComponentPort(Object component, Method insert, Method consume, Method readEu, Method readRoom,
                                        Object simulationAct, long fePerUnit) implements Port {

        static Port of(Object component, Object simulationAct) {
            Method insert = null;
            for (Method candidate : component.getClass().getMethods()) {
                if (candidate.getName().equals("insertEu") && candidate.getParameterCount() == 2
                        && candidate.getParameterTypes()[0] == long.class) {
                    insert = candidate;
                    break;
                }
            }
            Method readEu = method(component.getClass(), "getEu");
            Method readRoom = method(component.getClass(), "getRemainingCapacity");
            Method consume = method(component.getClass(), "consumeEu", long.class, simulationAct.getClass());
            if (insert == null || readEu == null || readRoom == null) return null;
            return new MachineComponentPort(component, insert, consume, readEu, readRoom, simulationAct, miFePerEu());
        }

        @Override
        public long charge() throws Exception {
            long space = (long) readRoom.invoke(component);
            if (space <= 0) return 0;
            return (long) insert.invoke(component, space, simulationAct);
        }

        @Override
        public long stored() throws Exception {
            return (long) readEu.invoke(component);
        }

        @Override
        public long drain(long amount) throws Exception {
            return consume == null ? 0 : (long) consume.invoke(component, amount, simulationAct);
        }

        @Override
        public boolean canDrain() {
            return consume != null;
        }
    }

    // --- Reflection helpers -------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static BlockCapability<?, Direction> lookupCapability(String className, String fieldName) {
        try {
            Field field = Class.forName(className).getField(fieldName);
            Object value = field.get(null);
            return value instanceof BlockCapability<?, ?> capability
                    ? (BlockCapability<?, Direction>) capability
                    : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object enumConstant(String className, String name) {
        try {
            for (Object constant : Class.forName(className).getEnumConstants()) {
                if (constant instanceof Enum<?> value && value.name().equals(name)) return constant;
            }
        } catch (Throwable ignored) {
            // Mod absent.
        }
        return null;
    }

    private static Object invokeInterfaceMethod(@Nullable Class<?> type, Object target, String methodName) {
        try {
            if (type == null || !type.isInstance(target)) return null;
            return type.getMethod(methodName).invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static @Nullable Class<?> type(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            Method method = owner.getMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (Throwable t) {
            return null;
        }
    }

    private static long miFePerEuCache;

    /** Read once the first time a Modern Industrialization buffer is actually seen. */
    private static long miFePerEu() {
        if (miFePerEuCache > 0) return miFePerEuCache;
        return miFePerEuCache = readMiFePerEu();
    }

    private static long readMiFePerEu() {
        try {
            Class<?> configClass = Class.forName("aztech.modern_industrialization.config.MIServerConfig");
            Object instance = configClass.getField("INSTANCE").get(null);
            Object value = configClass.getField("forgeEnergyPerEu").get(instance);
            Method getter = value.getClass().getMethod("getAsInt");
            return Math.max(1, ((Number) getter.invoke(value)).longValue());
        } catch (Throwable ignored) {
            return 10;
        }
    }

    private static long ceilDiv(long value, long divisor) {
        if (value <= 0) return 0;
        return (value + divisor - 1) / divisor;
    }

    private static long saturatingMultiply(long a, long b) {
        if (a <= 0 || b <= 0) return 0;
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE;
        return a * b;
    }

    private static long saturatingAdd(long a, long b) {
        if (Long.MAX_VALUE - a < b) return Long.MAX_VALUE;
        return a + b;
    }
}
