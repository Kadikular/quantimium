package com.kadikular.quantimium.client;

import com.kadikular.quantimium.block.entity.LaserRelayBlockEntity;
import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.kadikular.quantimium.item.LensItem;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The colours of lenses fitted in laser blocks, baked into their models: a Lens Mount's slot panels (tint 0 to 3,
 * dark when empty) and a Relay's emitter tips (tint by face, white when bare). Read from the block entity when
 * its chunk is built, which it asks for again when its lenses change.
 */
public final class LensTints {

    /** An empty Lens Mount slot. */
    public static final int EMPTY_SLOT = 0xFF2C303C;
    /** A bare emitter tip: its own colours. */
    private static final int BARE = 0xFFFFFFFF;

    private LensTints() {}

    private static int colour(ItemStack lens, int none) {
        return lens.getItem() instanceof LensItem item ? 0xFF000000 | item.lens().colour : none;
    }

    /** A Lens Mount's four slot panels, by slot. */
    public static List<BlockTintSource> lensMountSlots() {
        List<BlockTintSource> sources = new ArrayList<>();
        for (int slot = 0; slot < LensMountBlockEntity.SLOTS; slot++) {
            int index = slot;
            sources.add(new BlockTintSource() {
                @Override
                public int color(BlockState state) {
                    return EMPTY_SLOT;
                }

                @Override
                public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                    return level.getBlockEntity(pos) instanceof LensMountBlockEntity mount
                            ? colour(mount.getLensSlots().getStackInSlot(index), EMPTY_SLOT) : EMPTY_SLOT;
                }
            });
        }
        return sources;
    }

    /** A Relay's six emitter tips, by face (its 3D data value). */
    public static List<BlockTintSource> relayTips() {
        List<BlockTintSource> sources = new ArrayList<>();
        for (Direction face : Direction.values()) sources.add(null);
        for (Direction face : Direction.values()) {
            sources.set(face.get3DDataValue(), new BlockTintSource() {
                @Override
                public int color(BlockState state) {
                    return BARE;
                }

                @Override
                public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                    return level.getBlockEntity(pos) instanceof LaserRelayBlockEntity relay ? colour(relay.lens(face), BARE) : BARE;
                }
            });
        }
        return sources;
    }
}
