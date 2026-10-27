package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.EmitterHolder;
import com.kadikular.quantimium.block.entity.LensMountBlockEntity;
import com.kadikular.quantimium.item.LensWithSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Where a lens being set is: in a hand, in a Lens Mount's slot, or in a Relay emitter. How to read it, what to tell
 * its block when it changes (its light changes with it), and whether the player can still reach it.
 *
 * @param heldSlot the hotbar slot holding it, which its menu keeps it in, or -1
 */
public record LensHolder(Supplier<ItemStack> lens, Runnable changed, Predicate<Player> valid, int heldSlot) {

    private static final byte IN_HAND = 0;
    private static final byte IN_MOUNT = 1;
    private static final byte IN_RELAY = 2;

    private static boolean settable(ItemStack stack) {
        return stack.getItem() instanceof LensWithSettings;
    }

    public static LensHolder hand(Inventory inventory, InteractionHand hand) {
        return new LensHolder(() -> inventory.player.getItemInHand(hand), () -> {}, who -> settable(who.getItemInHand(hand)),
                hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : -1);
    }

    public static LensHolder mount(LensMountBlockEntity mount, int slot) {
        return new LensHolder(() -> mount.getLensSlots().getStackInSlot(slot), mount::lensChanged,
                who -> !mount.isRemoved() && settable(mount.getLensSlots().getStackInSlot(slot))
                        && who.distanceToSqr(mount.getBlockPos().getCenter()) <= 64, -1);
    }

    public static LensHolder relay(EmitterHolder relay, Direction face) {
        return new LensHolder(() -> relay.lens(face), () -> relay.setLens(face, relay.lens(face)),
                who -> !relay.isRemoved() && settable(relay.lens(face)) && who.distanceToSqr(relay.getBlockPos().getCenter()) <= 64, -1);
    }

    /** On the client: the holder the server described, or the main hand if what it named is gone. */
    public static LensHolder read(Inventory inventory, FriendlyByteBuf buf) {
        byte where = buf.readByte();
        if (where == IN_MOUNT) {
            BlockPos pos = buf.readBlockPos();
            int slot = buf.readByte();
            if (inventory.player.level().getBlockEntity(pos) instanceof LensMountBlockEntity mount) return mount(mount, slot);
        } else if (where == IN_RELAY) {
            BlockPos pos = buf.readBlockPos();
            Direction face = buf.readEnum(Direction.class);
            if (inventory.player.level().getBlockEntity(pos) instanceof EmitterHolder relay) return relay(relay, face);
        } else {
            return hand(inventory, buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }
        return hand(inventory, InteractionHand.MAIN_HAND);
    }

    /** Opens the settings of the lens in {@code hand}. */
    public static void openInHand(ServerPlayer player, InteractionHand hand) {
        open(player, player.getItemInHand(hand), hand(player.getInventory(), hand), buf -> {
            buf.writeByte(IN_HAND);
            buf.writeBoolean(hand == InteractionHand.MAIN_HAND);
        });
    }

    public static void openInMount(ServerPlayer player, LensMountBlockEntity mount, int slot) {
        open(player, mount.getLensSlots().getStackInSlot(slot), mount(mount, slot), buf -> {
            buf.writeByte(IN_MOUNT);
            buf.writeBlockPos(mount.getBlockPos());
            buf.writeByte(slot);
        });
    }

    public static void openInRelay(ServerPlayer player, EmitterHolder relay, Direction face) {
        open(player, relay.lens(face), relay(relay, face), buf -> {
            buf.writeByte(IN_RELAY);
            buf.writeBlockPos(relay.getBlockPos());
            buf.writeEnum(face);
        });
    }

    private static void open(ServerPlayer player, ItemStack stack, LensHolder holder, Consumer<net.minecraft.network.RegistryFriendlyByteBuf> where) {
        if (!(stack.getItem() instanceof LensWithSettings lens)) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, who) -> lens.menu(id, inventory, holder), stack.getHoverName()), where::accept);
    }
}
