package com.kadikular.quantimium.item;

import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import com.kadikular.quantimium.block.entity.QuantumFoundryPartBlockEntity;
import com.kadikular.quantimium.flux.FieldTrend;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Reads flux and anomaly in the pointed (or player's) chunk. Used on a block that has something to
 * say ({@link FluxMeterReadout}: machines, the rift array, crystals, the detector), adds its line.
 * Action bar only — no chat.
 */
public class FluxMeterItem extends Item {

    public FluxMeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!context.getLevel().isClientSide()) {
            report(player, context.getLevel(), context.getClickedPos(),
                    context.getLevel().getBlockEntity(context.getClickedPos()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            report(player, level, player.blockPosition(), null);
        }
        return InteractionResult.SUCCESS;
    }

    private static void report(Player player, Level level, BlockPos pos, @Nullable BlockEntity be) {
        MutableComponent line = reading(QuantumFlux.sample(level, pos), QuantumFlux.chunkSettling(level, pos))
                .withStyle(ChatFormatting.AQUA);
        if (QuantumFlux.chunkShielded(level, pos)) {
            // How full the containment over this chunk is; past 100% the overflow becomes anomaly.
            line.append(Component.translatable("item.quantimium.flux_meter.reading.load",
                    Math.round(QuantumFlux.chunkLoad(level, pos) * 100.0)));
        }
        MutableComponent machine = machineLine(be);
        if (machine != null) {
            line.append(Component.literal("  ·  "));
            line.append(machine);
        }
        player.sendOverlayMessage(line);
    }

    private static MutableComponent reading(QuantumFlux.Neighbourhood field, double settling) {
        return Component.translatable(
                "item.quantimium.flux_meter.reading.here",
                FluxMeterReadout.flux(field.flux()),
                FieldTrend.describe(field.flux(), settling),
                FluxMeterReadout.flux(field.anomaly()),
                Component.translatable("flux.quantimium.band." + field.anomalyBand().getSerializedName()));
    }

    /** What the block has to say for itself; a Foundry part answers for its controller. */
    @Nullable
    private static MutableComponent machineLine(@Nullable BlockEntity be) {
        if (be instanceof QuantumFoundryPartBlockEntity part && part.host() instanceof BlockEntity host) {
            be = host;
        }
        return be instanceof FluxMeterReadout readout ? readout.fluxMeterLine() : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tip, TooltipFlag flag) {
        tip.accept(Component.translatable("item.quantimium.flux_meter.tooltip")
                .withStyle(ChatFormatting.GRAY));
        tip.accept(Component.translatable("item.quantimium.flux_meter.tooltip.machine")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
