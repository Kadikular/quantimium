package com.kadikular.quantimium.item;

import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.InteractionResult;
import com.kadikular.quantimium.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Goggles that let a thin slice of the mirror through. Worn on the head, the field shows in the real
 * world: flux rising in plumes, anomaly glitching in the air, machines feeding the field or drawing it
 * in, and the Veiled where it really stands, faint. It can see you seeing it.
 *
 * <p>Worn in the helmet slot for now; {@link #isWorn} is the one place a Curios slot would be added.
 */
public class MirrorLensItem extends Item {

    public MirrorLensItem(Properties properties) {
        // Worn in the helmet slot; the component also makes it swap in on use, as Equipable did.
        super(properties.component(DataComponents.EQUIPPABLE,
                Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build()));
    }

    /** Whether {@code entity} is looking through a Mirror Lens. */
    public static boolean isWorn(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.MIRROR_LENS.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.quantimium.mirror_lens.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.quantimium.mirror_lens.tooltip.watched").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
