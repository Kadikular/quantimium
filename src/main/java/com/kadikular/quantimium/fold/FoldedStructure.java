package com.kadikular.quantimium.fold;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Everything a Folded Tesseract holds: the blocks of the volume it was folded from, where they stood
 * in the box, and what the fold adapter needs to put the machine back as it was.
 *
 * <p>Everything is in world axes, measured from the box's north-west bottom corner. A fold never
 * rotates: it unfolds the way it was built, in any chamber with room for its blocks, whichever edge
 * that chamber's core is on. The box it came from only matters to the tooltip.
 *
 * @param adapter    the {@link FoldAdapter} that checked and captured the structure
 * @param sizeX      the box it was folded in, east–west; it unfolds in one at least as big each way
 * @param sizeY      the box's height
 * @param sizeZ      the box's extent north–south
 * @param controller the controller's offset from the box's corner
 * @param cells      every block that was in the volume, air left out
 * @param state      what the adapter kept about the machine, such as its arms or coil tiers
 * @param tesseract  the Tesseract that went in, handed back on unfold
 */
public record FoldedStructure(Identifier adapter, int sizeX, int sizeY, int sizeZ, BlockPos controller,
                              List<Cell> cells, CompoundTag state, ItemStack tesseract) {

    public FoldedStructure {
        cells = List.copyOf(cells);
        state = state.copy();
        tesseract = tesseract.copy();
    }

    /** One block: its offset from the core, its state, and its block entity's data if it has one. */
    public record Cell(BlockPos offset, BlockState state, Optional<CompoundTag> data) {
        public static final Codec<Cell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(Cell::offset),
                BlockState.CODEC.fieldOf("state").forGetter(Cell::state),
                CompoundTag.CODEC.optionalFieldOf("data").forGetter(Cell::data)
        ).apply(instance, Cell::new));
    }

    private static final Codec<Integer> SIDE = Codec.intRange(FoldChamber.MIN_SIZE, FoldChamber.MAX_SIZE);

    public static final Codec<FoldedStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("adapter").forGetter(FoldedStructure::adapter),
            SIDE.fieldOf("size_x").forGetter(FoldedStructure::sizeX),
            SIDE.fieldOf("size_y").forGetter(FoldedStructure::sizeY),
            SIDE.fieldOf("size_z").forGetter(FoldedStructure::sizeZ),
            BlockPos.CODEC.fieldOf("controller").forGetter(FoldedStructure::controller),
            Cell.CODEC.listOf().fieldOf("cells").forGetter(FoldedStructure::cells),
            CompoundTag.CODEC.optionalFieldOf("state", new CompoundTag()).forGetter(FoldedStructure::state),
            ItemStack.CODEC.fieldOf("tesseract").forGetter(FoldedStructure::tesseract)
    ).apply(instance, FoldedStructure::new));

    /** The whole structure goes to the client too: a stack the client hands back in creative must still hold it. */
    public static final StreamCodec<RegistryFriendlyByteBuf, FoldedStructure> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    /** The box it was folded from: east–west × height × north–south. */
    public String dimensions() {
        return FoldChamber.dimensions(sizeX, sizeY, sizeZ);
    }

    /** The lowest offset any block has from the box's corner, on each axis: where the machine starts. */
    public BlockPos contentMin() {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (Cell cell : cells) {
            x = Math.min(x, cell.offset().getX());
            y = Math.min(y, cell.offset().getY());
            z = Math.min(z, cell.offset().getZ());
        }
        return cells.isEmpty() ? BlockPos.ZERO : new BlockPos(x, y, z);
    }

    /** How much room the blocks themselves take, east–west, up and north–south: no more than the box. */
    public BlockPos contentSize() {
        if (cells.isEmpty()) return BlockPos.ZERO;
        BlockPos min = contentMin();
        int x = 0, y = 0, z = 0;
        for (Cell cell : cells) {
            x = Math.max(x, cell.offset().getX() - min.getX() + 1);
            y = Math.max(y, cell.offset().getY() - min.getY() + 1);
            z = Math.max(z, cell.offset().getZ() - min.getZ() + 1);
        }
        return new BlockPos(x, y, z);
    }

    /** The machine's own size, east–west × height × north–south. */
    public String contentDimensions() {
        BlockPos size = contentSize();
        return FoldChamber.dimensions(size.getX(), size.getY(), size.getZ());
    }

    public Optional<FoldAdapter> adapterOrEmpty() {
        return FoldAdapters.byId(adapter);
    }

    /** Stacks compare their components by value, and an {@link ItemStack} only equals itself. */
    @Override
    public boolean equals(Object other) {
        return other instanceof FoldedStructure that
                && adapter.equals(that.adapter) && sizeX == that.sizeX && sizeY == that.sizeY
                && sizeZ == that.sizeZ
                && controller.equals(that.controller) && cells.equals(that.cells) && state.equals(that.state)
                && ItemStack.matches(tesseract, that.tesseract);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(adapter, sizeX, sizeY, sizeZ, controller, cells, state,
                ItemStack.hashItemAndComponents(tesseract));
    }
}
