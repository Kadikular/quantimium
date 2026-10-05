package com.kadikular.quantimium.superposition;

import com.kadikular.quantimium.init.ModAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Every Sophon in the world and where its double is: folded up (an item somewhere) or waiting in a
 * pod. Kept on the Overworld, so a pod's screen can list doubles in chunks and dimensions that are
 * not loaded, and so the doubles cap counts Sophons nobody can currently see.
 *
 * <p>A pod keeps its entry's name and modules up to date whenever they change, which is what the
 * list and the rescue read, rather than loading the pod to ask it.
 */
public final class SophonRegistry {

    /** Where a double is, besides folded up and carried. */
    public static final int IN_POD = 0;
    /** Standing out in the world, where its owner tethered away from: a field double. */
    public static final int IN_FIELD = 1;
    /** Knocked out: its Sophon lies on the ground where it fell. */
    public static final int DROPPED = 2;
    /** Taken by the Veiled, which carries its Sophon until it is driven off or held. */
    public static final int TAKEN = 3;

    /**
     * One Sophon: whose it is, and where its double is. {@code pod} is where: the pod it waits in, the
     * spot a field double stands on, or where its Sophon was knocked out ({@code place} says which);
     * empty while it is folded up and carried. {@code entity} is a field double's.
     */
    public record Entry(UUID owner, Optional<GlobalPos> pod, String podName, int modules, boolean listed, int place,
                        Optional<UUID> entity) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                GlobalPos.CODEC.optionalFieldOf("pod").forGetter(Entry::pod),
                Codec.STRING.optionalFieldOf("pod_name", "").forGetter(Entry::podName),
                Codec.INT.optionalFieldOf("modules", 0).forGetter(Entry::modules),
                Codec.BOOL.optionalFieldOf("listed", true).forGetter(Entry::listed),
                Codec.INT.optionalFieldOf("place", IN_POD).forGetter(Entry::place),
                UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(Entry::entity)
        ).apply(instance, Entry::new));

        public boolean inPod() {
            return pod.isPresent() && place == IN_POD;
        }

        public boolean inField() {
            return pod.isPresent() && place == IN_FIELD;
        }

        public boolean dropped() {
            return pod.isPresent() && place == DROPPED;
        }

        public boolean taken() {
            return pod.isPresent() && place == TAKEN;
        }

        public boolean hasModule(PodModule module) {
            return (modules & module.bit()) != 0;
        }

        Entry folded() {
            return new Entry(owner, Optional.empty(), "", 0, true, IN_POD, Optional.empty());
        }

        Entry at(GlobalPos where, String name, int moduleMask, boolean isListed) {
            return new Entry(owner, Optional.of(where), name, moduleMask, isListed, IN_POD, Optional.empty());
        }
    }

    /** A Sophon with its id, for listing. */
    public record Sophon(UUID id, Entry entry) {}

    private record Stored(UUID id, Entry entry) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Stored::id),
                Entry.CODEC.fieldOf("entry").forGetter(Stored::entry)
        ).apply(instance, Stored::new));
    }

    private record Anchor(UUID owner, GlobalPos pod) {
        static final Codec<Anchor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Anchor::owner),
                GlobalPos.CODEC.fieldOf("pod").forGetter(Anchor::pod)
        ).apply(instance, Anchor::new));
    }

    private record Recovery(UUID owner, List<GlobalPos> pods) {
        static final Codec<Recovery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Recovery::owner),
                GlobalPos.CODEC.listOf().fieldOf("pods").forGetter(Recovery::pods)
        ).apply(instance, Recovery::new));
    }

    private record Stash(UUID owner, List<ItemStack> items) {
        static final Codec<Stash> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Stash::owner),
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(Stash::items)
        ).apply(instance, Stash::new));
    }

    public static final Codec<SophonRegistry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Stored.CODEC.listOf().fieldOf("sophons").forGetter(SophonRegistry::stored),
            Anchor.CODEC.listOf().fieldOf("anchors").forGetter(SophonRegistry::storedAnchors),
            Stash.CODEC.listOf().optionalFieldOf("stashes", List.of()).forGetter(SophonRegistry::storedStashes),
            Recovery.CODEC.listOf().optionalFieldOf("recovery", List.of()).forGetter(SophonRegistry::storedRecovery)
    ).apply(instance, SophonRegistry::new));

    /** Slots in a player's stash: a chest's worth. */
    public static final int STASH_SLOTS = 27;

    private final Map<UUID, Entry> sophons = new HashMap<>();
    private final Map<UUID, GlobalPos> anchors = new HashMap<>();
    private final Map<UUID, NonNullList<ItemStack>> stashes = new HashMap<>();
    /** Each player's pods with a Recovery module, which pull a knocked-out double back by themselves. */
    private final Map<UUID, Set<GlobalPos>> recovery = new HashMap<>();

    public SophonRegistry() {}

    private SophonRegistry(List<Stored> stored, List<Anchor> anchorList, List<Stash> stashList, List<Recovery> recoveryList) {
        for (Recovery pods : recoveryList) recovery.put(pods.owner(), new HashSet<>(pods.pods()));
        for (Stored entry : stored) sophons.put(entry.id(), entry.entry());
        for (Anchor anchor : anchorList) anchors.put(anchor.owner(), anchor.pod());
        for (Stash stash : stashList) {
            NonNullList<ItemStack> items = NonNullList.withSize(STASH_SLOTS, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(STASH_SLOTS, stash.items().size()); i++) items.set(i, stash.items().get(i));
            stashes.put(stash.owner(), items);
        }
    }

    public static SophonRegistry get(MinecraftServer server) {
        return server.overworld().getData(ModAttachments.SOPHONS);
    }

    public boolean isEmpty() {
        return sophons.isEmpty() && anchors.isEmpty() && stashes.isEmpty() && recovery.isEmpty();
    }

    private List<Stored> stored() {
        return sophons.entrySet().stream().map(e -> new Stored(e.getKey(), e.getValue())).toList();
    }

    private List<Anchor> storedAnchors() {
        return anchors.entrySet().stream().map(e -> new Anchor(e.getKey(), e.getValue())).toList();
    }

    private List<Recovery> storedRecovery() {
        return recovery.entrySet().stream().map(e -> new Recovery(e.getKey(), List.copyOf(e.getValue()))).toList();
    }

    private List<Stash> storedStashes() {
        return stashes.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(stack -> !stack.isEmpty()))
                .map(e -> new Stash(e.getKey(), List.copyOf(e.getValue()))).toList();
    }

    // ---- Sophons ----

    /** A new Sophon for {@code owner}, folded. */
    public UUID create(UUID owner) {
        UUID id = UUID.randomUUID();
        sophons.put(id, new Entry(owner, Optional.empty(), "", 0, true, IN_POD, Optional.empty()));
        return id;
    }

    @Nullable
    public Entry entry(UUID id) {
        return sophons.get(id);
    }

    public int count(UUID owner) {
        int count = 0;
        for (Entry entry : sophons.values()) if (entry.owner().equals(owner)) count++;
        return count;
    }

    /** {@code owner}'s Sophons, those in pods first, then by pod name. */
    public List<Sophon> of(UUID owner) {
        List<Sophon> list = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : sophons.entrySet()) {
            if (entry.getValue().owner().equals(owner)) list.add(new Sophon(entry.getKey(), entry.getValue()));
        }
        list.sort(Comparator.comparing((Sophon s) -> !s.entry().inPod()).thenComparing(s -> s.entry().podName()));
        return list;
    }

    /** The double now waits in {@code pod}. */
    public void placed(UUID id, GlobalPos pod, String podName, int modules, boolean listed) {
        Entry entry = sophons.get(id);
        if (entry != null) sophons.put(id, entry.at(pod, podName, modules, listed));
    }

    /** The double was folded back into its Sophon. */
    public void folded(UUID id) {
        Entry entry = sophons.get(id);
        if (entry != null) sophons.put(id, entry.folded());
    }

    /** The pod holding {@code id} was renamed, listed or unlisted, or its modules changed. */
    public void describe(UUID id, String podName, int modules, boolean listed) {
        Entry entry = sophons.get(id);
        if (entry != null && entry.inPod()) sophons.put(id, entry.at(entry.pod().get(), podName, modules, listed));
    }

    /** The double now stands out in the world at {@code where}, as the field double {@code entity}. */
    public void inField(UUID id, GlobalPos where, UUID entity) {
        Entry entry = sophons.get(id);
        if (entry != null) sophons.put(id, new Entry(entry.owner(), Optional.of(where), "", 0, true, IN_FIELD, Optional.of(entity)));
    }

    /** The Veiled {@code by} took the double at {@code where}, and carries its Sophon. */
    public void taken(UUID id, GlobalPos where, UUID by) {
        Entry entry = sophons.get(id);
        if (entry != null) sophons.put(id, new Entry(entry.owner(), Optional.of(where), "", 0, false, TAKEN, Optional.of(by)));
    }

    /**
     * Whether {@code entry}'s double is prey for the Veiled: standing in the field, or in a pod with no
     * Ward module. A double folded up, knocked out or already taken is not.
     */
    public static boolean unguarded(Entry entry) {
        return entry.inField() || (entry.inPod() && !entry.hasModule(PodModule.WARD));
    }

    /** Every Sophon, for the Veiled looking for prey. */
    public List<Sophon> all() {
        List<Sophon> list = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : sophons.entrySet()) list.add(new Sophon(entry.getKey(), entry.getValue()));
        return list;
    }

    /** The double was knocked out, and its Sophon lies on the ground at {@code where}. */
    public void dropped(UUID id, GlobalPos where) {
        Entry entry = sophons.get(id);
        if (entry != null) sophons.put(id, new Entry(entry.owner(), Optional.of(where), "", 0, false, DROPPED, Optional.empty()));
    }

    /** The Sophon is gone: spent on a rescue, or lost with its item. */
    public void remove(UUID id) {
        sophons.remove(id);
    }

    /** Forgets every Sophon {@code owner} has, for a player whose items were lost where nothing saw it. */
    public int forget(UUID owner) {
        int before = sophons.size();
        sophons.values().removeIf(entry -> entry.owner().equals(owner));
        anchors.remove(owner);
        return before - sophons.size();
    }

    // ---- Stashes ----

    /**
     * {@code owner}'s stash: one per player, whichever of their pods it is opened from. It is kept here,
     * out of phase, rather than in a pod, so it can be opened from anywhere without loading one.
     */
    public NonNullList<ItemStack> stash(UUID owner) {
        return stashes.computeIfAbsent(owner, key -> NonNullList.withSize(STASH_SLOTS, ItemStack.EMPTY));
    }

    /** Whether one of {@code owner}'s doubles waits in a pod with a Stash module, which is what opens it. */
    public boolean hasStash(UUID owner) {
        for (Entry entry : sophons.values()) {
            if (entry.owner().equals(owner) && entry.inPod() && entry.hasModule(PodModule.STASH)) return true;
        }
        return false;
    }

    // ---- Recovery ----

    /** Notes whether the pod at {@code pod}, {@code owner}'s, has a Recovery module. */
    public void recoveryPod(UUID owner, GlobalPos pod, boolean has) {
        Set<GlobalPos> pods = recovery.computeIfAbsent(owner, key -> new HashSet<>());
        if (has) pods.add(pod); else pods.remove(pod);
        if (pods.isEmpty()) recovery.remove(owner);
    }

    public Set<GlobalPos> recoveryPods(UUID owner) {
        return Set.copyOf(recovery.getOrDefault(owner, Set.of()));
    }

    // ---- Anchors ----

    @Nullable
    public GlobalPos anchor(UUID owner) {
        return anchors.get(owner);
    }

    public void setAnchor(UUID owner, @Nullable GlobalPos pod) {
        if (pod == null) {
            anchors.remove(owner);
        } else {
            anchors.put(owner, pod);
        }
    }
}
