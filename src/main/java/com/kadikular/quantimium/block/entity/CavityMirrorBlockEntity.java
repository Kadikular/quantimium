package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.CavityMirrorBlock;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.laser.LaserBeam;
import com.kadikular.quantimium.laser.LaserBeamView;
import com.kadikular.quantimium.laser.LaserNetworks;
import com.kadikular.quantimium.laser.LaserNode;
import com.kadikular.quantimium.laser.LaserTuning;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One mirror of an optical cavity. A Cavity Mirror facing another mirror (a Laser Mirror, or a second
 * Cavity Mirror) across a clear gap holds light between them, as a laser's cavity does. Unpaired, it
 * passes light on out of its back, as a Laser Mirror does. Light that reaches either mirror from outside charges it;
 * light leaves through its back, the output coupler, for blocks that ask for it. Each holds
 * {@link LaserTuning#CAVITY_FE_PER_BLOCK} for each block of gap, and loses a little every tick, as flux.
 * A mirror that loses its partner, or is broken, lets all its light go as flux.
 */
public class CavityMirrorBlockEntity extends BlockEntity implements LaserNode, FluxMeterReadout, LaserBeamView {

    private long stored;
    /** Blocks of clear gap to the partner, or 0 for none. */
    private int gap;
    /** Whether the partner is a Cavity Mirror too, so both hold the light; a Laser Mirror holds none. */
    private boolean partnerHolds;
    private double leaked;
    /** Whether light leaves by the back; a wrench turns it off, so the cavity only stores. */
    private boolean output = true;
    private List<LaserBeam> shownBeams = List.of();
    /** On the client: how bright the light held looks, for the glow between the mirrors. */
    private int shownCharge;

    public CavityMirrorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAVITY_MIRROR_BE.get(), pos, state);
    }

    public Direction facing() {
        return getBlockState().getValue(CavityMirrorBlock.FACING);
    }

    public boolean paired() {
        return gap > 0;
    }

    public long stored() {
        return stored;
    }

    public boolean output() {
        return output;
    }

    /** Turns light out of the back on or off. */
    public void toggleOutput() {
        output = !output;
        setChanged();
        if (level != null) LaserNetworks.changed(level);
    }

    public long capacity() {
        return gap * LaserTuning.CAVITY_FE_PER_BLOCK;
    }

    /** Whether a mirror faces this one across a clear gap; how many blocks of gap, or 0. */
    private int findGap() {
        partnerHolds = false;
        if (level == null) return 0;
        BlockPos.MutableBlockPos at = worldPosition.mutable();
        Direction facing = facing();
        for (int step = 1; step <= LaserTuning.CAVITY_MAX_GAP + 1; step++) {
            at.move(facing);
            if (!level.isLoaded(at)) return 0;
            if (level.getBlockEntity(at) instanceof CavityMirrorBlockEntity other) {
                partnerHolds = true;
                return other.facing() == facing.getOpposite() && step > 1 ? step - 1 : 0;
            }
            BlockState there = level.getBlockState(at);
            if (there.getBlock() instanceof com.kadikular.quantimium.block.LaserMirrorBlock) {
                return there.getValue(com.kadikular.quantimium.block.LaserMirrorBlock.FACING) == facing.getOpposite() && step > 1 ? step - 1 : 0;
            }
            if (!LaserBeam.passes(level, at, level.getBlockState(at))) return 0;
        }
        return 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CavityMirrorBlockEntity mirror) {
        if (!(level instanceof ServerLevel server)) return;
        if (level.getGameTime() % 20 == Math.floorMod(pos.hashCode(), 20)) mirror.checkPartner(server);
        mirror.share(server);
        if (mirror.stored > 0) {
            double leak = mirror.stored * LaserTuning.CAVITY_LEAK;
            mirror.leaked += leak;
            mirror.stored -= (long) Math.ceil(leak);
            if (mirror.stored < 0) mirror.stored = 0;
            mirror.setChanged();
        }
        if (level.getGameTime() % 20 == 0) {
            if (mirror.leaked >= 1) QuantumFlux.emitFromEnergy(server, pos, (long) mirror.leaked);
            mirror.leaked = 0;
            mirror.showCharge();
        }
    }

    /**
     * Light held in a cavity bounces between both mirrors: what either holds evens out between them, so
     * both glow and both can give it out. The lower of the two does it, once a tick.
     */
    private void share(ServerLevel server) {
        if (!paired() || !partnerHolds) return;
        BlockPos other = worldPosition.relative(facing(), gap + 1);
        if (other.compareTo(worldPosition) < 0 || !(server.getBlockEntity(other) instanceof CavityMirrorBlockEntity partner)) return;
        long total = stored + partner.stored;
        if (Math.abs(stored - partner.stored) <= 1) return;
        stored = Math.min(capacity(), total / 2);
        partner.stored = Math.min(partner.capacity(), total - stored);
        setChanged();
        partner.setChanged();
    }

    private void checkPartner(ServerLevel server) {
        boolean held = partnerHolds;
        int now = findGap();
        if (now == gap && held == partnerHolds) return;
        gap = now;
        if (stored > capacity()) release(server, stored - capacity());
        setChanged();
        LaserNetworks.changed(server);
        showCharge();
    }

    /** Lets {@code fe} of held light go, as flux where it stands. */
    private void release(ServerLevel server, long fe) {
        if (fe <= 0) return;
        stored -= fe;
        QuantumFlux.emitFromEnergy(server, worldPosition, fe);
    }

    /** Tells clients when the light held changes enough to see. */
    private void showCharge() {
        int charge = LaserNetworks.brightness(stored / 100);
        BlockState state = getBlockState();
        boolean lit = stored > 0 && paired();
        if (level != null && state.getValue(CavityMirrorBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(CavityMirrorBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
        if (charge != shownCharge) {
            shownCharge = charge;
            sync();
        }
    }

    // ---- the network ----

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    /** Light leaves by the back while its output is on: what it held when paired, or what reaches it when not. */
    @Override
    public Set<Direction> outputs() {
        return output ? Set.of(facing().getOpposite()) : Set.of();
    }

    @Override
    public int range() {
        return LaserTuning.RELAY_RANGE;
    }

    /** Unpaired, it's a mirror: light passes through it, out of its back. */
    @Override
    public boolean relays() {
        return !paired();
    }

    @Override
    public boolean buffers() {
        return paired();
    }

    @Override
    public long available() {
        return paired() && output ? Math.min(stored, LaserTuning.CAVITY_RATE) : 0;
    }

    @Override
    public void draw(long fe) {
        stored = Math.max(0, stored - fe);
        setChanged();
    }

    @Override
    public long room() {
        return paired() ? Math.max(0, Math.min(capacity() - stored, LaserTuning.CAVITY_RATE)) : 0;
    }

    @Override
    public void fill(long fe) {
        stored = Math.min(capacity(), stored + fe);
        setChanged();
    }

    @Override
    public void showBeams(List<LaserBeam> beams) {
        boolean same = LaserBeamView.sameLook(shownBeams, beams);
        shownBeams = beams;
        if (!same) sync();
    }

    /** Its output, and the light held, drawn across the whole gap: by this one, or the lower of two Cavity Mirrors. */
    @Override
    public List<LaserBeam> syncedBeams() {
        if (gap <= 0 || shownCharge <= 0) return shownBeams;
        if (partnerHolds && worldPosition.relative(facing(), gap + 1).compareTo(worldPosition) < 0) return shownBeams;
        List<LaserBeam> all = new ArrayList<>(shownBeams);
        long flow = (long) Math.pow(10, shownCharge - 1);
        all.add(new LaserBeam(worldPosition, facing(), null, gap, null, LaserBeam.End.OPEN, flow, true));
        return all;
    }

    // ---- life ----

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            gap = findGap();
            LaserNetworks.add(level, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) LaserNetworks.remove(level, this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) LaserNetworks.remove(level, this);
    }

    /** Broken, it lets all the light it held go, as flux. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) release(server, stored);
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("Stored", stored);
        out.putInt("Gap", gap);
        out.putBoolean("PartnerHolds", partnerHolds);
        out.putBoolean("Output", output);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        stored = in.getLongOr("Stored", 0);
        gap = in.getIntOr("Gap", 0);
        partnerHolds = in.getBooleanOr("PartnerHolds", true);
        output = in.getBooleanOr("Output", true);
        shownCharge = in.getIntOr("Charge", 0);
        in.read("Beams", CompoundTag.CODEC).ifPresent(tag -> shownBeams = LaserBeamView.load(worldPosition, tag));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.put("Beams", LaserBeamView.save(shownBeams));
        tag.putInt("Charge", shownCharge);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** What it holds, or why it holds nothing. */
    @Override
    @Nullable
    public MutableComponent fluxMeterLine() {
        if (!paired()) return Component.translatable("message.quantimium.cavity.unpaired").withStyle(ChatFormatting.GRAY);
        MutableComponent line = Component.translatable("message.quantimium.cavity.holds", String.format(java.util.Locale.ROOT, "%,d", stored),
                String.format(java.util.Locale.ROOT, "%,d", capacity()), gap).withStyle(ChatFormatting.DARK_AQUA);
        return output ? line : line.append(Component.translatable("message.quantimium.cavity.no_output").withStyle(ChatFormatting.GRAY));
    }
}
