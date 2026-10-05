package com.kadikular.quantimium.compat.jade;

import com.kadikular.quantimium.util.NbtCompat;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.block.entity.QuantumFoundryPartBlockEntity;
import com.kadikular.quantimium.flux.AnomalyEffects;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/** The server half of {@link FieldProvider}: the field where a Quantimium block stands, and its readout. */
enum FieldData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public Identifier getUid() {
        return FieldProvider.UID;
    }

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return isOurs(accessor.getBlockEntity());
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        BlockEntity be = accessor.getBlockEntity();
        if (!isOurs(be)) return;
        Level level = accessor.getLevel();
        BlockPos pos = accessor.getPosition();
        QuantumFlux.Neighbourhood here = QuantumFlux.sample(level, pos);
        CompoundTag field = new CompoundTag();
        field.putDouble("flux", here.flux());
        field.putDouble("anomaly", here.anomaly());
        field.putDouble("settling", QuantumFlux.chunkSettling(level, pos));
        field.putDouble("load", QuantumFlux.chunkShielded(level, pos) ? QuantumFlux.chunkLoad(level, pos) : -1.0);
        field.putInt("surcharge", AnomalyEffects.surchargePercent(level, pos));
        field.putInt("leak", AnomalyEffects.passiveDrainFePerTick(level, pos));
        MutableComponent line = readout(be);
        if (line != null) field.putString("readout", NbtCompat.componentToJson(line, level.registryAccess()));
        data.put(FieldProvider.KEY, field);
    }

    /** Quantimium's own blocks only: vanilla's and other mods' stay as they are. */
    private static boolean isOurs(@Nullable BlockEntity be) {
        return be != null && Quantimium.MODID.equals(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getNamespace());
    }

    /** What the block says for itself; a Foundry part answers for its controller, as with the Flux Meter. */
    @Nullable
    private static MutableComponent readout(BlockEntity be) {
        if (be instanceof QuantumFoundryPartBlockEntity part && part.host() instanceof BlockEntity host) be = host;
        return be instanceof FluxMeterReadout readout ? readout.fluxMeterLine() : null;
    }
}
