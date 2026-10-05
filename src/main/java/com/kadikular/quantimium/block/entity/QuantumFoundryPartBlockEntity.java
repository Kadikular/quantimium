package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.block.multiblock.MultiblockHost;
import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight plinth proxy; all state remains on its host controller.
 *
 * <p>The host is recorded rather than searched for. A plinth can belong to a Foundry beside it or
 * to a Containment Hall four blocks below it, and hunting through that volume on every capability
 * lookup would be wasteful when the controller already knows the answer each time it re-forms.
 */
public class QuantumFoundryPartBlockEntity extends BlockEntity {

    @Nullable
    private BlockPos hostPos;

    public QuantumFoundryPartBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUANTUM_FOUNDRY_PART_BE.get(), pos, state);
    }

    /** Called by a controller that has just claimed this plinth. */
    public void bindHost(BlockPos host) {
        if (host.equals(hostPos)) return;
        hostPos = host.immutable();
        invalidateCapabilities();
        setChanged();
    }

    /** Called by a controller that no longer claims this plinth. Ignores other hosts' claims. */
    public void releaseHost(BlockPos host) {
        if (!host.equals(hostPos)) return;
        hostPos = null;
        invalidateCapabilities();
        setChanged();
    }

    @Nullable
    public MultiblockHost host() {
        if (level == null || hostPos == null) return null;
        if (!(level.getBlockEntity(hostPos) instanceof MultiblockHost host)) return null;
        return host.isFormed() ? host : null;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (hostPos != null) NbtCompat.putPos(tag, "Host", hostPos);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        hostPos = tag.contains("Host") ? NbtCompat.getPos(tag, "Host").orElse(null) : null;
    }
}
