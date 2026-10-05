package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.kadikular.quantimium.block.FieldMonitorBlock;
import com.kadikular.quantimium.flux.FieldSurvey;
import com.kadikular.quantimium.flux.FluxBand;
import com.kadikular.quantimium.flux.FluxMeterReadout;
import com.kadikular.quantimium.flux.QuantumFlux;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.menu.FieldMonitorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Reads the field in the {@link FieldSurvey#RADIUS} chunks round it once a second and counts the
 * chunks where what it watches for is happening ({@link Watch}). Any at all lights it and powers
 * redstone; a comparator reads how many, up to 15.
 */
public class FieldMonitorBlockEntity extends BlockEntity implements MenuProvider, FluxMeterReadout {

    /** What it raises the alarm for. */
    public enum Watch {
        /** Containment overloaded: flux past what holds it. */
        OVERLOAD,
        /** Anomaly at Medium or more where nothing contains it: tears, mites and the surcharge. */
        ANOMALY_MEDIUM,
        /** Anomaly at High or more where nothing contains it: crystals and failing tears too. */
        ANOMALY_HIGH;

        public Watch next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int BUTTON_WATCH = 0;

    private Watch watch = Watch.OVERLOAD;
    private int alerting;
    private int watched;
    private double peakFlux;
    private double peakAnomaly;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> watch.ordinal();
                case 1 -> alerting;
                case 2 -> watched;
                case 3 -> (int) Math.min(Integer.MAX_VALUE, Math.round(peakFlux)) & 0xFFFF;
                case 4 -> (int) (Math.min(Integer.MAX_VALUE, Math.round(peakFlux)) >>> 16);
                case 5 -> (int) Math.min(Integer.MAX_VALUE, Math.round(peakAnomaly)) & 0xFFFF;
                case 6 -> (int) (Math.min(Integer.MAX_VALUE, Math.round(peakAnomaly)) >>> 16);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 7;
        }
    };

    public FieldMonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIELD_MONITOR_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, FieldMonitorBlockEntity monitor) {
        if (!(level instanceof ServerLevel server) || server.getGameTime() % 20L != 0L) return;
        monitor.read(server);
    }

    /** Reads the chunks round it and sets the alarm. */
    public void read(ServerLevel level) {
        ChunkPos centre = ChunkPos.containing(worldPosition);
        int radius = FieldSurvey.RADIUS;
        int count = 0;
        int total = 0;
        double flux = 0.0;
        double anomaly = 0.0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos middle = new ChunkPos(centre.x() + dx, centre.z() + dz).getMiddleBlockPosition(worldPosition.getY());
                if (!level.hasChunkAt(middle)) continue;
                total++;
                QuantumFlux.Neighbourhood here = QuantumFlux.chunk(level, middle);
                flux = Math.max(flux, here.flux());
                anomaly = Math.max(anomaly, here.anomaly());
                if (alerts(level, middle, here)) count++;
            }
        }
        alerting = count;
        watched = total;
        peakFlux = flux;
        peakAnomaly = anomaly;
        BlockState state = getBlockState();
        boolean alert = count > 0;
        if (state.getValue(FieldMonitorBlock.ALERT) != alert) {
            level.setBlock(worldPosition, state.setValue(FieldMonitorBlock.ALERT, alert), Block.UPDATE_ALL);
        } else {
            level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        }
        setChanged();
    }

    private boolean alerts(ServerLevel level, BlockPos pos, QuantumFlux.Neighbourhood here) {
        return switch (watch) {
            case OVERLOAD -> QuantumFlux.chunkShielded(level, pos) && !QuantumFlux.chunkContained(level, pos);
            case ANOMALY_MEDIUM -> !QuantumFlux.chunkContained(level, pos) && here.anomaly() >= FluxBand.MEDIUM.minFlux();
            case ANOMALY_HIGH -> !QuantumFlux.chunkContained(level, pos) && here.anomaly() >= FluxBand.HIGH.minFlux();
        };
    }

    /** How many chunks are alerting, up to 15. */
    public int comparatorSignal() {
        return Math.min(15, alerting);
    }

    public int alerting() {
        return alerting;
    }

    public Watch watch() {
        return watch;
    }

    public void setWatch(Watch watch) {
        this.watch = watch;
        setChanged();
        if (level instanceof ServerLevel server) read(server);
    }

    public void pressButton(int id) {
        if (id == BUTTON_WATCH) setWatch(watch.next());
    }

    public ContainerData getData() {
        return data;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.field_monitor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FieldMonitorMenu(containerId, inventory, this, data);
    }

    @Override
    public MutableComponent fluxMeterLine() {
        return Component.translatable("item.quantimium.flux_meter.field_monitor",
                Component.translatable("gui.quantimium.field_monitor.watch." + watch.key()), alerting, watched)
                .withStyle(ChatFormatting.DARK_AQUA);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Watch", watch.ordinal());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        watch = Watch.values()[Math.floorMod(tag.getIntOr("Watch", 0), Watch.values().length)];
    }
}
