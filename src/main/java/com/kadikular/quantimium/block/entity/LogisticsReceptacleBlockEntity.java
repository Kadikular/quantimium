package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.LogisticsReceptacleBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import com.kadikular.quantimium.laser.LogisticsMember;
import com.kadikular.quantimium.recipe.FilterEntry;
import com.kadikular.quantimium.util.LegacyItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A Logistics Receptacle: light passes straight through it (never along its part's axis, into the inventory), and
 * while a Logistics Controller's light reaches it, the inventory its part faces is on that controller's network, as
 * storage, a provider or a requester. See {@code Logistics}.
 */
public class LogisticsReceptacleBlockEntity extends BlockEntity implements LaserNode, LogisticsMember, MenuProvider, FluxMeterReadout, LaserBeamView {

    public static final int FILTER_SLOTS = 9;
    public static final int MAX_PRIORITY = 999;

    /** Ghost entries: what it stores, provides or requests. Empty, storage and providers take everything; a requester, nothing. */
    private final SimpleContainer filter = new SimpleContainer(FILTER_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            LogisticsReceptacleBlockEntity.this.setChanged();
        }
    };
    private boolean allow = true;
    /** A requester keeping counts stocked: each entry's count is how many it keeps in the inventory. */
    private boolean keep;
    private final int[] keepCounts = new int[FILTER_SLOTS];
    public static final int MAX_KEEP = 9_999;
    private Role role = Role.STORAGE;
    private boolean active;
    private int priority;
    private boolean connected;
    /** How often it looks, when active: see {@link LogisticsMember#pace()}. Not saved: a reload starts it looking. */
    private final int[] pace = new int[2];
    private Set<Direction> passing = EnumSet.noneOf(Direction.class);
    private List<LaserBeam> shownBeams = List.of();

    public LogisticsReceptacleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGISTICS_RECEPTACLE_BE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(LogisticsReceptacleBlock.FACING);
    }

    // ---- settings ----

    public void cycleRole() {
        role = role.next();
        changed();
    }

    /**
     * A provider on a machine that makes things to order: what that machine may make for the network from anything
     * on it (other machines' crafts too), only from what's stored, or nothing, offering only what's there.
     */
    public enum Crafts { STORED, NOTHING }

    private Crafts crafts = Crafts.STORED;

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

    /** An active requester: whether it has machines make what isn't stored, or takes only what's stored. */
    private boolean requestsMade = true;

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
        if (level == null) return null;
        return level.getBlockEntity(worldPosition.relative(facing())) instanceof com.kadikular.quantimium.laser.OfferMaker maker ? maker : null;
    }

    /** On another network's Logistics Materialiser: that network's offers, as its mode lets them out. */
    @Override
    @Nullable
    public com.kadikular.quantimium.laser.OfferTable imports() {
        if (level == null || !(level.getBlockEntity(worldPosition.relative(facing())) instanceof LogisticsMaterialiserBlockEntity materialiser)) return null;
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

    private void changed() {
        setChanged();
        showState();
        sync();
    }

    /** The block shows its role, and whether a controller's light reaches it. */
    private void showState() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(LogisticsReceptacleBlock.ROLE)) return;
        BlockState shown = state.setValue(LogisticsReceptacleBlock.ROLE, role).setValue(LogisticsReceptacleBlock.LIT, connected && networkPowered);
        if (shown != state) level.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
    }

    // ---- logistics ----

    @Override
    public LogisticsMember logistics() {
        return this;
    }

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
        if (level == null) return null;
        Direction face = facing();
        // A Laser Port's slots, whole: its faces are set for pipes, which may only put in while it sends.
        if (level.getBlockEntity(worldPosition.relative(face)) instanceof LaserPortBlockEntity port) return port.slots();
        return LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK, worldPosition.relative(face), face.getOpposite()));
    }

    @Override
    public BlockPos inventoryPos() {
        return worldPosition.relative(facing());
    }

    @Override
    public int[] pace() {
        return pace;
    }

    @Override
    public void connected(boolean connected) {
        if (this.connected == connected) return;
        this.connected = connected;
        showState();
        sync();
    }

    /** Whether its network's controller can pay for moves: it's lit only then. */
    private boolean networkPowered;

    @Override
    public void networkPowered(boolean powered) {
        if (networkPowered == powered) return;
        networkPowered = powered;
        showState();
    }

    // ---- powering the machine it's on ----

    /** Whether light reaching it powers the machine it's on, too. */
    private boolean powers;
    /** Whether the machine it's on takes power (worked out on the server, for the screen's tab). */
    private boolean canPower;

    public boolean powers() {
        return powers;
    }

    public boolean canPower() {
        return canPower;
    }

    public void togglePowers() {
        powers = !powers;
        changed();
        if (level != null && !level.isClientSide()) com.kadikular.quantimium.laser.LaserNetworks.changed(level);
    }

    /** Looks again whether the machine it's on takes power: when its screen opens, and when what's beside it changes. */
    public void checkPower() {
        if (level == null || level.isClientSide()) return;
        Direction face = facing();
        boolean can = level.getCapability(Capabilities.Energy.BLOCK, worldPosition.relative(face), face.getOpposite()) != null;
        if (can == canPower) return;
        canPower = can;
        setChanged();
        sync();
    }

    @Override
    @Nullable
    public Direction feeds() {
        return powers ? facing() : null;
    }

    // ---- the network ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @Override
    public Set<Direction> outputs() {
        return passing;
    }

    /** Light goes straight through, across its part's axis. */
    @Override
    public Set<Direction> outputsFor(Set<Direction> arriving) {
        Set<Direction> out = EnumSet.noneOf(Direction.class);
        for (Direction travel : arriving) if (travel.getAxis() != facing().getAxis()) out.add(travel);
        passing = out;
        return out;
    }

    /**
     * Light across it passes straight on; light onto its outer face, towards the inventory, joins it to the
     * network and stops there. None comes out of the inventory it serves.
     */
    @Override
    public boolean accepts(Direction travel) {
        return travel != facing().getOpposite();
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    @Override
    public boolean relays() {
        return true;
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (!same) sync();
    }

    @Override
    public List<LaserBeam> syncedBeams() {
        return shownBeams;
    }

    /** Light leaves from the focus in its middle, as it arrives there. */
    @Override
    public net.minecraft.world.phys.Vec3[] endsOf(LaserBeam beam) {
        return new net.minecraft.world.phys.Vec3[] {worldPosition.getCenter(), beam.ends()[1]};
    }

    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        return describe().withStyle(connected ? ChatFormatting.DARK_AQUA : ChatFormatting.GRAY);
    }

    /** Its role, whether active, and its priority: "Active provider, priority 5". */
    public MutableComponent describe() {
        String key = "message.quantimium.logistics_receptacle." + role.getSerializedName() + (role != Role.STORAGE && active ? ".active" : "");
        MutableComponent line = Component.translatable(key, priority);
        if (!connected) line.append(Component.translatable("message.quantimium.logistics_receptacle.unconnected"));
        return line;
    }

    // ---- life ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            showState();
            LaserNetworks.add(level, this);
        }
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.logistics_receptacle");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new com.kadikular.quantimium.menu.LogisticsReceptacleMenu(containerId, inventory, this);
    }

    private void sync() {
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("Role", role.ordinal());
        out.putBoolean("Active", active);
        out.putInt("Crafts", crafts.ordinal());
        out.putBoolean("RequestsMade", requestsMade);
        out.putInt("Priority", priority);
        out.putBoolean("Allow", allow);
        out.putBoolean("Keep", keep);
        out.putIntArray("KeepCounts", keepCounts);
        out.putBoolean("Connected", connected);
        out.putBoolean("Powers", powers);
        out.putBoolean("CanPower", canPower);
        List<ItemStack> entries = new ArrayList<>();
        for (int i = 0; i < FILTER_SLOTS; i++) entries.add(filter.getItem(i));
        out.store("Filter", ItemStack.OPTIONAL_CODEC.listOf(), entries);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
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
        powers = in.getBooleanOr("Powers", false);
        canPower = in.getBooleanOr("CanPower", false);
        List<ItemStack> entries = in.read("Filter", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < FILTER_SLOTS; i++) filter.getItems().set(i, i < entries.size() ? entries.get(i) : ItemStack.EMPTY);
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
