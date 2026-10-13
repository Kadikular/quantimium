package com.kadikular.quantimium.laser;

import com.kadikular.quantimium.init.ModDamageTypes;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One action one beam carries (see {@link Light.Stream}): breaking the block it ends on, hurting what
 * stands in it, or gathering items lying in it. Only the sources that pay for the stream pay for it,
 * and only for work done: a Harvest beam's power goes into cutting the block, a cut at a time, so a
 * block that goes before it's through takes the cut with it, as a player's mining would; a hit or an
 * item carried is paid for when it happens. It works only while the beam is lit. What it's done so far
 * is kept while the network is rebuilt; a world reload starts it over.
 */
public final class LensAction {

    /** Which: its node, which of the node's beams, and which action. */
    public record Key(BlockPos node, int beam, Lens action) {}

    private final Key key;
    private LaserBeam beam;
    private Light.Stream stream;
    /** Where what it brings back goes: the collecting Laser Ports up its own stream's light, nearest first. */
    private List<BlockPos> collectors = List.of();
    /** Where what it places comes from: the sending Laser Ports up its own stream's light, nearest first. */
    private List<BlockPos> senders = List.of();
    /** For an area prism's action: the blocks it works on, instead of the beam's end; null for a beam's. */
    @Nullable
    private net.minecraft.world.level.levelgen.structure.BoundingBox area;
    /** Which of the box's blocks are in the area: all of a picked area or a cube's; a sphere's, not its corners. */
    private java.util.function.Predicate<BlockPos> within = pos -> true;
    /** How far through the area it's looked, and the block it's found to work on next. */
    private int cursor;
    /** Blocks looked at in a row with nothing to do, and till when it rests after a whole fruitless sweep. */
    private int missed;
    private long restUntil;
    private int placeCooldown;
    /** For an area prism's action: the block it last placed, and until when it shows a beam there. */
    @Nullable
    private BlockPos placed;
    /** When it last did any work (a cut, a block placed, a hit): an area prism shows its beam only while it's working. */
    private long lastWorked = Long.MIN_VALUE;
    /** How far the light carrying it reaches: an area prism works only that far. */
    private int reach = LaserTuning.ACTION_REACH;
    /** The marks of the light carrying it ({@link Light#PHANTOM}, {@link Light#MIRRORED}). */
    private int marks;
    /** For Phantom light's upkeep: the solid blocks its beam passes through. */
    private int solids;
    private long now;

    /** The block being cut, what it costs, and the FE gone into it so far. */
    @Nullable
    private BlockPos cutting;
    /** The last block it saw along a clear line, and until when that's taken as still so. */
    @Nullable
    private BlockPos seen;
    private long seenUntil;
    private long cutCost;
    private long cut;
    private int cooldown;
    /** FE the cut, or the next hits, want this tick. */
    private long want;
    /** FE drawn towards the next Veil Thread, by mirrored Pickup from a held Veiled. */
    private long threadCut;
    /** FE charged towards the next hits: a hit can cost more than a tick's light, so it charges up, and keeps what it has. */
    private long charged;
    /** What it saw in the beam at its last sweep. */
    private List<LivingEntity> targets = new ArrayList<>();

    LensAction(Key key) {
        this.key = key;
    }

    public Key key() {
        return key;
    }

    void update(@Nullable LaserBeam beam, Light.Stream stream, List<BlockPos> collectors, List<BlockPos> senders,
                @Nullable net.minecraft.world.level.levelgen.structure.BoundingBox area, int reach, int marks, int solids) {
        this.reach = reach;
        this.marks = marks;
        this.solids = solids;
        this.beam = beam;
        this.stream = stream;
        this.collectors = List.copyOf(collectors);
        this.senders = List.copyOf(senders);
        if (!java.util.Objects.equals(this.area, area)) cursor = 0;
        this.area = area;
    }

    /** Smelting and Crushing working on the world on this light: what they'd still change, Harvest leaves to them. */
    private List<Lens> worldSteps = List.of();

    void worldSteps(List<Lens> steps) {
        worldSteps = steps;
    }

    /** What it asks of the network this tick: only a cut in progress asks; the rest pay as they act. */
    long demand() {
        return want;
    }

    /** Takes up to {@code fe} into the cut; what it took. */
    long fill(long fe) {
        long took = Math.min(fe, want);
        if (took > 0 && key.action() == Lens.HARVEST) lastWorked = now;
        if (key.action() == Lens.DAMAGE) charged += took;
        else if (key.action() == Lens.HARVEST) cut += took;
        else if (key.action() == Lens.PICKUP) threadCut += took;
        want -= took;
        return took;
    }

    /** The level, while it acts: for sight lines from inside entity filters. */
    @Nullable
    private ServerLevel sightLevel;

