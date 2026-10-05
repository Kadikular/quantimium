package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * One catalyst in a glass case. The Horizon Core reads it each time it looks over its reactor, so
 * swapping a catalyst changes what the Reactor can make within a second.
 */
public class CatalystBayBlockEntity extends BlockEntity {

    private ItemStack catalyst = ItemStack.EMPTY;

    public CatalystBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CATALYST_BAY_BE.get(), pos, state);
    }

    public ItemStack getCatalyst() {
        return catalyst;
    }

    public void setCatalyst(ItemStack stack) {
        catalyst = stack.copyWithCount(Math.min(1, stack.getCount()));
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !catalyst.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), catalyst);
        catalyst = ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("Catalyst", ItemStack.OPTIONAL_CODEC, catalyst);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        catalyst = in.read("Catalyst", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
