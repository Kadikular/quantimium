package com.kadikular.quantimium.item;

import com.kadikular.quantimium.fold.FoldAdapter;
import com.kadikular.quantimium.fold.FoldedStructure;
import com.kadikular.quantimium.init.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * A multiblock folded into its Tesseract by a Fold Chamber. It can't tick or be touched, and in a
 * Quantum Crafter it is a catalyst for every recipe the machine makes. It unfolds in a chamber at
 * least as big each way, the way it was built, and hands its Tesseract back bound to the controller.
 */
public class FoldedTesseractItem extends Item {

    public FoldedTesseractItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static FoldedStructure folded(ItemStack stack) {
        return stack.getItem() instanceof FoldedTesseractItem ? stack.get(ModDataComponents.FOLDED.get()) : null;
    }

    /** The adapter for what's folded in {@code stack}, if it is a Folded Tesseract holding something we know. */
    public static Optional<FoldAdapter> adapter(ItemStack stack) {
        FoldedStructure folded = folded(stack);
        return folded == null ? Optional.empty() : folded.adapterOrEmpty();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tip, TooltipFlag flag) {
        FoldedStructure folded = folded(stack);
        if (folded == null) {
            tip.accept(Component.translatable("item.quantimium.folded_tesseract.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        Optional<FoldAdapter> adapter = folded.adapterOrEmpty();
        tip.accept(Component.translatable("item.quantimium.folded_tesseract.holds",
                        adapter.map(a -> a.describe(folded.state()))
                                .orElse(Component.literal(folded.adapter().toString())))
                .withStyle(ChatFormatting.AQUA));
        tip.accept(Component.translatable("item.quantimium.folded_tesseract.folded_at", folded.dimensions())
                .withStyle(ChatFormatting.GRAY));
        if (adapter.isPresent()) {
            tip.accept(Component.translatable("item.quantimium.folded_tesseract.catalyst",
                            adapter.get().catalyst(folded.state()).getHoverName())
                    .withStyle(ChatFormatting.GRAY));
        }
        tip.accept(Component.translatable("item.quantimium.folded_tesseract.unfolds", folded.contentDimensions())
                .withStyle(ChatFormatting.DARK_PURPLE));
        tip.accept(Component.translatable("item.quantimium.folded_tesseract.returns")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
