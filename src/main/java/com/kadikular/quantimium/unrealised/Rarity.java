package com.kadikular.quantimium.unrealised;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;

/**
 * How rare an ore is, for Unrealised Matter: set per ore by the block tags
 * {@code #quantimium:unrealised/common} … {@code /very_rare}, which a datapack can add to. An ore in
 * none of them counts as very rare: an unknown ore should never come out as often as coal.
 */
public enum Rarity implements StringRepresentable {
    COMMON, UNCOMMON, RARE, VERY_RARE;

    private final TagKey<Block> tag = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "unrealised/" + getSerializedName()));

    /** Ores never made from Matter, whatever else says. */
    public static final TagKey<Block> EXCLUDED = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "unrealised/excluded"));

    public TagKey<Block> tag() {
        return tag;
    }

    /** The commonest tier {@code state} is tagged with, or very rare if none. */
    public static Rarity of(BlockState state) {
        for (Rarity rarity : values()) {
            if (state.is(rarity.tag)) return rarity;
        }
        return VERY_RARE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
