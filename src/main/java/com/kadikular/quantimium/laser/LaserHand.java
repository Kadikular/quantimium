package com.kadikular.quantimium.laser;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The stand-in player an Interact lens works through: one to a level. A fake player doesn't tick, so its
 * swing would never charge and what it holds would never count; this one always swings fully charged,
 * with what's in its hand counting as a player's would.
 */
public final class LaserHand extends FakePlayer {

    private static final Map<ServerLevel, LaserHand> HANDS = new WeakHashMap<>();
    private static final com.mojang.authlib.GameProfile PROFILE =
            new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes("quantimium:laser".getBytes()), "[Quantimium Laser]");

    /** What the item in hand gives it, put on as it's held and taken off after. */
    private final List<Map.Entry<AttributeInstance, AttributeModifier>> worn = new ArrayList<>();

    private LaserHand(ServerLevel level) {
        super(level, PROFILE);
    }

    public static LaserHand of(ServerLevel level) {
        return HANDS.computeIfAbsent(level, LaserHand::new);
    }

    @Override
    public float getAttackStrengthScale(float partialTicks) {
        return 1.0f;
    }

    /** Puts {@code stack} in its hand, with what it gives (a sword's damage), as a player picking it up would have. */
    public void hold(ItemStack stack) {
        letGo();
        setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.forEachModifier(EquipmentSlot.MAINHAND, (Holder<Attribute> attribute, AttributeModifier modifier) -> {
            AttributeInstance instance = getAttribute(attribute);
            if (instance == null || instance.hasModifier(modifier.id())) return;
            instance.addTransientModifier(modifier);
            worn.add(Map.entry(instance, modifier));
        });
    }

    /** Empties its hand of what it gave, leaving the item for the caller. */
    public ItemStack letGo() {
        for (Map.Entry<AttributeInstance, AttributeModifier> entry : worn) entry.getKey().removeModifier(entry.getValue());
        worn.clear();
        ItemStack held = getItemInHand(InteractionHand.MAIN_HAND);
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return held;
    }
}
