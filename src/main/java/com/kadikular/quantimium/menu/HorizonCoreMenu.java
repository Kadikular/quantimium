package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.HorizonCoreBlockEntity;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.network.HorizonViewPayload;
import com.kadikular.quantimium.reactor.ReactorPlanner;
import com.kadikular.quantimium.reactor.ReactorCounter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The Horizon Core's screen has no slots: it shows the horizon's stock and what its catalysts can
 * reach, sent by {@link HorizonViewPayload} once a second, the lists only when they change.
 */
public class HorizonCoreMenu extends AbstractContainerMenu {

    public static final int DATA_COUNT = 4;
    /** The load and energy line, at least once a second. */
    private static final int VIEW_TICKS = 20;
    /** The list, as soon as a new count lands, but no more often than this. */
    private static final int CHANGE_TICKS = 4;

    private final HorizonCoreBlockEntity blockEntity;
    private final ContainerData data;
    @Nullable
    private final Player player;
    private int sinceView;
    private int sinceSent;
    private int sentVersion = -1;
    @Nullable
    private ReactorCounter.Counts sentCounts;
    @Nullable
    private Component pendingMessage;

    public HorizonCoreMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv,
                (HorizonCoreBlockEntity) playerInv.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public HorizonCoreMenu(int containerId, Inventory playerInv, HorizonCoreBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.HORIZON_CORE_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.player = playerInv.player;
        checkContainerDataCount(data, DATA_COUNT);
        addDataSlots(data);
    }

    public HorizonCoreBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getEnergyStored() {
        return (data.get(1) << 16) | (data.get(0) & 0xFFFF);
    }

    public int getMaxEnergyStored() {
        return (data.get(3) << 16) | (data.get(2) & 0xFFFF);
    }

    /** A request from this player's screen: made now, its result shown at once. */
    public void request(ItemResource item, int count) {
        ReactorPlanner.Result result = blockEntity.request(item, Math.max(1, count));
        pendingMessage = result.planned()
                ? Component.translatable("message.quantimium.reactor.made", count, item.toStack(1).getHoverName())
                : result.problem();
        sinceView = 0;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer server)) return;
        sinceSent++;
        boolean changed = blockEntity.ledgerVersion() != sentVersion || blockEntity.getCounts() != sentCounts;
        boolean due = sinceView-- <= 0 || (changed || pendingMessage != null) && sinceSent >= CHANGE_TICKS;
        if (!due) return;
        sinceView = VIEW_TICKS;
        sinceSent = 0;
        sentVersion = blockEntity.ledgerVersion();
        sentCounts = blockEntity.getCounts();
        PacketDistributor.sendToPlayer(server, new HorizonViewPayload(blockEntity.getBlockPos(),
                blockEntity.getLedger().mass(), blockEntity.capacity(), blockEntity.rings(), blockEntity.isActive(),
                changed ? Optional.of(entries()) : Optional.empty(), Optional.ofNullable(pendingMessage)));
        pendingMessage = null;
    }

    /**
     * Everything held or makeable: held amounts straight from the ledger, totals from the last count.
     * Something held since the last count shows with what's held until the next.
     */
    private List<HorizonViewPayload.Entry> entries() {
        ReactorCounter.Counts counts = blockEntity.getCounts();
        java.util.Map<ItemResource, Long> held = blockEntity.getLedger().view();
        List<HorizonViewPayload.Entry> entries = new ArrayList<>(counts.counts().size() + held.size());
        counts.counts().forEach((item, total) -> {
            long have = held.getOrDefault(item, 0L);
            entries.add(new HorizonViewPayload.Entry(item, have, Math.max(total, have)));
        });
        held.forEach((item, have) -> {
            if (!counts.counts().containsKey(item)) entries.add(new HorizonViewPayload.Entry(item, have, have));
        });
        return entries;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.isUsableBy(player);
    }
}
