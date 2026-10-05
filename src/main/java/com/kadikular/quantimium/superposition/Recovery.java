package com.kadikular.quantimium.superposition;

import com.kadikular.quantimium.block.entity.SuperpositionPodBlockEntity;
import com.kadikular.quantimium.entity.FieldDouble;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.item.SophonBinding;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Recovery module: brings a lost double home to its pod from wherever it is. A field double still
 * standing is folded up where it stands; a Sophon lying where a double was knocked out is picked up from
 * the ground. Either unfolds in the pod over {@link Relay#FORMING_TICKS}, for what a swap that far costs.
 * A Sophon somebody has picked up is out of its reach: it is theirs to give back.
 *
 * <p>It works from the pod's screen, and by itself: when one of your doubles is knocked out, the first
 * of your empty Recovery pods that can pays to bring it back.
 */
public final class Recovery {

    private Recovery() {}

    /** From a pod's screen: bring {@code sophon} back into the pod at {@code podPos}. */
    public static void recover(ServerPlayer player, BlockPos podPos, UUID sophon) {
        if (!(player.level().getBlockEntity(podPos) instanceof SuperpositionPodBlockEntity pod)) return;
        SophonRegistry.Entry entry = SophonRegistry.get(player.level().getServer()).entry(sophon);
        String refusal = !pod.canUse(player) ? "not_your_pod"
                : entry == null || !entry.owner().equals(player.getUUID()) ? "no_double"
                : into(player.level().getServer(), pod, sophon);
        if (refusal != null) {
            player.sendOverlayMessage(Component.translatable("message.quantimium.pod." + refusal)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.sendOverlayMessage(Component.translatable("message.quantimium.recovery.recovered", pod.displayName())
                .withStyle(ChatFormatting.AQUA));
        player.closeContainer();
    }

    /**
     * One of {@code owner}'s doubles was just knocked out: the first of their Recovery pods that can
     * (whole, empty, powered, and not {@code except}, the pod it was knocked out of) brings it back.
     */
    public static void afterKnockOut(MinecraftServer server, UUID owner, UUID sophon, @Nullable GlobalPos except) {
        for (GlobalPos where : SophonRegistry.get(server).recoveryPods(owner)) {
            if (where.equals(except)) continue;
            SuperpositionPodBlockEntity pod = Relay.podAt(server, where);
            if (pod == null || into(server, pod, sophon) != null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) player.sendSystemMessage(Component.translatable("message.quantimium.recovery.auto",
                    pod.displayName()).withStyle(ChatFormatting.AQUA));
            return;
        }
    }

    /** Brings {@code sophon}'s double into {@code pod}; the reason it could not, or null once it has. */
    @Nullable
    static String into(MinecraftServer server, SuperpositionPodBlockEntity pod, UUID sophon) {
        if (!(pod.getLevel() instanceof ServerLevel home)) return "no_double";
        if (!pod.isFormed() || !pod.hasModule(PodModule.RECOVERY)) return "no_recovery";
        if (pod.hasDouble()) return "occupied";
        if (!home.getEntitiesOfClass(Player.class, pod.interior()).isEmpty()) return "standing";
        SophonRegistry registry = SophonRegistry.get(server);
        SophonRegistry.Entry entry = registry.entry(sophon);
        if (entry != null && entry.taken()) return "taken";
        if (entry == null || (!entry.inField() && !entry.dropped())) return "not_lost";
        if (pod.owner() != null && !pod.owner().equals(entry.owner())) return "not_your_pod";
        GlobalPos where = entry.pod().get();
        ServerLevel there = server.getLevel(where.dimension());
        if (there == null) return "no_double";
        there.getChunk(where.pos());
        int cost = Superposition.cost(pod.globalPos(), where);
        if (pod.getEnergyStorage().getEnergyStored() < cost) return "no_power";

        if (entry.inField()) {
            Entity found = entry.entity().map(there::getEntity).orElse(null);
            if (!(found instanceof FieldDouble fieldDouble)) return "not_reachable";
            fieldDouble.discard();
        } else {
            ItemEntity lying = lying(there, where.pos(), sophon);
            if (lying == null) {
                // Not where it fell: somebody has it now.
                registry.folded(sophon);
                return "stolen";
            }
            // Emptied first, so the Sophon is not taken for lost as the item leaves the world.
            lying.setItem(ItemStack.EMPTY);
            lying.discard();
        }
        pod.getEnergyStorage().consume(cost);
        pod.receiveDouble(sophon);
        registry.placed(sophon, pod.globalPos(), pod.displayName().getString(), pod.modules(), pod.isListed());
        Superposition.podEffect(there, where.pos(), Superposition.EFFECT_FOLD);
        Superposition.podEffect(home, pod.getBlockPos(), Superposition.EFFECT_FORMING);
        return null;
    }

    @Nullable
    private static ItemEntity lying(ServerLevel level, BlockPos near, UUID sophon) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(near).inflate(4), item -> {
            SophonBinding binding = item.getItem().get(ModDataComponents.SOPHON.get());
            return binding != null && binding.id().equals(sophon);
        });
        return items.isEmpty() ? null : items.getFirst();
    }
}
