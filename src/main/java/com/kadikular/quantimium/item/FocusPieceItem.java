package com.kadikular.quantimium.item;

import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity;
import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Core;
import com.kadikular.quantimium.block.entity.LaserFocusBlockEntity.Part;
import com.kadikular.quantimium.init.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * A piece of a Laser Focus block: a focus (Laser Focus, Clear Laser Focus) or an attachment (Stand, Emitter, Cover).
 * Used on a Laser Focus block, it goes on: a focus into its middle, an attachment onto the face clicked. Used anywhere
 * else, it puts down a Laser Focus block holding it, an attachment on the side against the block clicked. Like a part
 * on a cable bus, an attachment does nothing until there's a focus in its block.
 */
public class FocusPieceItem extends Item {

    @Nullable
    private final Core core;
    @Nullable
    private final Part part;

    private FocusPieceItem(Properties properties, @Nullable Core core, @Nullable Part part) {
        super(properties);
        this.core = core;
        this.part = part;
    }

    public static FocusPieceItem focus(Properties properties, Core core) {
        return new FocusPieceItem(properties, core, null);
    }

    public static FocusPieceItem attachment(Properties properties, Part part) {
        return new FocusPieceItem(properties, null, part);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        // Onto a Laser Focus block: into its middle, or onto the face clicked.
        if (level.getBlockEntity(context.getClickedPos()) instanceof LaserFocusBlockEntity focus && fits(focus, face)) {
            if (!level.isClientSide()) {
                put(focus, face);
                used(context, context.getClickedPos());
            }
            return InteractionResult.SUCCESS;
        }
        // Against a block with a Laser Focus block already beside it: onto that one's face against it.
        BlockPos beside = context.getClickedPos().relative(face);
        if (level.getBlockEntity(beside) instanceof LaserFocusBlockEntity next) {
            if (!fits(next, face.getOpposite())) return InteractionResult.FAIL;
            if (!level.isClientSide()) {
                put(next, face.getOpposite());
                used(context, beside);
            }
            return InteractionResult.SUCCESS;
        }
        // Anywhere else: a new one, held against the block clicked.
        BlockPlaceContext place = new BlockPlaceContext(context);
        if (!place.canPlace()) return InteractionResult.FAIL;
        BlockPos at = place.getClickedPos();
        BlockState state = ModBlocks.LASER_FOCUS.get().defaultBlockState();
        Player player = context.getPlayer();
        if (!level.isUnobstructed(state, at, player == null ? CollisionContext.empty() : CollisionContext.of(player))) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            if (!level.setBlock(at, state, Block.UPDATE_ALL)) return InteractionResult.FAIL;
            if (!(level.getBlockEntity(at) instanceof LaserFocusBlockEntity focus)) return InteractionResult.FAIL;
            put(focus, face.getOpposite());
            used(context, at);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this).toLanguageKey("item") + ".tip";
        Object loss = new java.text.DecimalFormat("0.##").format(com.kadikular.quantimium.Config.relayLoss() * 100);
        tooltip.accept(Component.translatable(key, loss).withStyle(ChatFormatting.GRAY));
    }

    /** Whether this goes onto {@code focus}: a focus into an empty middle, an attachment onto an empty face. */
    private boolean fits(LaserFocusBlockEntity focus, Direction face) {
        return core != null ? focus.core() == Core.NONE : focus.part(face) == Part.NONE;
    }

    private void put(LaserFocusBlockEntity focus, Direction face) {
        if (core != null) focus.setCore(core);
        else if (part != null) focus.setPart(face, part);
    }

    private static void used(UseOnContext context, BlockPos at) {
        context.getLevel().playSound(null, at, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        Player player = context.getPlayer();
        if (player == null || !player.getAbilities().instabuild) context.getItemInHand().shrink(1);
    }
}
