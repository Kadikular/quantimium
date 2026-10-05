package com.kadikular.quantimium.fold;

import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.QuantumFoundryStructure;
import com.kadikular.quantimium.block.entity.QuantumFoundryBlockEntity;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModRecipeTypes;
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
 * Our own Quantum Foundry: the proof of concept. Its arms are what it keeps, since a recipe needs a
 * number of them. Its energy is spent, not refused: the Foundry only takes power in, so there would
 * be no way to empty it.
 */
public final class FoundryFoldAdapter implements FoldAdapter {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(Quantimium.MODID, "quantum_foundry");
    public static final String ARMS = "arms";

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean handles(BlockState controller) {
        return controller.is(ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get());
    }

    @Override
    public Optional<Component> refusal(ServerLevel level, BlockPos controller) {
        if (!(level.getBlockEntity(controller) instanceof QuantumFoundryBlockEntity foundry)) {
            return Optional.of(Component.translatable("message.quantimium.fold.not_formed"));
        }
        foundry.revalidateStructure();
        if (!foundry.isFormed()) return Optional.of(Component.translatable("message.quantimium.fold.not_formed"));
        if (foundry.getProgress() > 0) {
            int percent = foundry.getDuration() <= 0 ? 0 : foundry.getProgress() * 100 / foundry.getDuration();
            return Optional.of(Component.translatable("message.quantimium.fold.mid_recipe", percent));
        }
        int items = 0;
        for (int slot = 0; slot < foundry.getInventory().getSlots(); slot++) {
            items += foundry.getInventory().getStackInSlot(slot).getCount();
        }
        if (items > 0) return Optional.of(Component.translatable("message.quantimium.fold.controller_items", items));
        return Optional.empty();
    }

    @Override
    public boolean ownsPart(ServerLevel level, BlockPos controller, BlockPos pos) {
        return QuantumFoundryStructure.coversPart(level, controller, pos);
    }

    @Override
    public CompoundTag capture(ServerLevel level, BlockPos controller) {
        CompoundTag state = new CompoundTag();
        if (level.getBlockEntity(controller) instanceof QuantumFoundryBlockEntity foundry) {
            state.putInt(ARMS, Integer.bitCount(foundry.getPillarMask()));
            foundry.getEnergyStorage().setEnergy(0);
            foundry.setChanged();
        }
        return state;
    }

    @Override
    public void reform(ServerLevel level, BlockPos controller, CompoundTag state) {
        if (level.getBlockEntity(controller) instanceof QuantumFoundryBlockEntity foundry) foundry.reform();
    }

    @Override
    public Component describe(CompoundTag state) {
        return Component.translatable("item.quantimium.folded_tesseract.foundry",
                ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get().getName(), arms(state));
    }

    @Override
    public ItemStack catalyst(CompoundTag state) {
        return new ItemStack(ModBlocks.QUANTUM_FOUNDRY_CONTROLLER.get());
    }

    @Override
    public List<RecipeType<?>> recipeTypes(CompoundTag state) {
        return List.of(ModRecipeTypes.FOUNDRY_TYPE.get());
    }

    public static int arms(CompoundTag state) {
        return state.getIntOr(ARMS, 0);
    }
}
