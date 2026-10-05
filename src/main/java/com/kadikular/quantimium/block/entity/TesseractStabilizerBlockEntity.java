package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.util.LegacyFluids;
import com.kadikular.quantimium.util.LegacyItems;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import com.kadikular.quantimium.util.RestrictedItems;
import com.kadikular.quantimium.util.NbtCompat;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.kadikular.quantimium.block.TesseractStabilizerBlock;
import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideMode;
import com.kadikular.quantimium.init.ModBlockEntities;
import com.kadikular.quantimium.menu.TesseractStabilizerMenu;
import com.kadikular.quantimium.recipe.EntangledLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import com.kadikular.quantimium.util.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Docks one Entangled Link and exposes the bound inventory/tank as local face capabilities, gated by
 * {@link SideConfig}. The link's bound side is the remote attachment; these faces are local logistics.
 */
public class TesseractStabilizerBlockEntity extends BlockEntity implements MenuProvider, SideConfigurable {

    public static final int LINK_SLOT = 0;

    /** Every face but the open one, which stays clear for the tesseract void; one profile per facing. */
    private static final SideAutomationProfile[] PROFILES = new SideAutomationProfile[Direction.values().length];

    static {
        for (Direction open : Direction.values()) {
            PROFILES[open.get3DDataValue()] = new SideAutomationProfile(
                    SideAutomationProfile.sidesExcept(open), SideAutomationProfile.sidesExcept(open));
        }
    }