    /** Once a tick, before blocks are charged: work done at once pays first, and a cut asks for its next share. */
    void act(ServerLevel level, LaserNetworks networks) {
        sightLevel = level;
        now = level.getGameTime();
        boolean pickup = stream.has(Lens.PICKUP) && !collectors.isEmpty();
        int phase = Math.floorMod(key.hashCode() + (int) level.getGameTime(), LaserTuning.DAMAGE_INTERVAL);
        switch (key.action()) {
            case HARVEST -> harvest(level, networks, pickup);
            case DAMAGE -> damage(level, networks, phase == 0, pickup);
            case PICKUP -> {
                // In the mirror, Pickup draws Veil Thread out of a held Veiled its light reaches.
                if (mirrored()) drawThread(level, networks);
                if (phase != 0 || collectors.isEmpty()) return;
                // Items are small: anything within nearly a block of the beam's line counts. Each light takes
                // only what lies on its own side.
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && onBeam(item.getBoundingBox(), 0.45)
                        && com.kadikular.quantimium.event.MirrorPhaseInteractions.mirrorOnly(item) == mirrored()
                        && (stream.filter() == null || stream.filter().lets(item.getItem())))) {
                    carry(networks, item, true);
                }
            }
            // Nothing in the mirror to place, fill or empty.
            case PLACE -> {
                if (!mirrored()) place(level, networks);
            }
            case INSERT, EXTRACT -> {
                if (!mirrored()) move(level, networks, key.action() == Lens.INSERT);
            }
            case INTERACT -> {
                if (!mirrored()) interact(level, networks);
            }
            case SMELT, CRUSH -> {
                if (!mirrored()) processAt(level, networks);
            }
            // Phantom light's upkeep: so much a tick for each block it passes through, while it's lit.
            case PHANTOM -> want = cutCost = (long) solids * com.kadikular.quantimium.Config.phantomFePerBlock();
            default -> {}
        }
    }

    // ---- hitting ----

    /**
     * Charges up for the next hits while there's something to hit, and hits each sweep with what it's
     * charged: each Amplify lens doubles a hit, and what it costs, so a hit can take more than one tick's
     * light to charge. What's charged is kept, never lost, up to one round of hits.
     */
    private void damage(ServerLevel level, LaserNetworks networks, boolean sweep, boolean pickup) {
        int scale = 1 << stream.amps();
        long hit = LaserTuning.DAMAGE_FE_PER_HIT * scale;
        if (sweep) {
            List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive()
                    && onBeam(entity.getBoundingBox(), 0.25) && inMirror(entity) == mirrored()
                    && (stream.filter() == null || stream.filter().letsHit(entity)));
            // A Filter's keep count: that many are spared, and only those beyond it are hit. Always the same
            // ones spared (the oldest), so a hit is finished on one rather than spread over the herd.
            int keep = stream.filter() == null ? 0 : stream.filter().keep();
            if (keep > 0) found = found.stream().sorted(java.util.Comparator.comparingInt(LivingEntity::getId).reversed()).toList();
            targets = found.size() <= keep ? new ArrayList<>() : new ArrayList<>(found.subList(0, found.size() - keep));
            // With Harmonic lenses it hits as a hand holding Looting would, so what it kills drops more.
            LaserHand looter = stream.fortune() > 0 && !targets.isEmpty() ? lootingHand(level) : null;
            var source = looter == null ? level.damageSources().source(ModDamageTypes.LASER)
                    : level.damageSources().source(ModDamageTypes.LASER, looter);
            for (LivingEntity entity : targets) {
                if (charged < hit) break;
                if (entity.hurtServer(level, source, LaserTuning.DAMAGE_PER_HIT * scale)) {
                    charged -= hit;
                    networks.workDone(this);
                    lastWorked = now;
                    // Killed, with Pickup: what it drops goes straight back up the beam, wherever it lands.
                    if (pickup && !entity.isAlive()) gatherDrops(level, networks, entity);
                    // Smelting or Crushing, without Pickup: what it drops is cooked (or crushed) where it falls.
                    else if (!entity.isAlive() && processes()) processDrops(level, networks, entity);
                }
            }
            if (looter != null) looter.letGo();
        }
        targets.removeIf(entity -> !entity.isAlive());
        want = Math.max(0, hit * targets.size() - charged);
    }

    /** The laser's hand, at the beam's source, holding a blade with Looting for each Harmonic lens. */
    private LaserHand lootingHand(ServerLevel level) {
        LaserHand hand = LaserHand.of(level);
        BlockPos at = beam != null ? beam.from() : key.node();
        hand.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        ItemStack blade = new ItemStack(Items.STICK);
        blade.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOOTING), stream.fortune());
        hand.hold(blade);
        return hand;
    }

    // ---- interacting ----

    /**
     * Uses one thing a sending Laser Port up the beam sends (or an empty hand, with none) on what the beam ends on, as a
     * player would: set to use, a right click on the block (a door, a lever, an item on a block; an empty
     * bucket fills from the fluid it ends on); set to hit, a swing at the first thing living in the beam.
     * In an area, the same on what's living there, or on its blocks in turn. What's left in hand after goes
     * back up the beam to a collecting Laser Port, or to the sending Laser Port.
     */
    private void interact(ServerLevel level, LaserNetworks networks) {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        if ((beam == null && area == null) || (area != null && now < restUntil) || !networks.canPay(this, LaserTuning.PLACE_FE)) return;
        // It works with what the sending Laser Port holds where it is: the hand holds a copy, and only once using it has
        // changed it (eaten, filled, worn) is the real one taken and what it became sent on. Unused, nothing moves.
        // Each kind of thing the sending Laser Port holds in turn, till one is used: carrots for the pigs, wheat for the cows, shears.
        List<ItemStack> tried = new ArrayList<>();
        ItemStack sent;
        ItemStack after;
        List<ItemStack> extras;
        boolean done;
        LaserHand hand = handFor(level);
        do {
            sent = senders.isEmpty() ? ItemStack.EMPTY : networks.take(senders, stack -> lets(stack)
                    && tried.stream().noneMatch(seen -> ItemStack.isSameItemSameComponents(seen, stack)), 1, true);
            if (!sent.isEmpty()) tried.add(sent);
            ItemStack held = sent.copy();
            hand.hold(held);
            done = stream.hits() ? swing(level, hand) : use(level, hand, held);
            // Whatever it holds now: a filled bucket, what's left of a stack, a worn sword.
            after = hand.letGo();
            extras = List.copyOf(hand.getInventory().getNonEquipmentItems());
            hand.getInventory().clearContent();
        } while (!done && !sent.isEmpty() && !stream.hits() && tried.size() < LaserTuning.INTERACT_TRIES);
        if (!done) return;
        ItemStack used = sent;
        boolean changed = !ItemStack.matches(after, used) || extras.stream().anyMatch(extra -> !extra.isEmpty());
        if (changed && !used.isEmpty()) networks.take(senders, stack -> ItemStack.isSameItemSameComponents(stack, used), 1, false);
        if (changed) {
            for (ItemStack extra : extras) settle(level, networks, extra, used);
            settle(level, networks, after, used);
        }
        networks.pay(this, LaserTuning.PLACE_FE);
        networks.workDone(this);
        placeCooldown = LaserTuning.PLACE_COOLDOWN / stream.pace();
        if (area == null) placed = beam.hit();
        lastWorked = now;
        // A door opened, a block changed: the beam may reach on, or not. An area's beams don't change.
        if (area == null) LaserNetworks.changed(level);
    }

    /**
     * Puts what the hand held back where it belongs: what's still what was sent (food nobody ate, a worn
     * sword) back to the sending Laser Port first; anything new (a filled bucket) up to a collecting Laser Port first; else the
     * other; else on the ground.
     */
    private void settle(ServerLevel level, LaserNetworks networks, ItemStack stack, ItemStack sent) {
        if (stack.isEmpty()) return;
        boolean same = stack.is(sent.getItem());
        ItemStack left = same ? networks.returnTo(senders, stack) : stack;
        if (!left.isEmpty() && !collectors.isEmpty()) left = networks.collect(collectors, left);
        if (!same && !left.isEmpty()) left = networks.returnTo(senders, left);
        if (!left.isEmpty()) Block.popResource(level, handSpot(), left);
    }

    /** Where the stand-in player stands: before what the beam ends on, or in an area, by what it's working on. */
    private BlockPos handSpot() {
        if (area != null) return placed != null && area.isInside(placed) ? placed.above() : key.node();
        return beam.hit() != null ? beam.hit().relative(beam.face().getOpposite()) : beam.from();
    }

    /** A right click: with something in hand, on the first thing living in the beam that takes it; else on the block the beam ends on. */
    private boolean use(ServerLevel level, LaserHand hand, ItemStack held) {
        if (!held.isEmpty() && useOnLiving(level, hand)) return true;
        if (area != null) return useInArea(level, hand, held);
        if (beam.hit() == null) return false;
        // Something light passes, just before the end (a lever, a button, a crop), is what it uses; else what it ends on.
        BlockPos before = beam.hit().relative(beam.face().getOpposite());
        BlockPos pos = beam.length() > 0 && !level.getBlockState(before).isAir() ? before : beam.hit();
        BlockState state = level.getBlockState(pos);
        // An empty bucket fills from a fluid's source.
        if (held.is(Items.BUCKET) && state.getBlock() instanceof net.minecraft.world.level.block.BucketPickup pickup && !state.getFluidState().isEmpty()) {
            ItemStack filled = pickup.pickupBlock(hand, level, pos, state);
            if (filled.isEmpty()) return false;
            hand.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, filled);
            return true;
        }
        var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).relative(beam.face().getOpposite(), 0.5),
                beam.face().getOpposite(), pos, false);
        net.minecraft.world.InteractionResult result = hand.gameMode.useItemOn(hand, level, held, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        return result.consumesAction();
    }

    /**
     * Uses what's in hand on what's living in the beam, as a player's right click would: feeding, shearing,
     * milking. A Filter says on what (only grown ones, say). Never a villager, which would wait for a trade
     * nobody makes.
     */
    private boolean useOnLiving(ServerLevel level, LaserHand hand) {
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive() && entity != hand
                && !(entity instanceof net.minecraft.world.entity.player.Player) && !(entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager)
                && onBeam(entity.getBoundingBox(), 0.25) && !inMirror(entity) && (stream.filter() == null || stream.filter().letsHit(entity)));
        for (LivingEntity entity : found) {
            Vec3 at = entity.getBoundingBox().getCenter().subtract(entity.position());
            if (hand.interactOn(entity, net.minecraft.world.InteractionHand.MAIN_HAND, at).consumesAction()) {
                touching(entity);
                return true;
            }
        }
        return false;
    }

    /** A right click on the next block of the area that takes one, from the side facing the prism; after a sweep with none, a rest. */
    private boolean useInArea(ServerLevel level, LaserHand hand, ItemStack held) {
        for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
            BlockPos pos = areaPos(cursor);
            cursor = (cursor + 1) % areaVolume();
            if (within.test(pos) && level.isLoaded(pos) && !level.getBlockState(pos).isAir() && inSight(level, pos)) {
                net.minecraft.core.Direction face = net.minecraft.core.Direction.getApproximateNearest(Vec3.atCenterOf(key.node()).subtract(Vec3.atCenterOf(pos)));
                hand.setPos(pos.relative(face).getX() + 0.5, pos.relative(face).getY(), pos.relative(face).getZ() + 0.5);
                var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
                if (hand.gameMode.useItemOn(hand, level, held, net.minecraft.world.InteractionHand.MAIN_HAND, hit).consumesAction()) {
                    missed = 0;
                    placed = pos;
                    touched = null;
                    return true;
                }
            }
            if (fruitless()) return false;
        }
        return false;
    }

    /** What an area's Interact last worked on that moves: its beam follows it. */
    @Nullable
    private net.minecraft.world.entity.Entity touched;

    private void touching(net.minecraft.world.entity.Entity entity) {
        touched = entity;
        placed = entity.blockPosition();
    }

    /** A swing at the first thing living in the beam, with what's in hand. */
    private boolean swing(ServerLevel level, LaserHand hand) {
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box(), entity -> entity.isAlive() && entity != hand
                && onBeam(entity.getBoundingBox(), 0.25) && !inMirror(entity) && (stream.filter() == null || stream.filter().letsHit(entity)));
        if (found.isEmpty()) return false;
        hand.attack(found.getFirst());
        touching(found.getFirst());
        return true;
    }

    /** A stand-in player at the beam, facing the way it goes. */
    private LaserHand handFor(ServerLevel level) {
        LaserHand hand = LaserHand.of(level);
        BlockPos at = handSpot();
        hand.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (beam == null) return hand;
        hand.setYRot(beam.face().toYRot());
        hand.setXRot(beam.face() == net.minecraft.core.Direction.UP ? -90 : beam.face() == net.minecraft.core.Direction.DOWN ? 90 : 0);
        return hand;
    }

    // ---- inventories ----

    /**
     * Insert: a stack of what the sending Laser Ports up the beam send, as much as fits, into the inventory the beam
     * ends on. Extract: a stack out of it, as much as the collecting Laser Ports up the beam have room for. In an
     * area, each inventory there in turn. A Filter says what; one stack each wait.
     */
    private void move(ServerLevel level, LaserNetworks networks, boolean in) {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        if (in ? senders.isEmpty() : collectors.isEmpty()) return;
        long most = LaserTuning.PICKUP_FE_PER_STACK + moveMost() * ((stream.has(Lens.SMELT) ? LaserTuning.SMELT_FE_PER_ITEM : 0)
                + (stream.has(Lens.CRUSH) ? LaserTuning.CRUSH_FE_PER_ITEM : 0));
        if (!networks.canPay(this, most)) return;
        BlockPos at = area == null ? inventoryAtEnd(level) : nextAreaInventory(level);
        if (at == null) return;
        net.minecraft.core.Direction side = area == null ? beam.face().getOpposite() : null;
        net.neoforged.neoforge.items.IItemHandler handler = com.kadikular.quantimium.util.LegacyItems.legacy(
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, at, side));
        if (handler == null) return;
        // What crushing and smelting on the way cost, or -1 for nothing moved.
        // What can go neither in nor back falls out by the inventory: before it, on a beam; on top of it, in an area.
        BlockPos spill = area == null ? beam.hit().relative(beam.face().getOpposite()) : at.above();
        long processing = in ? insertInto(level, networks, handler, spill) : extractFrom(level, networks, handler, spill);
        // Nothing to move (nothing it may take, or no room): it looks again in a while, not every tick.
        if (processing < 0) {
            placeCooldown = LaserTuning.IDLE_RETRY;
            return;
        }
        networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK + processing);
        networks.workDone(this);
        placeCooldown = LaserTuning.PLACE_COOLDOWN / stream.pace();
        placed = at;
        lastWorked = now;
    }

    /**
     * Into the inventory: what a sending Laser Port sends, crushed and smelted on the way with those lenses. What that
     * cost, or -1 for none moved. What makes several things of one goes one at a time.
     */
    private long insertInto(ServerLevel level, LaserNetworks networks, net.neoforged.neoforge.items.IItemHandler handler, BlockPos spill) {
        ItemStack offered = networks.take(senders, stack -> lets(stack)
                && arriving(level, stack, 1).stream().allMatch(each -> fits(handler, each) == each.getCount()), moveMost(), true);
        if (offered.isEmpty()) return -1;
        Processing.Made made = made(level, offered);
        int count = 1;
        if (made.ofOne().size() == 1) {
            ItemStack one = made.ofOne().getFirst();
            count = fits(handler, times(one, offered.getCount())) / one.getCount();
        }
        if (count <= 0) return -1;
        ItemStack taken = networks.take(senders, stack -> ItemStack.isSameItemSameComponents(stack, offered), count, false);
        if (taken.isEmpty()) return -1;
        for (ItemStack each : arriving(level, taken, taken.getCount())) {
            ItemStack left = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, each, false);
            if (!left.isEmpty()) left = networks.returnTo(senders, left);
            if (!left.isEmpty()) Block.popResource(level, spill, left);
        }
        return made.fe() * taken.getCount();
    }

    /** Out of the inventory, up to a collecting Laser Port: crushed and smelted on the way with those lenses. What that cost, or -1 for none moved. */
    private long extractFrom(ServerLevel level, LaserNetworks networks, net.neoforged.neoforge.items.IItemHandler handler, BlockPos spill) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack there = handler.extractItem(slot, moveMost(), true);
            if (there.isEmpty() || !lets(there)) continue;
            Processing.Made made = made(level, there);
            int room;
            if (made.ofOne().size() == 1) {
                ItemStack one = made.ofOne().getFirst();
                ItemStack comes = times(one, there.getCount());
                if (!networks.roomFor(collectors, comes)) continue;
                room = (comes.getCount() - networks.simulateCollect(collectors, comes).getCount()) / one.getCount();
            } else {
                room = made.ofOne().stream().allMatch(each -> networks.roomForAll(collectors, each)) ? 1 : 0;
            }
            if (room <= 0) continue;
            ItemStack taken = handler.extractItem(slot, room, false);
            for (ItemStack each : arriving(level, taken, taken.getCount())) {
                ItemStack left = networks.collect(collectors, each);
                if (!left.isEmpty()) left = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, left, false);
                if (!left.isEmpty()) Block.popResource(level, spill, left);
            }
            return made.fe() * taken.getCount();
        }
        return -1;
    }

    /** The most Insert or Extract moves at once: {@link LaserTuning#MOVE_ITEMS}, doubled for each Speed lens. */
    private int moveMost() {
        return LaserTuning.MOVE_ITEMS << stream.speed();
    }

    /** {@code count} of {@code stack} as it would arrive: crushed and smelted, with those lenses, where they can be. */
    private List<ItemStack> arriving(ServerLevel level, ItemStack stack, int count) {
        Processing.Made made = made(level, stack);
        return made.changed() ? Processing.times(made.ofOne(), count) : List.of(stack.copyWithCount(count));
    }

    /** How much of {@code stack} the inventory would take. */
    private static int fits(net.neoforged.neoforge.items.IItemHandler handler, ItemStack stack) {
        return stack.getCount() - net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, stack.copy(), true).getCount();
    }

    private boolean lets(ItemStack stack) {
        return stream.filter() == null || stream.filter().lets(stack);
    }

    /** The inventory a beam ends on, if it ends on one. */
    @Nullable
    private BlockPos inventoryAtEnd(ServerLevel level) {
        if (beam == null || beam.hit() == null || beam.end() == LaserBeam.End.OPEN) return null;
        return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, beam.hit(), beam.face().getOpposite()) == null
                ? null : beam.hit();
    }

    /** The next inventory in the area it can see, looking on from the last. */
    @Nullable
    private BlockPos nextAreaInventory(ServerLevel level) {
        if (now < restUntil) return null;
        for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
            BlockPos pos = areaPos(cursor);
            cursor = (cursor + 1) % areaVolume();
            if (within.test(pos) && level.isLoaded(pos) && level.getBlockEntity(pos) != null
                    && level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, null) != null && inSight(level, pos)) {
                missed = 0;
                return pos;
            }
            if (fruitless()) return null;
        }
        return null;
    }

    // ---- placing ----

    /** Places a block a sending Laser Port up the beam sends, in the space where the beam ends (or the next free one in an area). */
    private void place(ServerLevel level, LaserNetworks networks) {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        if (senders.isEmpty()) return;
        long cost = area == null ? LaserTuning.PLACE_FE : Math.round(LaserTuning.PLACE_FE * LaserTuning.AREA_COST);
        if (!networks.canPay(this, cost)) return;
        net.minecraft.world.phys.BlockHitResult at = area == null ? placeHit(level) : nextAreaSpot(level, networks);
        if (at == null) return;
        cost += throughCost(level, at.getBlockPos().relative(at.getDirection()));
        if (!networks.canPay(this, cost)) return;
        ItemStack offered = networks.take(senders, stack -> placeable(level, stack, at), 1, true);
        if (offered.isEmpty()) return;
        ItemStack taken = networks.take(senders, stack -> ItemStack.isSameItemSameComponents(stack, offered), 1, false);
        if (taken.isEmpty()) return;
        boolean done;
        if (taken.getItem() instanceof net.minecraft.world.item.BucketItem bucket) {
            // A bucket of water or lava empties where the block would go, as a dispenser empties one; the bucket goes back.
            BlockPos spot = at.getBlockPos().relative(at.getDirection());
            done = bucket.emptyContents(null, level, spot, at, taken);
            if (done) {
                ItemStack empty = networks.returnTo(senders, new ItemStack(Items.BUCKET));
                if (!empty.isEmpty()) Block.popResource(level, spot, empty);
                taken = ItemStack.EMPTY;
            }
        } else if (taken.getItem() instanceof net.minecraft.world.item.BlockItem item) {
            done = item.place(new net.minecraft.world.item.context.BlockPlaceContext(
                    level, null, net.minecraft.world.InteractionHand.MAIN_HAND, taken, at)).consumesAction();
        } else {
            done = false;
        }
        if (!done) {
            // It wouldn't go after all: back where it came from.
            ItemStack back = networks.returnTo(senders, taken);
            if (!back.isEmpty()) Block.popResource(level, at.getBlockPos().relative(at.getDirection()), back);
            return;
        }
        networks.pay(this, cost);
        networks.workDone(this);
        // Each Speed lens halves the wait for the next.
        placeCooldown = LaserTuning.PLACE_COOLDOWN / stream.pace();
        placed = at.getBlockPos().relative(at.getDirection());
        lastWorked = now;
        // A beam now ends on what it placed; an area's beams don't change.
        if (area == null) LaserNetworks.changed(level);
    }

    /** Where a beam places: against the block it ends on, from its side. */
    @Nullable
    private net.minecraft.world.phys.BlockHitResult placeHit(ServerLevel level) {
        if (beam == null || beam.hit() == null || (beam.end() != LaserBeam.End.BLOCKED && beam.end() != LaserBeam.End.SINK)) return null;
        BlockPos spot = beam.hit().relative(beam.face().getOpposite());
        if (!level.getBlockState(spot).canBeReplaced()) return null;
        return new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(beam.hit()), beam.face().getOpposite(), beam.hit(), false);
    }

    /** Whether {@code stack} would go in where {@code at} says, and the stream's filter lets it. */
    private boolean placeable(ServerLevel level, ItemStack stack, net.minecraft.world.phys.BlockHitResult at) {
        if (stream.filter() != null && !stream.filter().lets(stack)) return false;
        if (stack.getItem() instanceof net.minecraft.world.item.BucketItem bucket) {
            return bucket.getContent() != net.minecraft.world.level.material.Fluids.EMPTY
                    && level.getBlockState(at.getBlockPos().relative(at.getDirection())).canBeReplaced();
        }
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)) return false;
        var context = new net.minecraft.world.item.context.BlockPlaceContext(level, null, net.minecraft.world.InteractionHand.MAIN_HAND, stack.copyWithCount(1), at);
        if (!context.canPlace()) return false;
        BlockState state = item.getBlock().getStateForPlacement(context);
        return state != null && state.canSurvive(level, context.getClickedPos());
    }

    /** The next free space in the area something could be placed in: one resting on a block below it. */
    @Nullable
    private net.minecraft.world.phys.BlockHitResult nextAreaSpot(ServerLevel level, LaserNetworks networks) {
        if (now < restUntil) return null;
        for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
            BlockPos pos = areaPos(cursor);
            cursor = (cursor + 1) % areaVolume();
            if (fruitless()) return null;
            if (!within.test(pos) || !level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()) continue;
            BlockPos below = pos.below();
            if (level.getBlockState(below).isAir() || !inSight(level, pos)) continue;
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(below), net.minecraft.core.Direction.UP, below, false);
            if (!networks.take(senders, stack -> placeable(level, stack, hit), 1, true).isEmpty()) {
                missed = 0;
                return hit;
            }
        }
        return null;
    }

    /** Counts one more block with nothing to do; after a whole sweep of them, it rests a while. Whether it's resting now. */
    private boolean fruitless() {
        if (++missed < areaVolume()) return false;
        missed = 0;
        restUntil = now + LaserTuning.AREA_REST;
        return true;
    }

    private int areaVolume() {
        return area == null ? 1 : area.getXSpan() * area.getYSpan() * area.getZSpan();
    }

    /** For an area prism's action: which blocks of its box are in its area. */
    void within(java.util.function.Predicate<BlockPos> within) {
        this.within = within;
    }

    /** The {@code index}th block of the area, row by row, bottom up. */
    private BlockPos areaPos(int index) {
        int x = area.getXSpan();
        int z = area.getZSpan();
        int i = Math.floorMod(index, areaVolume());
        return new BlockPos(area.minX() + i % x, area.minY() + i / (x * z), area.minZ() + (i / x) % z);
    }

    /** Puts power into cutting the block the beam ends on; breaks it when the cut is through. */
    private void harvest(ServerLevel level, LaserNetworks networks, boolean pickup) {
        if (cooldown > 0) cooldown--;
        BlockPos target = harvestTarget(level, networks, pickup);
        if (target == null || !target.equals(cutting)) {
            // A new block, or none: the cut on the old one is lost, as a player's would be.
            if (cutting != null) level.destroyBlockProgress(crackId(), cutting, -1);
            cutting = target;
            cut = 0;
            cutCost = target == null ? 0 : Math.round(harvestCost(level, target) * (1 + stream.amps()) * (area == null ? 1.0 : LaserTuning.AREA_COST))
                    + throughCost(level, target);
        }
        if (target == null) {
            want = 0;
            return;
        }
        // Smelting or Crushing before it on the light do their work on the block first: sandstone becomes glass, then it's cut.
        if (area == null && !worldSteps.isEmpty() && !level.getBlockState(target).getBlock().asItem().equals(Items.AIR)
                && Processing.ofOne(level, key.node(), new ItemStack(level.getBlockState(target).getBlock().asItem()), worldSteps).changed()) {
            want = 0;
            return;
        }
        // With Pickup, it only cuts what it can carry away: no room up the beam for the drops, it waits.
        if (pickup && !roomForDrops(level, networks, target)) {
            want = 0;
            return;
        }
        if (cut >= cutCost && cooldown <= 0) {
            level.destroyBlockProgress(crackId(), target, -1);
            breakBlock(level, networks, target, pickup);
            networks.workDone(this);
            cooldown = LaserTuning.HARVEST_COOLDOWN / stream.pace();
            cutting = null;
            cut = 0;
            want = 0;
            return;
        }
        level.destroyBlockProgress(crackId(), target, (int) Math.min(9, cut * 10 / Math.max(1, cutCost)));
        want = Math.max(0, cutCost - cut);
    }

    /**
     * The block a Harvest beam would break: one light passes through just before its end (a crop on
     * farmland, grass), or else what it ends on, if it can be broken. In an area, the one being cut, or
     * the next that can be. With Pickup, an area or Phantom light passes over what it couldn't carry away.
     */
    @Nullable
    private BlockPos harvestTarget(ServerLevel level, LaserNetworks networks, boolean pickup) {
        if (area != null) {
            if (cutting != null && area.isInside(cutting) && cuttable(level, cutting) && inSight(level, cutting)) return cutting;
            if (now < restUntil) return null;
            for (int i = 0; i < LaserTuning.AREA_SCAN * stream.pace(); i++) {
                BlockPos pos = areaPos(cursor);
                cursor = (cursor + 1) % areaVolume();
                if (within.test(pos) && level.isLoaded(pos) && cuttable(level, pos) && inSight(level, pos)
                        && (!pickup || roomForDrops(level, networks, pos))) {
                    missed = 0;
                    return pos;
                }
                if (fruitless()) return null;
            }
            return null;
        }
        if (beam == null) return null;
        // Phantom light goes through what it cuts: the first block on its way it may cut (the ore, with a Filter).
        // Mirrored light reaches the crystal it can see in the mirror, wherever on its way.
        if ((marks & Light.PHANTOM) != 0 || mirrored()) {
            if (mirrored() && beam.hit() != null && cuttable(level, beam.hit())) return beam.hit();
            for (BlockPos pos : beam.path()) {
                if (level.isLoaded(pos) && !level.getBlockState(pos).isAir() && cuttable(level, pos)
                        && (!pickup || roomForDrops(level, networks, pos))) return pos;
            }
            return null;
        }
        if (beam.hit() == null) return null;
        if (beam.end() != LaserBeam.End.BLOCKED && beam.end() != LaserBeam.End.SINK) return null;
        BlockPos before = beam.hit().relative(beam.face().getOpposite());
        if (beam.length() > 0 && !level.getBlockState(before).isAir() && cuttable(level, before)) return before;
        return cuttable(level, beam.hit()) ? beam.hit() : null;
    }

    /** Whether a Harvest beam may break the block at {@code pos}: in the mirror, only a fully grown Anomalite crystal. */
    private boolean cuttable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (mirrored()) {
            return state.is(com.kadikular.quantimium.init.ModBlocks.ANOMALITE_CRYSTAL.get())
                    && state.getValue(com.kadikular.quantimium.block.AnomaliteCrystalBlock.AGE) >= 3;
        }
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) return false;
        if (state.is(ModTags.LASER_UNBREAKABLE)) return false;
        // Plain, it cuts as an iron pickaxe: what needs better waits for an Amplify lens.
        if (state.requiresCorrectToolForDrops() && !tool(level).isCorrectToolForDrops(state)) return false;
        return stream.filter() == null || stream.filter().lets(state, () -> Block.getDrops(state, level, pos, null, null, tool(level)));
    }

    /** What it cuts as: an iron pickaxe, or with an Amplify lens a diamond one; with Silk Touch for Precision, Fortune for each Harmonic lens. */
    private ItemStack tool(ServerLevel level) {
        ItemStack tool = new ItemStack(stream.amps() > 0 ? Items.DIAMOND_PICKAXE : Items.IRON_PICKAXE);
        var enchantments = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        if (stream.has(Lens.PRECISION)) tool.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
        if (stream.fortune() > 0) tool.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE), stream.fortune());
        return tool;
    }

    private long harvestCost(ServerLevel level, BlockPos pos) {
        // A crystal takes as long to take apart as a Harvest Laser's does.
        if (mirrored()) {
            return (long) com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.FE_PER_TICK
                    * com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.HARVEST_TICKS;
        }
        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        return (long) Math.ceil(Math.max(1.0f, hardness) * LaserTuning.HARVEST_FE_PER_HARDNESS);
    }

    /**
     * Whether the collecting Laser Ports up the beam would take all of what breaking the block would drop: room for it,
     * and filters that let it in. With Pickup, nothing is cut that would be left lying.
     */
    private boolean roomForDrops(ServerLevel level, LaserNetworks networks, BlockPos pos) {
        for (ItemStack drop : drops(level, pos)) {
            for (ItemStack carried : processed(level, drop)) {
                if (!networks.roomForAll(collectors, carried)) return false;
            }
        }
        return true;
    }

    /** Breaks it as its pickaxe would, the drops going back up the beam with Pickup. */
    private void breakBlock(ServerLevel level, LaserNetworks networks, BlockPos pos, boolean pickup) {
        BlockState state = level.getBlockState(pos);
        List<ItemStack> drops = new ArrayList<>(drops(level, pos));
        if (mirrored()) {
            // On the mirror's side: the real world hears only the light, and the tear leaks as a Harvest Laser's does.
            com.kadikular.quantimium.phase.OverlayBlockEdit.allow(() -> com.kadikular.quantimium.phase.MirrorSounds.breakQuietly(level, pos));
            com.kadikular.quantimium.phase.MirrorSounds.play(level, pos, net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.9f);
            com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.leakFromTear(level, area == null ? beam.from() : key.node());
        } else {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            level.removeBlock(pos, false);
        }
        for (ItemStack drop : drops) {
            // Crushed and smelted on the way, picked up or not; with too little light for that, it comes as it is.
            List<ItemStack> out = processed(level, drop);
            long smelt = processCost(level, drop);
            if (smelt > 0 && !networks.canPay(this, smelt + (pickup ? LaserTuning.PICKUP_FE_PER_STACK : 0))) {
                out = List.of(drop);
                smelt = 0;
            }
            List<ItemStack> left = out;
            if (pickup && out.stream().allMatch(stack -> networks.roomFor(collectors, stack))
                    && networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK + smelt)) {
                left = out.stream().map(stack -> networks.collect(collectors, stack)).toList();
            } else if (smelt > 0) {
                networks.pay(this, smelt);
            }
            for (ItemStack stack : left) {
                if (stack.isEmpty()) continue;
                // Cut on the mirror's side, it falls there: only the mirror sees it, or can take it.
                if (mirrored()) com.kadikular.quantimium.event.MirrorPhaseInteractions.dropInMirror(level, pos, stack);
                else Block.popResource(level, pos, stack);
            }
        }
        // A beam now reaches on past where the block was; an area's beams don't change.
        if (area == null) LaserNetworks.changed(level);
    }

    /** What breaking it gives: a crystal its shards, as a Harvest Laser's; anything else, what its pickaxe would get. */
    private List<ItemStack> drops(ServerLevel level, BlockPos pos) {
        if (mirrored()) {
            return List.of(new ItemStack(com.kadikular.quantimium.init.ModItems.ANOMALITE_SHARD.get(),
                    com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.SHARDS));
        }
        // Unrealising: ore comes away as Unrealised Matter, one to a block, with no history yet.
        if (stream.has(Lens.UNREALISE) && com.kadikular.quantimium.Config.unrealisingLens()
                && level.getBlockState(pos).is(net.neoforged.neoforge.common.Tags.Blocks.ORES)) {
            return List.of(new ItemStack(com.kadikular.quantimium.init.ModItems.UNREALISED_MATTER.get()));
        }
        return Block.getDrops(level.getBlockState(pos), level, pos, null, null, tool(level));
    }

    private boolean mirrored() {
        return (marks & Light.MIRRORED) != 0;
    }

    /** Whether {@code entity} is of the mirror: the Veiled, a mirror mite. Mirrored light hits only those; real light, everything else. */
    private static boolean inMirror(LivingEntity entity) {
        return entity instanceof com.kadikular.quantimium.entity.Veiled || entity instanceof com.kadikular.quantimium.entity.MirrorEndermite;
    }

    // ---- Veil Thread ----

    /** For tests: a thread being drawn is {@code ticksLeft} ticks of drawing from done. */
    void nearlyDrawn(int ticksLeft) {
        if (key.action() != Lens.PICKUP || !mirrored()) return;
        long whole = (long) com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_FE_PER_TICK
                * com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_TICKS;
        threadCut = Math.max(threadCut, whole - (long) ticksLeft * com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_FE_PER_TICK);
    }

    /**
     * Mirrored Pickup whose light reaches into a Containment Hall's cell, through its glass, draws Veil
     * Thread out of the Veiled held there, as a Harvest Laser does: one hall to one draw at a time, at the
     * same rate, the thread going up the beam to a collecting Laser Port.
     */
    private void drawThread(ServerLevel level, LaserNetworks networks) {
        com.kadikular.quantimium.block.entity.ContainmentHallBlockEntity hall = null;
        if (beam != null) {
            List<BlockPos> reached = new ArrayList<>(beam.path());
            if (beam.hit() != null) reached.add(beam.hit());
            for (BlockPos pos : reached) {
                hall = com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.hallHolding(level, pos);
                if (hall != null) break;
            }
        }
        ItemStack thread = new ItemStack(com.kadikular.quantimium.init.ModItems.VEIL_THREAD.get());
        if (hall == null || !networks.roomForAll(collectors, thread) || !hall.claimThread(key.node(), now)) {
            want = 0;
            return;
        }
        long whole = (long) com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_FE_PER_TICK
                * com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_TICKS;
        if (threadCut >= whole) {
            threadCut = 0;
            networks.collect(collectors, thread);
            networks.workDone(this);
            lastWorked = now;
            level.playSound(null, key.node(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 0.6f);
        }
        // Drawn at a Harvest Laser's pace: so much a tick, however much light there is.
        want = Math.min(com.kadikular.quantimium.block.entity.HarvestLaserBlockEntity.THREAD_FE_PER_TICK, whole - threadCut);
    }

    /** Items a kill just dropped, near where it fell. */
    private void gatherDrops(ServerLevel level, LaserNetworks networks, LivingEntity killed) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, killed.getBoundingBox().inflate(1.5),
                item -> item.isAlive() && item.tickCount <= 1)) {
            // All of it: the filter chose what to hit, not what it drops.
            carry(networks, item, false);
        }
    }

    /** Crushes and smelts the items a kill just dropped, where they lie. */
    private void processDrops(ServerLevel level, LaserNetworks networks, LivingEntity killed) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, killed.getBoundingBox().inflate(1.5),
                item -> item.isAlive() && item.tickCount <= 1)) {
            long cost = processCost(level, item.getItem());
            if (cost > 0 && networks.pay(this, cost)) replaceWith(level, item, processed(level, item.getItem()));
        }
    }

    /** Carries an item up the beam, if a collecting Laser Port has room and the stream's sources can pay; with {@code filtered}, only what its filter lets. */
    private void carry(LaserNetworks networks, ItemEntity item, boolean filtered) {
        ItemStack stack = item.getItem();
        if (filtered && stream.filter() != null && !stream.filter().lets(stack)) return;
        ServerLevel level = (ServerLevel) item.level();
        List<ItemStack> carried = processed(level, stack);
        if (!carried.stream().allMatch(each -> networks.roomFor(collectors, each))
                || !networks.pay(this, LaserTuning.PICKUP_FE_PER_STACK + processCost(level, stack))) return;
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack each : carried) {
            ItemStack rest = networks.collect(collectors, each.copy());
            if (!rest.isEmpty()) left.add(rest);
        }
        networks.workDone(this);
        replaceWith(level, item, left);
    }

    /** {@code item} becomes {@code stacks}, where it lies: gone with none, more items with more. */
    private static void replaceWith(ServerLevel level, ItemEntity item, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            item.discard();
            return;
        }
        item.setItem(stacks.getFirst());
        for (ItemStack rest : stacks.subList(1, stacks.size())) level.addFreshEntity(new ItemEntity(level, item.getX(), item.getY(), item.getZ(), rest));
    }

    // ---- crushing and smelting ----

    /** Whether this stream crushes or smelts what it frees or moves. */
    private boolean processes() {
        return stream.has(Lens.SMELT) || stream.has(Lens.CRUSH);
    }

    /** What one of {@code stack} becomes with this stream's Crushing and Smelting lenses: nothing, in the mirror. */
    private Processing.Made made(ServerLevel level, ItemStack stack) {
        if (!processes() || stack.isEmpty() || mirrored()) return new Processing.Made(List.of(stack.copyWithCount(1)), 0);
        return Processing.ofOne(level, key.node(), stack, stream.steps());
    }

    /**
     * What Crushing and Smelting lenses make of {@code stack} on its way: one, then the other, in their
     * lenses' order (see {@link Processing}), in stacks no bigger than they may be. What neither changes comes as it is, the same stack.
     */
    private List<ItemStack> processed(ServerLevel level, ItemStack stack) {
        Processing.Made made = made(level, stack);
        return made.changed() ? Processing.times(made.ofOne(), stack.getCount()) : List.of(stack);
    }

    /** Whether Smelting or Crushing alone could change the block at {@code pos}, as its item: one it may break, that its filter lets. */
    private boolean processable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null || state.is(ModTags.LASER_UNBREAKABLE)) return false;
        ItemStack as = new ItemStack(state.getBlock().asItem());
        return !as.isEmpty() && lets(as) && processCost(level, as) > 0;
    }

    /** FE for crushing and smelting {@code stack}: nothing when it comes as it is. */
    private long processCost(ServerLevel level, ItemStack stack) {
        return made(level, stack).fe() * stack.getCount();
    }

    /** {@code count} of what {@code one} is, a stack's worth at most. */
    private static ItemStack times(ItemStack one, int count) {
        return one.copyWithCount(Math.min(one.getMaxStackSize(), one.getCount() * count));
    }

    /**
     * Smelting or Crushing alone: what lies in the beam is done where it lies, a stack at a time; else the
     * block the beam ends on, as its item would be. A block it becomes goes in its place (sand becomes glass,
     * and the light goes on through; stone becomes cobblestone, then gravel, then sand); anything else drops
     * there (an ore's ingot, or its dust).
     */
    private void processAt(ServerLevel level, LaserNetworks networks) {
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }
        if (beam == null || area != null || !networks.canPay(this, Math.min(LaserTuning.SMELT_FE_PER_ITEM, LaserTuning.CRUSH_FE_PER_ITEM))) return;
        if (!processLying(level, networks) && !processBlock(level, networks)) return;
        networks.workDone(this);
        placeCooldown = LaserTuning.PLACE_COOLDOWN / stream.pace();
        lastWorked = now;
    }

    private boolean processLying(ServerLevel level, LaserNetworks networks) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box(), item -> item.isAlive() && onBeam(item.getBoundingBox(), 0.45)
                && !com.kadikular.quantimium.event.MirrorPhaseInteractions.mirrorOnly(item) && lets(item.getItem()))) {
            long cost = processCost(level, item.getItem());
            if (cost == 0 || !networks.pay(this, cost)) continue;
            replaceWith(level, item, processed(level, item.getItem()));
            return true;
        }
        return false;
    }

    private boolean processBlock(ServerLevel level, LaserNetworks networks) {
        BlockPos pos = null;
        // Phantom light goes through what it can't change, to the first block on its way it can.
        if ((marks & Light.PHANTOM) != 0) {
            for (BlockPos on : beam.path()) {
                if (level.isLoaded(on) && processable(level, on)) {
                    pos = on;
                    break;
                }
            }
        }
        if (pos == null) {
            if (beam.hit() == null || (beam.end() != LaserBeam.End.BLOCKED && beam.end() != LaserBeam.End.SINK)) return false;
            // As Harvest picks: something light passes just before the end (a glass block, a crop), else the end.
            BlockPos before = beam.hit().relative(beam.face().getOpposite());
            pos = beam.length() > 0 && !level.getBlockState(before).isAir() && processable(level, before) ? before : beam.hit();
            if (!processable(level, pos)) return false;
        }
        BlockState state = level.getBlockState(pos);
        ItemStack as = new ItemStack(state.getBlock().asItem());
        long cost = processCost(level, as);
        if (!networks.pay(this, cost)) return false;
        List<ItemStack> out = processed(level, as);
        ItemStack first = out.getFirst();
        if (out.size() == 1 && first.getCount() == 1 && first.getItem() instanceof net.minecraft.world.item.BlockItem block) {
            level.setBlockAndUpdate(pos, block.getBlock().defaultBlockState());
        } else {
            level.removeBlock(pos, false);
            for (ItemStack each : out) Block.popResource(level, pos, each);
        }
        level.playSound(null, pos, stream.has(Lens.SMELT) ? net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH : net.minecraft.sounds.SoundEvents.GRINDSTONE_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.6f);
        LaserNetworks.changed(level);
        return true;
    }


    /** The cracks it draws on the block it's cutting: one set per action. */
    private int crackId() {
        return -1 - (key.hashCode() & 0x3FFFFFFF);
    }

    /** Whether the area prism can see {@code pos}: a clear line of light from it, ending there, within its light's reach. */
    private boolean inSight(ServerLevel level, BlockPos pos) {
        // A block it has just seen stays seen a little while: the block it's cutting is looked at every tick.
        if (pos.equals(seen) && now < seenUntil) return true;
        LaserBeam line = LaserBeam.traceTo(level, key.node(), pos, reach, marks);
        boolean sees = pos.equals(line.hit());
        if (sees) {
            seen = pos.immutable();
            seenUntil = now + LaserTuning.SIGHT_KEPT;
        }
        return sees;
    }

    /** For tests: what cutting the block it's on costs in all; for Phantom upkeep, what this tick's costs. */
    long cutCost() {
        return cutCost;
    }

    /** For an area prism's Phantom light: what working on {@code pos} costs for the blocks it goes through to get there. */
    private long throughCost(ServerLevel level, BlockPos pos) {
        if (area == null || (marks & Light.PHANTOM) == 0) return 0;
        return (long) LaserBeam.traceTo(level, key.node(), pos, reach, marks).solidsThrough(level) * com.kadikular.quantimium.Config.phantomFePerBlock();
    }

    /** Exactly where in what it's working on the beam goes: the middle of what it's hitting, or of the block. */
    @Nullable
    net.minecraft.world.phys.Vec3 workingPoint(ServerLevel level) {
        BlockPos block = working(level);
        if (block == null) return null;
        if (key.action() == Lens.DAMAGE && !targets.isEmpty()) return targets.getFirst().getBoundingBox().getCenter();
        if (key.action() == Lens.INTERACT && touched != null && touched.isAlive()) return touched.getBoundingBox().getCenter();
        return block.getCenter();
    }

    /** What an area prism's action is working on just now, for the beam it shows: or null. */
    @Nullable
    BlockPos working(ServerLevel level) {
        // Only while it's working: not while it waits for room, or for anything to do.
        if (area == null || level.getGameTime() - lastWorked > LaserTuning.PLACE_COOLDOWN) return null;
        return switch (key.action()) {
            case HARVEST -> cutting;
            case PLACE, INSERT, EXTRACT -> placed;
            case INTERACT -> touched != null && touched.isAlive() ? touched.blockPosition() : placed;
            // Where what it's hitting is now, so the beam follows it.
            case DAMAGE -> targets.isEmpty() ? null : BlockPos.containing(targets.getFirst().getBoundingBox().getCenter());
            default -> null;
        };
    }

    /** Round the beam, or the area, to look for things in it. */
    private AABB box() {
        if (area != null) return AABB.of(area);
        Vec3[] ends = beam.ends();
        return new AABB(ends[0], ends[1]).inflate(1.0);
    }

    /** Whether a box comes within {@code slack} of the beam's line, or for an area, is in it. */
    private boolean onBeam(AABB box, double slack) {
        if (area != null) return box.intersects(AABB.of(area)) && within.test(BlockPos.containing(box.getCenter())) && sightLevel != null
                && inSight(sightLevel, BlockPos.containing(box.getCenter()));
        Vec3[] ends = beam.ends();
        AABB near = box.inflate(slack);
        return near.clip(ends[0], ends[1]).isPresent() || near.contains(ends[0]) || near.contains(ends[1]);
    }

    /** What this stream is and who pays: for the Spectroscope. */
    Light.Stream stream() {
        return stream;
    }

    List<BlockPos> collectors() {
        return collectors;
    }
}
