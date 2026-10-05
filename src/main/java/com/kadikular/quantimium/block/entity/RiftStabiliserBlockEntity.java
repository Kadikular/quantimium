package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.block.RiftStabiliserBlock;
import com.kadikular.quantimium.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import com.kadikular.quantimium.flux.QuantumFlux;

/**
 * Rift Stabiliser: one pillar of the array a Rift Anchor holds a rift with. It has no mind of its
 * own — the anchor claims it, bills it and tells it what to draw — so all it keeps is its power and,
 * for the renderer, where its beams go.
 *
 * <p>Draw scales with the rift's stage, since a bigger wound takes more holding: 100 FE/t per
 * stabiliser at stage 1 up to 220 at stage 4.
 */
public class RiftStabiliserBlockEntity extends BlockEntity implements QuantumEnergyHost, FluxMeterReadout {

    public static final int ENERGY_CAPACITY = 200_000;
    public static final int MAX_RECEIVE = 2_000;
    /** An anchor re-binds every second; one silent for longer than this has gone. */
    private static final long BIND_TIMEOUT = 40L;
    /** The emitter is the crossed core floating a third of a block over the cap (RiftStabiliserBER). */
    public static final double EMITTER_HEIGHT = 4.0 / 3.0;

    private final QuantumEnergyStorage energyStorage =
            new QuantumEnergyStorage(ENERGY_CAPACITY, MAX_RECEIVE, 0) {
                @Override
                protected void onReceived() {
                    setChanged();
                }
            };

    /** The anchor that has claimed it. Server-side and transient: anchors re-claim on load. */
    @Nullable
    private BlockPos anchor;
    private long boundTick;
    /** FE spent since flux was last emitted for it. */
    private long unemittedFe;
    /** Synced: the anchor block of the rift it is beaming at, or null when idle. */
    @Nullable
    private BlockPos beamRift;
    private int beamStage;
    /**
     * Re-light this cell once per load. The stabiliser was a full opaque cube before it had its
     * stepped shape, and stored light is trusted on load and never recomputed, so stabilisers placed
     * back then kept a black cell, and the faces inside it rendered black. Deliberately transient.
     */
    private boolean relightPending = true;

    public RiftStabiliserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RIFT_STABILISER_BE.get(), pos, state);
    }

    public static int fePerTick(int stage) {
        return 60 + 40 * stage;
    }

    @Override
    public QuantumEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    public Vec3 emitter() {
        return Vec3.atBottomCenterOf(worldPosition).add(0.0, EMITTER_HEIGHT, 0.0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RiftStabiliserBlockEntity be) {
        if (be.relightPending) {
            be.relightPending = false;
            level.getChunkSource().getLightEngine().checkBlock(pos);
        }
        if (be.anchor != null && level.getGameTime() - be.boundTick > BIND_TIMEOUT) {
            be.anchor = null;
            be.show(null, 0);
        }
        // The beam is not saved, but the lit state is: an unclaimed stabiliser goes dark on load.
        if (be.anchor == null) be.light(false);
        // It emits flux for the power it spends, once a second: pure flux, like a generator's, since it
        // is holding a wound shut rather than working, and a held rift adds no anomaly to its field.
        if (be.unemittedFe > 0 && level.getGameTime() % 20L == 0L && level instanceof ServerLevel server) {
            QuantumFlux.emitFromEnergy(server, pos, be.unemittedFe, 1.0);
            be.unemittedFe = 0;
        }
    }

    /** Free, already this anchor's, or held by one that has stopped asserting its claim. */
    public boolean claimableBy(BlockPos by) {
        return anchor == null || anchor.equals(by)
                || level == null || level.getGameTime() - boundTick > BIND_TIMEOUT;
    }

    public void bind(BlockPos by, long now) {
        anchor = by.immutable();
        boundTick = now;
    }

    public void release(BlockPos by) {
        if (!by.equals(anchor)) return;
        anchor = null;
        show(null, 0);
    }

    public boolean draw(int fe) {
        if (energyStorage.getEnergyStored() < fe) return false;
        energyStorage.consume(fe);
        unemittedFe += fe;
        setChanged();
        return true;
    }

    /** What the renderer should draw. Synced only when it changes. */
    public void show(@Nullable BlockPos rift, int stage) {
        light(rift != null);
        if (Objects.equals(beamRift, rift) && (rift == null || beamStage == stage)) return;
        beamRift = rift == null ? null : rift.immutable();
        beamStage = stage;
        setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void light(boolean beaming) {
        if (!(level instanceof ServerLevel server)) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(RiftStabiliserBlock.BEAMING) || state.getValue(RiftStabiliserBlock.BEAMING) == beaming) return;
        server.setBlock(worldPosition, state.setValue(RiftStabiliserBlock.BEAMING, beaming), Block.UPDATE_CLIENTS);
    }

    @Nullable
    public BlockPos beamRift() {
        return beamRift;
    }

    public int beamStage() {
        return beamStage;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        energyStorage.setEnergy(tag.getIntOr("Energy", 0));
        if (tag.contains("BeamRift")) {
            beamRift = NbtCompat.getPos(tag, "BeamRift").orElse(null);
            beamStage = tag.getIntOr("BeamStage", 0);
        } else if (tag.contains("BeamStage")) {
            beamRift = null;
        }
    }

    /** The beam rides only on sync; the anchor recomputes it every tick. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveLegacy(tag, registries);
        if (beamRift != null) NbtCompat.putPos(tag, "BeamRift", beamRift);
        tag.putInt("BeamStage", beamStage);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Beaming (and what that costs), claimed but idle, or free. */
    @Override
    public MutableComponent fluxMeterLine() {
        MutableComponent line;
        if (beamRift != null) {
            line = Component.translatable("item.quantimium.flux_meter.rift_stabiliser.beaming", beamStage,
                    FluxMeterReadout.fe(fePerTick(beamStage)));
        } else if (anchor != null) {
            line = Component.translatable("item.quantimium.flux_meter.rift_stabiliser.idle");
        } else {
            line = Component.translatable("item.quantimium.flux_meter.rift_stabiliser.free");
        }
        return line.withStyle(ChatFormatting.LIGHT_PURPLE);
    }
}
