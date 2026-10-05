package com.kadikular.quantimium.event;

import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.QuantimiumAdvancements;
import com.kadikular.quantimium.entity.MirrorEndermite;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.init.ModTags;
import com.kadikular.quantimium.phase.MirrorPhase;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Prevents a phased player from changing or operating the backing world. */
@EventBusSubscriber(modid = Quantimium.MODID)
public final class MirrorPhaseInteractions {

    private static final String MIRROR_DROP = "quantimium:mirror_drop";
    private static final String MIRROR_SHOT = "quantimium:mirror_shot";

    private MirrorPhaseInteractions() {}

    /** Anomalite and Unrealised Ore stay interactive in the mirror; everything else is scenery. */
    private static boolean harvestableInMirror(BlockState state) {
        return state.is(ModTags.ANOMALITE) || state.is(ModBlocks.UNREALISED_ORE.get());
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (event.getState().is(ModTags.ANOMALITE)) {
            if (!MirrorPhase.canHarvestOverlay(event.getPlayer())) event.setCanceled(true);
            return;
        }
        if (event.getPlayer() instanceof ServerPlayer player
                && MirrorPhase.isPhased(player)
                && !harvestableInMirror(event.getState())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && MirrorPhase.isPhased(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player
                && MirrorPhase.isPhased(player)
                && !harvestableInMirror(event.getLevel().getBlockState(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !MirrorPhase.isPhased(player)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (harvestableInMirror(state)) return;
        // Doors, gates, buttons and levers still work from the mirror, on the real world; nothing in
        // hand is used on them.
        if (state.is(ModTags.MIRROR_USABLE)) {
            event.setUseItem(TriState.FALSE);
            return;
        }
        event.setCanceled(true);
    }

    /** A mite killed in the mirror leaves what it drops in the mirror, for whoever killed it. */
    @SubscribeEvent
    public static void onLivingDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof MirrorEndermite)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || !MirrorPhase.isPhased(killer)) return;
        for (ItemEntity drop : event.getDrops()) markMirrorDrop(drop, killer);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getTarget() instanceof MirrorEndermite
                || MirrorPhase.isPhased(player)
                || event.getTarget() instanceof Player target && MirrorPhase.isPhased(target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getTarget() instanceof MirrorEndermite
                || MirrorPhase.isPhased(player)
                || event.getTarget() instanceof Player target && MirrorPhase.isPhased(target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean mite = event.getTarget() instanceof MirrorEndermite;
        boolean breached = event.getTarget() instanceof MirrorEndermite endermite && endermite.isBreached();
        boolean overlayPeer = isPhasedPlayer(event.getTarget());
        if (MirrorPhase.isPhased(player)) {
            if (!mite && !overlayPeer) event.setCanceled(true);
            return;
        }
        // A breached mite is in the real world and can be fought from it.
        if ((mite && !breached) || overlayPeer) event.setCanceled(true);
    }

    /**
     * Do not let inventory items become stranded in the backing world. ItemTossEvent fires after
     * removal from the inventory, so cancellation must also return the stack.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!MirrorPhase.isPhased(event.getPlayer())) return;
        ItemEntity tossed = event.getEntity();
        event.getPlayer().getInventory().add(tossed.getItem());
        event.setCanceled(true);
    }

    /** Lets a phased player pick up something the mirror handed them, and nobody else. */
    public static void markMirrorDrop(ItemEntity drop, ServerPlayer owner) {
        drop.setTarget(owner.getUUID());
        drop.getPersistentData().putBoolean(MIRROR_DROP, true);
    }

    /** Marks mirror-harvest drops as owned by the phased breaker. */
    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)
                || !MirrorPhase.isPhased(player)
                || !harvestableInMirror(event.getState())) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            drop.setTarget(player.getUUID());
            drop.getPersistentData().putBoolean(MIRROR_DROP, true);
        }
    }

    /**
     * Loose backing-world matter remains isolated; owned mirror harvest is collectable. So is a Sophon: a
     * folded body belongs to both worlds, and the Veiled drops them where only the mirror can see it.
     */
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!MirrorPhase.isPhased(event.getPlayer())) return;
        ItemEntity item = event.getItemEntity();
        if (item.getItem().has(ModDataComponents.SOPHON.get())) {
            event.setCanPickup(TriState.TRUE);
            return;
        }
        boolean ownedMirrorDrop = item.getPersistentData().getBooleanOr(MIRROR_DROP, false)
                && event.getPlayer().getUUID().equals(item.getTarget());
        event.setCanPickup(ownedMirrorDrop ? TriState.TRUE : TriState.FALSE);
    }

    @SubscribeEvent
    public static void onXpPickup(PlayerXpEvent.PickupXp event) {
        if (MirrorPhase.isPhased(event.getEntity())) event.setCanceled(true);
    }

    /**
     * Mirror mites hunt phased players only. Everything else still treats a phased player as
     * not present, and overworld mobs never pick a mite as a target.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity aboutTo = event.getNewAboutToBeSetTarget();
        if (aboutTo == null) return;
        LivingEntity mob = event.getEntity();
        if (aboutTo instanceof MirrorEndermite) {
            if (!(mob instanceof MirrorEndermite)) event.setCanceled(true);
            return;
        }
        if (mob instanceof MirrorEndermite endermite) {
            if (!(aboutTo instanceof Player player) || !endermite.realTo(player)) {
                event.setCanceled(true);
            }
            return;
        }
        if (aboutTo instanceof Player target && MirrorPhase.isPhased(target)) {
            event.setCanceled(true);
        }
    }

    /**
     * Environmental damage still applies (shared terrain). Overlay players can fight each other
     * and mites; backing-world attackers cannot reach them. Unphased players cannot injure mites.
     * Shots from a phased player only exist in the overlay, so they cannot cheese backing-world mobs.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        boolean fromPhased = fromPhasedPlayer(source);

        if (victim instanceof MirrorEndermite endermite) {
            if (fromPhased || endermite.isBreached()) return;
            if (attacker != null) event.setCanceled(true);
            return;
        }
        if (victim instanceof Player player && MirrorPhase.isPhased(player)) {
            if (attacker instanceof MirrorEndermite) {
                if (player instanceof ServerPlayer serverPlayer) {
                    QuantimiumAdvancements.award(serverPlayer, "these_locals_arent_friendly", "mite_hit");
                }
                return;
            }
            if (fromPhased) return;
            if (attacker != null) event.setCanceled(true);
            return;
        }
        boolean mirrorMite = attacker instanceof MirrorEndermite endermite && !endermite.isBreached();
        if (fromPhased || mirrorMite) event.setCanceled(true);
    }

    /** Fired arrows, tridents, and similar keep a mirror tag even if the owner is cleared later. */
    @SubscribeEvent
    public static void onProjectileJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof Projectile projectile && isPhasedPlayer(projectile.getOwner())) {
            projectile.getPersistentData().putBoolean(MIRROR_SHOT, true);
        }
    }

    /** Overlay shots pass through backing-world creatures; mites and overlay players still catch them. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!isMirrorShot(projectile)) return;
        if (projectile instanceof AbstractThrownPotion) {
            event.setCanceled(true);
            projectile.discard();
            return;
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit
                && (hit.getEntity() instanceof MirrorEndermite || isPhasedPlayer(hit.getEntity()))) {
            return;
        }
        event.setCanceled(true);
    }

    /** Crossbow fireworks and similar must not blast backing-world mobs from the overlay. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        var explosion = event.getExplosion();
        Entity direct = explosion.getDirectSourceEntity();
        Entity indirect = explosion.getIndirectSourceEntity();
        boolean mirror = isPhasedPlayer(direct)
                || isPhasedPlayer(indirect)
                || (direct != null && direct.getPersistentData().getBooleanOr(MIRROR_SHOT, false));
        if (!mirror) return;
        event.getAffectedEntities().removeIf(entity ->
                !(entity instanceof MirrorEndermite) && !isPhasedPlayer(entity));
        event.getAffectedBlocks().clear();
    }

    private static boolean fromPhasedPlayer(DamageSource source) {
        if (isPhasedPlayer(source.getEntity()) || isPhasedPlayer(source.getDirectEntity())) return true;
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile projectile && isMirrorShot(projectile)) return true;
        return direct != null && direct.getPersistentData().getBooleanOr(MIRROR_SHOT, false);
    }

    private static boolean isMirrorShot(Projectile projectile) {
        return projectile.getPersistentData().getBooleanOr(MIRROR_SHOT, false) || isPhasedPlayer(projectile.getOwner());
    }

    private static boolean isPhasedPlayer(Entity entity) {
        return entity instanceof Player player && MirrorPhase.isPhased(player);
    }
}
