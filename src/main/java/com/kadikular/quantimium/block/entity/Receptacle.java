package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.laser.LogisticsMember;
import com.kadikular.quantimium.recipe.FilterEntry;
import com.kadikular.quantimium.util.LegacyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A Logistics Receptacle on a face of a Laser Focus block: while a Logistics Controller's light reaches the focus, the
 * inventory that face is against is on that controller's network, as storage, a provider or a requester (see
 * {@code Logistics}). Several on one focus each serve their own inventory, with their own settings.
 */
public final class Receptacle implements LogisticsMember {

    public static final int FILTER_SLOTS = 9;
    public static final int MAX_PRIORITY = 999;
    public static final int MAX_KEEP = 9_999;

    /** The block it's on; null only while a Logistics Receptacle block from before is read, to become one of these. */
    @Nullable
    private final LaserFocusBlockEntity host;
    private final Direction face;

    /** Ghost entries: what it stores, provides or requests. Empty, storage and providers take everything; a requester, nothing. */
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (host != null) host.setChanged();
        }
    };
    private boolean allow = true;
    /** A requester keeping counts stocked: each entry's count is how many it keeps in the inventory. */
    private boolean keep;
    private final int[] keepCounts = new int[FILTER_SLOTS];
    private Role role = Role.STORAGE;
    private boolean active;
    private int priority;
    private boolean connected;
    /** Whether its network's controller can pay for moves: it's lit only then. */
    private boolean networkPowered;
    /** How often it looks, when active: see {@link LogisticsMember#pace()}. Not saved: a reload starts it looking. */
    private final int[] pace = new int[2];
    /** Whether light reaching it powers the machine it's on, too. */
    private boolean powers;
    /** Whether the machine it's on takes power (worked out on the server, for the screen's tab). */
    private boolean canPower;

    /**
     * A provider on a machine that makes things to order: what that machine may make for the network from anything
     * on it (other machines' crafts too), only from what's stored, or nothing, offering only what's there.
     */
    public enum Crafts { STORED, NOTHING }

    private Crafts crafts = Crafts.STORED;
    /** An active requester: whether it has machines make what isn't stored, or takes only what's stored. */
    private boolean requestsMade = true;

    Receptacle(@Nullable LaserFocusBlockEntity host, Direction face) {
        this.host = host;
        this.face = face;
    }

    /** Takes on {@code other}'s settings: a Logistics Receptacle block's, as it becomes one of these. */
    void copyFrom(Receptacle other) {
        role = other.role;
        active = other.active;
        crafts = other.crafts;
        requestsMade = other.requestsMade;
        priority = other.priority;
        allow = other.allow;
        keep = other.keep;
        System.arraycopy(other.keepCounts, 0, keepCounts, 0, FILTER_SLOTS);
        powers = other.powers;
        for (int i = 0; i < FILTER_SLOTS; i++) filter.getItems().set(i, other.filter.getItem(i).copy());
        changed();
    }

    /** The face it's on: towards the inventory it serves. */
    public Direction facing() {
        return face;
    }

    public LaserFocusBlockEntity host() {
        return host;
    }

    @Nullable
    public Level getLevel() {
        return host.getLevel();
    }

    public BlockPos getBlockPos() {
        return host.getBlockPos();
    }

    /** Whether its block is still there and still holds it: for its screen. */
    public boolean present() {
        return !host.isRemoved() && host.receptacle(face) == this;
    }

    /** Whether it's lit: on a network whose controller can pay for moves. */
    public boolean lit() {
        return connected && networkPowered;
    }

    /** Its model's look: its role, and whether it's lit (packed for {@link LaserFocusBlockEntity#layout}). */
    int look() {
        return role.ordinal() * 2 + (connected && networkPowered ? 1 : 0);
    }

    private void changed() {
        if (host != null) host.receptacleChanged();
    }

    // ---- settings ----

    public void cycleRole() {
        role = role.next();
        changed();
    }

    public Crafts crafts() {
        return crafts;
    }

    @Override
    public boolean offersCraftable() {
        return crafts != Crafts.NOTHING;
    }

    /** Crafts are made from what's really stored: chains of crafts are a Reactor's to plan. */
    @Override
    public boolean craftsFromCraftable() {
        return false;
    }

    @Override
    public boolean requestsCraftable() {
        return requestsMade;
    }

    public void toggleRequestsMade() {
        requestsMade = !requestsMade;
        changed();
    }

    public void cycleCrafts() {
        crafts = crafts == Crafts.NOTHING ? Crafts.STORED : Crafts.NOTHING;
        changed();
    }

    @Override
    @Nullable
    public com.kadikular.quantimium.laser.OfferMaker maker() {
        Level level = getLevel();
        if (level == null) return null;
        return level.getBlockEntity(inventoryPos()) instanceof com.kadikular.quantimium.laser.OfferMaker maker ? maker : null;
    }

    /** On another network's Logistics Materialiser: that network's offers, as its mode lets them out. */
    @Override
    @Nullable
    public com.kadikular.quantimium.laser.OfferTable imports() {
        Level level = getLevel();
        if (level == null || !(level.getBlockEntity(inventoryPos()) instanceof LogisticsMaterialiserBlockEntity materialiser)) return null;
        com.kadikular.quantimium.laser.LogisticsController theirs = materialiser.controller();
        return theirs == null ? null : materialiser.offersFor(theirs);
    }

    public void toggleActive() {
        active = !active;
        changed();
    }

    public void toggleAllow() {
        allow = !allow;
        changed();
    }

    public void addPriority(int by) {
        priority = Math.clamp((long) priority + by, -MAX_PRIORITY, MAX_PRIORITY);
        changed();
    }

    public boolean allows() {
        return allow;
    }

    public boolean keeps() {
        return keep;
    }

    public void toggleKeep() {
        keep = !keep;
        changed();
    }

    /** How many of entry {@code slot} it keeps stocked. */
    public int keepCount(int slot) {
        return slot >= 0 && slot < FILTER_SLOTS ? Math.max(1, keepCounts[slot]) : 0;
    }

    public void addKeepCount(int slot, int by) {
        if (slot < 0 || slot >= FILTER_SLOTS || filter.getItem(slot).isEmpty()) return;
        keepCounts[slot] = Math.clamp((long) keepCount(slot) + by, 1, MAX_KEEP);
        changed();
    }

    public SimpleContainer getFilter() {
        return filter;
    }

    /** An entry, and as many as the stack held: what it keeps stocked when keeping counts. */
    public void setFilterSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        filter.setItem(slot, FilterEntry.of(stack));
        keepCounts[slot] = Math.max(1, stack.getCount());
        changed();
    }

    public void cycleFilterTag(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) return;
        ItemStack entry = filter.getItem(slot);
        if (!entry.isEmpty()) filter.setItem(slot, FilterEntry.cycle(entry));
    }

    public boolean isConnected() {
        return connected;
    }

    // ---- logistics ----

    @Override
    public Role role() {
        return role;
    }

    @Override
    public boolean active() {
        return active;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean lets(ItemStack stack) {
        boolean listed = false;
        boolean any = false;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(i);
            if (entry.isEmpty()) continue;
            any = true;
            if (FilterEntry.matches(entry, stack)) listed = true;
        }
        // Nothing listed: storage and providers take or give anything; a requester asks for nothing on a whitelist, everything on a blacklist.
        if (!any) return role != Role.REQUESTER || !allow;
        return listed == allow;
    }

    /** Keeping counts, a requester on an allow list takes only what it's short of each entry. */
    @Override
    public int wants(ItemStack stack) {
        if (!keep || role != Role.REQUESTER || !allow) return Integer.MAX_VALUE;
        IItemHandler inventory = inventory();
        int short_ = 0;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            ItemStack entry = filter.getItem(i);
            if (entry.isEmpty() || !FilterEntry.matches(entry, stack)) continue;
            int held = 0;
            if (inventory != null) {
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    ItemStack there = inventory.getStackInSlot(slot);
                    if (!there.isEmpty() && FilterEntry.matches(entry, there)) held += there.getCount();
                }
            }
            short_ += Math.max(0, keepCount(i) - held);
        }
        return short_;
    }

    @Override
    @Nullable
    public IItemHandler inventory() {
        Level level = getLevel();
        if (level == null) return null;
        BlockPos at = inventoryPos();
        // A Laser Port's slots, whole: its faces are set for pipes, which may only put in while it sends.
        if (level.getBlockEntity(at) instanceof LaserPortBlockEntity port) return port.slots();
        // A Materialiser: every form it may make, where pipes see only the one chosen for export.
        if (level.getBlockEntity(at) instanceof MaterialiserBlockEntity materialiser) return materialiser.networkHandler(face.getOpposite());
        return LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, at, face.getOpposite()));
    }

    @Override
    public BlockPos inventoryPos() {
        return getBlockPos().relative(face);
    }

    @Override
    public int[] pace() {
        return pace;
    }

    @Override
    public void connected(boolean connected) {
        if (this.connected == connected) return;
        this.connected = connected;
        changed();
    }

    @Override
    public void networkPowered(boolean powered) {
        if (networkPowered == powered) return;
        networkPowered = powered;
        changed();
    }

    // ---- powering the machine it's on ----

    public boolean powers() {
        return powers;
    }

    public boolean canPower() {
        return canPower;
    }

    public void togglePowers() {
        powers = !powers;
        changed();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) com.kadikular.quantimium.laser.LaserNetworks.changed(level);
    }

    /** Looks again whether the machine it's on takes power: when its screen opens, and when what's beside it changes. */
    public void checkPower() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) return;
        boolean can = level.getCapability(Capabilities.Energy.BLOCK, inventoryPos(), face.getOpposite()) != null;
        if (can == canPower) return;
        canPower = can;
        changed();
    }

    // ---- readouts ----

    /** Its role, whether active, and its priority: "Active provider, priority 5". */
    public MutableComponent describe() {
        String key = "message.quantimium.logistics_receptacle." + role.getSerializedName() + (role != Role.STORAGE && active ? ".active" : "");
        MutableComponent line = Component.translatable(key, priority);
        if (!connected) line.append(Component.translatable("message.quantimium.logistics_receptacle.unconnected"));
        return line;
    }

    // ---- saving ----

    void save(ValueOutput out) {
        out.putInt("Role", role.ordinal());
        out.putBoolean("Active", active);
        out.putInt("Crafts", crafts.ordinal());
        out.putBoolean("RequestsMade", requestsMade);
        out.putInt("Priority", priority);
        out.putBoolean("Allow", allow);
        out.putBoolean("Keep", keep);
        out.putIntArray("KeepCounts", keepCounts);
        out.putBoolean("Connected", connected);
        out.putBoolean("NetworkPowered", networkPowered);
        out.putBoolean("Powers", powers);
        out.putBoolean("CanPower", canPower);
        List<ItemStack> entries = new ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) entries.add(filter.getItem(i));
        out.store("Filter", ItemStack.OPTIONAL_CODEC.listOf(), entries);
    }

    /** Read as saved here, or as a Logistics Receptacle block saved it before it became one of these. */
    void load(ValueInput in) {
        role = Role.values()[Math.floorMod(in.getIntOr("Role", 0), Role.values().length)];
        active = in.getBooleanOr("Active", false);
        // Crafting from anything (chains) is gone: those crafts are made from what's stored.
        crafts = in.getIntOr("Crafts", in.getBooleanOr("OffersMade", true) ? 0 : 2) == 2 ? Crafts.NOTHING : Crafts.STORED;
        requestsMade = in.getBooleanOr("RequestsMade", true);
        priority = in.getIntOr("Priority", 0);
        allow = in.getBooleanOr("Allow", true);
        keep = in.getBooleanOr("Keep", false);
        int[] counts = in.getIntArray("KeepCounts").orElse(new int[0]);
        for (int i = 0; i < FILTER_SLOTS; i++) keepCounts[i] = i < counts.length ? counts[i] : 1;
        connected = in.getBooleanOr("Connected", false);
        networkPowered = in.getBooleanOr("NetworkPowered", false);
        powers = in.getBooleanOr("Powers", false);
        canPower = in.getBooleanOr("CanPower", false);
        List<ItemStack> entries = in.read("Filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < FILTER_SLOTS; i++) filter.getItems().set(i, i < entries.size() ? entries.get(i) : ItemStack.EMPTY);
    }
}