    public static final int STATUS_EMPTY = 0;
    public static final int STATUS_LINKED = 1;
    public static final int STATUS_UNLOADED = 2;

    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
            invalidateCapabilities();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return EntangledLinks.fitsStabilizer(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    private List<SideConfig> sideConfigs = defaultSideConfigs();

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? resolveStatus() : 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 1;
        }
    };

    public TesseractStabilizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TESSERACT_STABILIZER_BE.get(), pos, state);
    }

    /** The open face, where the tesseract sits: up unless it was placed against a wall or ceiling. */
    public Direction openFace() {
        BlockState state = getBlockState();
        return state.hasProperty(TesseractStabilizerBlock.FACING) ? state.getValue(TesseractStabilizerBlock.FACING) : Direction.UP;
    }

    private List<SideConfig> defaultSideConfigs() {
        return sideAutomationProfile().sanitize(SideConfig.defaults().stream()
                .map(config -> new SideConfig(
                        config.side(),
                        SideMode.BOTH, config.itemInputMask(), config.itemOutputMask(), false, false,
                        SideMode.BOTH, config.fluidInputMask(), config.fluidOutputMask(), false, false))
                .toList());
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ItemStack getLink() {
        return inventory.getStackInSlot(LINK_SLOT);
    }

    public boolean insertLink(ItemStack held, Player player) {
        if (!EntangledLinks.fitsStabilizer(held) || !getLink().isEmpty()) return false;
        inventory.setStackInSlot(LINK_SLOT, held.copyWithCount(1));
        if (!player.getAbilities().instabuild) held.shrink(1);
        return true;
    }

    public ItemStack removeLink() {
        return inventory.extractItem(LINK_SLOT, 1, false);
    }

    public ContainerData getContainerData() {
        return data;
    }

    public int resolveStatus() {
        ItemStack link = getLink();
        if (!EntangledLinks.isBound(link)) return STATUS_EMPTY;
        return EntangledLinks.isBoundReachable(level, link) ? STATUS_LINKED : STATUS_UNLOADED;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TesseractStabilizerBlockEntity be) {
        if (level.isClientSide()) return;
        if (level.getGameTime() % 20L == 0L) be.runAutoTransfers(level);
    }

    @Nullable
    public ResourceHandler<ItemResource> getAutomationItemHandler(@Nullable Direction side) {
        if (side == null) return null;
        if (!sideAutomationProfile().supportsItems(side)) return null;
        SideConfig config = sideConfigs.get(side.get3DDataValue());
        if (config.itemMode() == SideMode.DISABLED) return null;
        ResourceHandler<ItemResource> remote = EntangledLinks.resolveItems(level, getLink());
        if (remote == null) return null;
        return RestrictedItems.byMode(remote, config.itemMode().allowsInput(), config.itemMode().allowsOutput());
    }

    @Nullable
    public ResourceHandler<FluidResource> getAutomationFluidHandler(@Nullable Direction side) {
        if (side == null) return null;
        if (!sideAutomationProfile().supportsFluids(side)) return null;
        SideConfig config = sideConfigs.get(side.get3DDataValue());
        if (config.fluidMode() == SideMode.DISABLED) return null;
        ResourceHandler<FluidResource> remote = EntangledLinks.resolveFluids(level, getLink());
        if (remote == null) return null;
        return RestrictedItems.byMode(remote, config.fluidMode().allowsInput(), config.fluidMode().allowsOutput());
    }

    private void runAutoTransfers(Level level) {
        for (Direction side : Direction.values()) {
            if (!sideAutomationProfile().supportsItems(side)
                    && !sideAutomationProfile().supportsFluids(side)) continue;
            SideConfig config = sideConfigs.get(side.get3DDataValue());

            if ((config.autoItemInput() || config.autoItemOutput())
                    && sideAutomationProfile().supportsItems(side)
                    && config.itemMode() != SideMode.DISABLED) {
                IItemHandler remote = EntangledLinks.resolve(level, getLink());
                IItemHandler neighbor = LegacyItems.legacy(level.getCapability(Capabilities.Item.BLOCK,
                        worldPosition.relative(side), side.getOpposite()));
                if (remote != null && neighbor != null) {
                    if (config.autoItemInput() && config.itemMode().allowsInput()) {
                        pullItem(neighbor, remote);
                    }
                    if (config.autoItemOutput() && config.itemMode().allowsOutput()) {
                        pushItem(remote, neighbor);
                    }
                }
            }

            if ((config.autoFluidInput() || config.autoFluidOutput())
                    && sideAutomationProfile().supportsFluids(side)
                    && config.fluidMode() != SideMode.DISABLED) {
                IFluidHandler remote = EntangledLinks.resolveFluid(level, getLink());
                IFluidHandler neighbor = LegacyFluids.legacy(level.getCapability(Capabilities.Fluid.BLOCK,
                        worldPosition.relative(side), side.getOpposite()));
                if (remote != null && neighbor != null) {
                    if (config.autoFluidInput() && config.fluidMode().allowsInput()) {
                        pullFluid(neighbor, remote);
                    }
                    if (config.autoFluidOutput() && config.fluidMode().allowsOutput()) {
                        pushFluid(remote, neighbor);
                    }
                }
            }
        }
    }

    private static void pullItem(IItemHandler from, IItemHandler into) {
        for (int source = 0; source < from.getSlots(); source++) {
            ItemStack offered = from.extractItem(source, 64, true);
            if (offered.isEmpty()) continue;
            ItemStack leftover = insertAll(into, offered, true);
            int accepted = offered.getCount() - leftover.getCount();
            if (accepted <= 0) continue;
            ItemStack extracted = from.extractItem(source, accepted, false);
            if (!extracted.isEmpty()) insertAll(into, extracted, false);
            return;
        }
    }

    private static void pushItem(IItemHandler from, IItemHandler into) {
        for (int source = 0; source < from.getSlots(); source++) {
            ItemStack offered = from.extractItem(source, 64, true);
            if (offered.isEmpty()) continue;
            ItemStack leftover = insertAll(into, offered, true);
            int accepted = offered.getCount() - leftover.getCount();
            if (accepted <= 0) continue;
            ItemStack extracted = from.extractItem(source, accepted, false);
            if (!extracted.isEmpty()) {
                ItemStack remaining = insertAll(into, extracted, false);
                if (!remaining.isEmpty()) from.insertItem(source, remaining, false);
            }
            return;
        }
    }

    private static ItemStack insertAll(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, simulate);
        }
        return remaining;
    }

    private static void pullFluid(IFluidHandler from, IFluidHandler into) {
        FluidStack offered = from.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) return;
        int accepted = into.fill(offered, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack extracted = from.drain(offered.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (!extracted.isEmpty()) into.fill(extracted, IFluidHandler.FluidAction.EXECUTE);
    }

    private static void pushFluid(IFluidHandler from, IFluidHandler into) {
        FluidStack offered = from.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (offered.isEmpty()) return;
        int accepted = into.fill(offered, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;
        FluidStack extracted = from.drain(offered.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
        if (!extracted.isEmpty()) into.fill(extracted, IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public List<SideConfig> getSideConfigs() {
        return List.copyOf(sideConfigs);
    }

    /** Read-only view for the renderer, which reads the modes every frame and must not allocate. */
    public List<SideConfig> sideConfigsView() {
        return sideConfigs;
    }

    @Override
    public void applySideConfigs(List<SideConfig> configs) {
        sideConfigs = sideAutomationProfile().sanitize(configs);
        setChanged();
        invalidateCapabilities();
        syncToClient();
    }

    @Override
    public SideAutomationProfile sideAutomationProfile() {
        return PROFILES[openFace().get3DDataValue()];
    }

    @Override
    public boolean isUsableBy(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.quantimium.tesseract_stabilizer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TesseractStabilizerMenu(containerId, playerInventory, this, data);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        CompoundTag tag = new CompoundTag();
        saveLegacy(tag, NbtCompat.registries(level));
        NbtCompat.write(out, tag);
    }

    private void saveLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("SideConfigs", SideConfig.saveAll(sideConfigs));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        loadLegacy(NbtCompat.read(in), NbtCompat.lookup(in));
    }

    private void loadLegacy(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompoundOrEmpty("Inventory"));
        if (tag.contains("SideConfigs")) {
            sideConfigs = sideAutomationProfile().sanitize(SideConfig.loadAll(tag.getListOrEmpty("SideConfigs")));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveLegacy(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        CompoundTag tag = NbtCompat.read(input);
        if (!tag.isEmpty()) loadLegacy(tag, NbtCompat.lookup(input));
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        Level level = this.level;
        if (level == null) return;
        ItemStack link = getLink();
        if (!link.isEmpty()) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), link);
        }
    }
}
