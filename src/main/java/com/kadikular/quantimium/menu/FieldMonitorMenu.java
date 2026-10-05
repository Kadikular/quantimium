package com.kadikular.quantimium.menu;

import com.kadikular.quantimium.block.entity.FieldMonitorBlockEntity;
import com.kadikular.quantimium.flux.FieldSurvey;
import com.kadikular.quantimium.init.ModBlocks;
import com.kadikular.quantimium.init.ModMenuTypes;
import com.kadikular.quantimium.network.FieldMonitorPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Field Monitor's screen: no slots, its counts as data, and on the server a map of the field round
 * it sent to the player once a second while the screen is open.
 */
public class FieldMonitorMenu extends AbstractContainerMenu {

    private static final int DATA_COUNT = 7;

    private final FieldMonitorBlockEntity blockEntity;
    private final ContainerData data;
    private final Player player;
    private int sinceMap;

    public FieldMonitorMenu(int containerId, Inventory inventory, FriendlyByteBuf extraData) {
        this(containerId, inventory,
                (FieldMonitorBlockEntity) inventory.player.level().getBlockEntity(extraData.readBlockPos()),
                new SimpleContainerData(DATA_COUNT));
    }

    public FieldMonitorMenu(int containerId, Inventory inventory, FieldMonitorBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.FIELD_MONITOR_MENU.get(), containerId);
        checkContainerDataCount(data, DATA_COUNT);
        this.blockEntity = blockEntity;
        this.data = data;
        this.player = inventory.player;
        addDataSlots(data);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        // The map, once a second, and at once when opened.
        if (player instanceof ServerPlayer server && sinceMap-- <= 0 && blockEntity.getLevel() instanceof ServerLevel level) {
            sinceMap = 20;
            PacketDistributor.sendToPlayer(server, new FieldMonitorPayload(FieldSurvey.survey(level,
                    ChunkPos.containing(blockEntity.getBlockPos()), blockEntity.getBlockPos().getY(), null)));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        blockEntity.pressButton(id);
        return true;
    }

    public FieldMonitorBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public FieldMonitorBlockEntity.Watch getWatch() {
        return FieldMonitorBlockEntity.Watch.values()[Math.floorMod(data.get(0), FieldMonitorBlockEntity.Watch.values().length)];
    }

    public int getAlerting() {
        return data.get(1);
    }

    public int getWatched() {
        return data.get(2);
    }

    public int getPeakFlux() {
        return (data.get(4) << 16) | (data.get(3) & 0xFFFF);
    }

    public int getPeakAnomaly() {
        return (data.get(6) << 16) | (data.get(5) & 0xFFFF);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, ModBlocks.FIELD_MONITOR.get());
    }
}
