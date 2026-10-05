package com.kadikular.quantimium.compat.ae2;

import net.minecraft.resources.ResourceKey;
import appeng.api.AECapabilities;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.init.ModCreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Everything Quantimium adds for Applied Energistics 2. Only {@link #init} reaches it, and only when
 * AE2 is loaded, so these classes (which use AE2's API) are never loaded without it, and without AE2
 * there is no ME Superposition Crafter at all rather than a dead one.
 */
public final class Ae2Content {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Quantimium.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Quantimium.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Quantimium.MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Quantimium.MODID);

    /** Machines whose recipes the Superposition Crafter may offer; packs add and remove by tag. */
    public static final TagKey<Item> CATALYSTS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(Quantimium.MODID, "superposition_crafter_catalysts"));

    public static final DeferredBlock<SuperpositionCrafterBlock> SUPERPOSITION_CRAFTER = BLOCKS.register(
            "me_superposition_crafter",
            id -> new SuperpositionCrafterBlock(BlockBehaviour.Properties.of()
                    .setId(ResourceKey.create(Registries.BLOCK, id))
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(5.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(SuperpositionCrafterBlock.LINK).light)));

    public static final DeferredItem<BlockItem> SUPERPOSITION_CRAFTER_ITEM =
            ITEMS.registerSimpleBlockItem("me_superposition_crafter", SUPERPOSITION_CRAFTER);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuperpositionCrafterBlockEntity>> SUPERPOSITION_CRAFTER_BE =
            BLOCK_ENTITIES.register("me_superposition_crafter_be", () ->
                    new BlockEntityType<>(SuperpositionCrafterBlockEntity::new, SUPERPOSITION_CRAFTER.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<SuperpositionCrafterMenu>> SUPERPOSITION_CRAFTER_MENU =
            MENUS.register("me_superposition_crafter_menu", () -> IMenuTypeExtension.create(SuperpositionCrafterMenu::new));

    private Ae2Content() {}

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(Ae2Content::registerCapabilities);
        modBus.addListener(Ae2Content::addToCreativeTab);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, SUPERPOSITION_CRAFTER_BE.get(),
                (be, context) -> be);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SUPERPOSITION_CRAFTER_BE.get(),
                (be, context) -> be.getEnergyStorage());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SUPERPOSITION_CRAFTER_BE.get(),
                (be, context) -> be.getCatalyst());
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ModCreativeTabs.QUANTIMIUM_TAB.get()) event.accept(SUPERPOSITION_CRAFTER_ITEM.get());
    }
}
