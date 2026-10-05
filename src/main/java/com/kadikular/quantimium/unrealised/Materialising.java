package com.kadikular.quantimium.unrealised;

import com.kadikular.quantimium.Config;
import com.kadikular.quantimium.flux.FluxBand;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Choosing what Unrealised Matter becomes, at the Materialiser (game plan E4): an ore of the pack's,
 * as any of the forms its history could make of it (see {@link MatterSteps}). What it can choose is capped by the flux band (commons
 * at Medium, uncommons at High, rares from Critical), and the rarest tier is never choosable.
 *
 * <p>Choosing costs Trace: {@link Config#materialiserPremium} (1.2 by default) times the ore's value
 * over the average value of a roll in the band, so choosing always costs a little more than rolling
 * would on average, and a hot field makes it cheaper. It also debits flux from the Materialiser's
 * chunk, by the ore's value.
 */
public final class Materialising {

    private Materialising() {}

    /** What an ore of each rarity is worth, for pricing: common 1, uncommon 3, rare 10, very rare 30. */
    public static int value(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 1;
            case UNCOMMON -> 3;
            case RARE -> 10;
            case VERY_RARE -> 30;
        };
    }

    /** The rarest ore a band can choose: none at Low, commons at Medium, up to rares from Critical. */
    @Nullable
    public static Rarity cap(FluxBand band) {
        return switch (band) {
            case LOW -> null;
            case MEDIUM -> Rarity.COMMON;
            case HIGH -> Rarity.UNCOMMON;
            case CRITICAL, SINGULARITY -> Rarity.RARE;
        };
    }

    /** The lowest band that can choose {@code rarity}; null for very rare, which never can. */
    @Nullable
    public static FluxBand bandFor(Rarity rarity) {
        for (FluxBand band : FluxBand.values()) {
            Rarity cap = cap(band);
            if (cap != null && cap.ordinal() >= rarity.ordinal()) return band;
        }
        return null;
    }

    public static boolean choosable(Rarity rarity, FluxBand band) {
        Rarity cap = cap(band);
        return cap != null && rarity.ordinal() <= cap.ordinal();
    }

    /** The average value of one observation in {@code band}. */
    public static double expectedValue(FluxBand band) {
        double value = 0.0;
        for (Rarity rarity : Rarity.values()) value += PackOres.share(rarity, band) * value(rarity);
        return value > 0.0 ? value : 1.0;
    }

    /** Trace for choosing an ore of {@code rarity} in {@code band}, per Matter. */
    public static double traceCost(Rarity rarity, FluxBand band) {
        return Config.materialiserPremium() * value(rarity) / expectedValue(band);
    }

    /** Flux debited from the chunk for choosing an ore of {@code rarity}, per Matter. */
    public static int fluxCost(Rarity rarity) {
        return Config.materialiserFluxPerValue() * value(rarity);
    }

    /** What the Materialiser is set to make: an ore of the pack's, as one of its forms (an item id). */
    public record Target(Identifier ore, Identifier form) {
        public static final StreamCodec<ByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Target::ore,
                Identifier.STREAM_CODEC, Target::form,
                Target::new);

        public boolean is(Identifier ore, ItemStack form) {
            return this.ore.equals(ore) && BuiltInRegistries.ITEM.getKey(form.getItem()).equals(this.form);
        }
    }

    /**
     * One thing the Materialiser could make of the Matter in it: an ore and one of its forms, as many
     * as one Matter makes, its rarity, and whether this band can choose it; with its Trace and flux
     * cost, and the band it needs if not.
     */
    public record Option(Identifier ore, ItemStack display, int rarity, boolean allowed, float trace, int flux,
                         int neededBand) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Option> STREAM_CODEC = StreamCodec.of(
                (buf, option) -> {
                    Identifier.STREAM_CODEC.encode(buf, option.ore());
                    ItemStack.STREAM_CODEC.encode(buf, option.display());
                    buf.writeVarInt(option.rarity());
                    buf.writeBoolean(option.allowed());
                    buf.writeFloat(option.trace());
                    buf.writeVarInt(option.flux());
                    buf.writeVarInt(option.neededBand());
                },
                buf -> new Option(Identifier.STREAM_CODEC.decode(buf), ItemStack.STREAM_CODEC.decode(buf),
                        buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(), buf.readVarInt()));
        public static final StreamCodec<RegistryFriendlyByteBuf, List<Option>> LIST_STREAM_CODEC =
                STREAM_CODEC.apply(ByteBufCodecs.list());
    }

    /**
     * Every form of every ore Matter with {@code history} could become, commonest ore first and each
     * ore's forms in the order its history made them, each shown as what one Matter makes: its average,
     * rounded down. Forms that round down to nothing aren't offered, and very rare ores never are.
     */
    public static List<Option> options(ServerLevel level, BlockPos pos, MatterHistory history, FluxBand band) {
        List<Option> options = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) {
            FluxBand needed = bandFor(rarity);
            if (needed == null) continue;
            for (PackOres.Ore ore : PackOres.ores()) {
                if (ore.rarity() != rarity) continue;
                for (Form form : MatterSteps.of(level, pos, history, ore)) {
                    if (form.whole() <= 0) continue;
                    options.add(new Option(ore.tag().location(), form.stack(), rarity.ordinal(), choosable(rarity, band),
                            (float) traceCost(rarity, band), fluxCost(rarity), needed.ordinal()));
                }
            }
        }
        return options;
    }

    /** What one Matter with {@code history} makes as {@code target}: its average, rounded down; empty if nothing. */
    public static ItemStack make(ServerLevel level, BlockPos pos, MatterHistory history, Target target) {
        PackOres.Ore ore = PackOres.byTag(target.ore());
        if (ore == null) return ItemStack.EMPTY;
        for (Form form : MatterSteps.of(level, pos, history, ore)) {
            if (target.is(target.ore(), form.item())) return form.whole() > 0 ? form.stack() : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }
}
