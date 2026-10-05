package com.kadikular.quantimium.client.renderer;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/**
 * An item model that moves on its own, wrapping the one that draws it. The GUI draws items into an
 * atlas once and reuses them unless they are marked animated, which vanilla does only for a glint; a
 * folding Tesseract in a slot would otherwise stand still. Item definitions use it as
 * {@code {"type": "quantimium:animated", "model": {...}}}.
 */
public record AnimatedItemModel(ItemModel model) implements ItemModel {

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner,
                       int seed) {
        output.setAnimated();
        model.update(output, item, resolver, displayContext, level, owner, seed);
    }

    public record Unbaked(ItemModel.Unbaked model) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemModels.CODEC.fieldOf("model").forGetter(Unbaked::model)).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            model.resolveDependencies(resolver);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            return new AnimatedItemModel(model.bake(context, transformation));
        }
    }
}
