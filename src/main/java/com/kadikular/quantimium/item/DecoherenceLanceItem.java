package com.kadikular.quantimium.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.InteractionResult;
import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.entity.Veiled;
import com.kadikular.quantimium.init.ModDataComponents;
import com.kadikular.quantimium.phase.FluxRift;
import com.kadikular.quantimium.phase.FluxRiftManager;
import com.kadikular.quantimium.phase.MirrorPhase;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Handheld decoherence: floods whatever it touches with environment until the superposition gives
 * and settles into one world. That closes a rift from its mirror side and unmakes a mite, and does
 * nothing at all to a cow — only things caught between realms have anything to lose.
 *
 * <p>Channelled like a bow, so it slows you while held. FE only for now; the beam and its inward-
 * drawn particles are drawn client-side from the same {@link LanceTargeting} the server uses.
 */
public class DecoherenceLanceItem extends Item {

    public static final int CAPACITY = 200_000;
    public static final int MAX_RECEIVE = 2_000;
    private static final int USE_TICKS = 72_000;
    /** Mites have ten ticks of hurt immunity anyway; pulsing matches it instead of wasting hits. */
    private static final int ENTITY_PULSE = 10;
    private static final float ENTITY_DAMAGE = 3.0f;
    /** Flux azure, like every other charge readout. */
    private static final int BAR_COLOUR = 0x3485FF;

    public DecoherenceLanceItem(Properties properties) {
        super(properties);
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ENERGY.get(), 0);
    }

    public static void setEnergy(ItemStack stack, int energy) {
        stack.set(ModDataComponents.ENERGY.get(), Mth.clamp(energy, 0, CAPACITY));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild && energy(stack) < Config.lanceFePerTick()) {
            if (level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.quantimium.lance.empty"));
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        if (level.isClientSide()) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.6f, false);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel serverLevel) || !(living instanceof ServerPlayer player)) return;
        int cost = Config.lanceFePerTick();
        if (!player.getAbilities().instabuild) {
            int stored = energy(stack);
            if (stored < cost) {
                player.stopUsingItem();
                return;
            }
            setEnergy(stack, stored - cost);
        }

        boolean phased = MirrorPhase.isPhased(player);
        LanceTargeting.Target target = LanceTargeting.find(player, 1.0f, spheres(serverLevel), phased);
        int held = USE_TICKS - remaining;
        switch (target.kind()) {
            case RIFT -> FluxRiftManager.drain(player, target.rift(), Config.lanceDrainPerTick());
            case SHADOW -> {
                if (held % 40 == 0) {
                    player.sendOverlayMessage(Component.translatable("message.quantimium.lance.shadow"));
                }
            }
            case ENTITY -> {
                // The Veiled is not hurt by it: every tick of contact freezes it and pins it.
                if (target.entity() instanceof Veiled veiled) {
                    veiled.lance(player);
                } else if (held % ENTITY_PULSE == 0 && target.entity() != null) {
                    target.entity().hurtServer(serverLevel, serverLevel.damageSources().indirectMagic(player, player), ENTITY_DAMAGE);
                }
            }
            case NONE -> {}
        }
    }

    private static List<LanceTargeting.RiftSphere> spheres(ServerLevel level) {
        List<LanceTargeting.RiftSphere> spheres = new ArrayList<>();
        for (FluxRift rift : FluxRiftManager.rifts(level)) {
            spheres.add(new LanceTargeting.RiftSphere(rift.id(), rift.centre(), FluxRift.hitRadius(rift.stage())));
        }
        return spheres;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity living, int remaining) {
        if (level.isClientSide()) {
            level.playLocalSound(living.getX(), living.getY(), living.getZ(),
                    SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.4f, 1.6f, false);
        }
        return false;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    /** Charge changes every tick of use; without this the held item would bob on every one. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * energy(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOUR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tip, TooltipFlag flag) {
        tip.accept(Component.translatable("item.quantimium.decoherence_lance.energy",
                String.format("%,d", energy(stack)), String.format("%,d", CAPACITY))
                .withStyle(ChatFormatting.GRAY));
        tip.accept(Component.translatable("item.quantimium.decoherence_lance.tooltip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
