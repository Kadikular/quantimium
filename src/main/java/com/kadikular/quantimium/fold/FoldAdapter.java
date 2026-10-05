package com.kadikular.quantimium.fold;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * What the Fold Chamber needs to know about one mod's multiblock. The chamber knows volumes; only the
 * machine's own mod knows whether it is formed, idle and empty, and what about it is worth keeping.
 *
 * <p>One per kind of controller. A bound Tesseract names the controller, and the adapter that
 * {@linkplain #handles handles} it answers for the whole structure.
 */
public interface FoldAdapter {

    Identifier id();

    /** Whether {@code controller} is a controller this adapter knows. */
    boolean handles(BlockState controller);

    /**
     * Why the machine can't fold as it stands, or empty when it can: not formed, mid-recipe, holding
     * items, fluid or energy. The chamber checks the rest of the volume itself.
     */
    Optional<Component> refusal(ServerLevel level, BlockPos controller);

    /**
     * Whether {@code pos} is one of the machine's own blocks. The chamber leaves those to
     * {@link #refusal}, since parts often stand in for the controller's inventory and energy.
     */
    boolean ownsPart(ServerLevel level, BlockPos controller, BlockPos pos);

    /** What about the machine to keep, read just before it folds: its tier, its arms, its coils. */
    CompoundTag capture(ServerLevel level, BlockPos controller);

    /** Puts the machine back together after its blocks are placed, so nothing remembers old positions. */
    void reform(ServerLevel level, BlockPos controller, CompoundTag state);

    /** The machine as one line, for the Folded Tesseract's tooltip: "Quantum Foundry (4 arms)". */
    Component describe(CompoundTag state);

    /** What the Quantum Crafter treats the folded machine as, for its whitelist, blacklist and band. */
    ItemStack catalyst(CompoundTag state);

    /** The recipe types the folded machine can run in a Quantum Crafter. */
    List<RecipeType<?>> recipeTypes(CompoundTag state);
}
