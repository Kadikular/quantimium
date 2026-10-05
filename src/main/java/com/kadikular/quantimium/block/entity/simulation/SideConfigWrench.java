package com.kadikular.quantimium.block.entity.simulation;

import com.kadikular.quantimium.block.entity.SideConfigurable;
import com.kadikular.quantimium.init.ModTags;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared wrench face-cycling for any {@link SideConfigurable} machine. Advances only the media
 * mode on the hit face; auto-transfer flags and slot masks stay in the config screen.
 *
 * <p>Future machines only need to call {@link #handleUseItemOn} from their block's {@code useItemOn}
 * (and rely on {@code WrenchInteractions} for the sneak gate) to get the same behaviour.
 */
public final class SideConfigWrench {

    private SideConfigWrench() {}

    /**
     * Block entry point: wrench cycles the hit face, anything else falls through to the usual
     * empty-hand / menu path.
     */
    public static InteractionResult handleUseItemOn(SideConfigurable machine, ItemStack stack,
                                                        Level level, Player player, BlockHitResult hit) {
        if (stack.isEmpty() || !stack.is(ModTags.TOOLS_WRENCH)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            tryCycle(machine, hit.getDirection(), player.isShiftKeyDown(), player);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * @param fluids true to cycle the fluid mode (sneak+wrench), false for the item mode
     * @return true if the click was handled (including no-op on an unsupported face/media)
     */
    public static boolean tryCycle(SideConfigurable machine, Direction side, boolean fluids, Player player) {
        if (!machine.isUsableBy(player)) return false;

        SideAutomationProfile profile = machine.sideAutomationProfile();
        boolean supported = fluids ? profile.supportsFluids(side) : profile.supportsItems(side);
        if (!supported) return true;

        List<SideConfig> configs = new ArrayList<>(machine.getSideConfigs());
        int index = side.get3DDataValue();
        SideConfig current = configs.get(index);
        SideConfig updated = fluids
                ? current.withFluidMode(current.fluidMode().next())
                : current.withItemMode(current.itemMode().next());
        configs.set(index, updated);
        machine.applySideConfigs(configs);
        return true;
    }
}
