package com.kadikular.quantimium.laser;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Optional;
import java.util.UUID;

/**
 * An Entity Filter Lens entry for one living thing, not its kind: bound by shift-using the lens on it (or in the air,
 * on yourself). A player shows as their head, anything else as its spawn egg, named after it; the entity's id rides on
 * the entry, so it matches that one alone, wherever it goes.
 */
public final class BoundEntity {

    private static final String KEY = "quantimium_bound";

    private BoundEntity() {}

    /** An entry bound to {@code entity}. */
    public static ItemStack entry(Entity entity) {
        ItemStack stack;
        if (entity instanceof Player player) {
            stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        } else {
            stack = SpawnEggItem.byId(entity.getType()).map(egg -> new ItemStack(egg.value())).orElseGet(() -> new ItemStack(Items.NAME_TAG));
        }
        stack.set(DataComponents.CUSTOM_NAME, entity.getName().copy());
        CompoundTag tag = new CompoundTag();
        tag.store(KEY, UUIDUtil.CODEC, entity.getUUID());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** The entity {@code entry} is bound to, if it's bound to one. */
    public static Optional<UUID> of(ItemStack entry) {
        CustomData data = entry.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        return data.copyTag().read(KEY, UUIDUtil.CODEC);
    }
}
