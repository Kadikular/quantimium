package com.kadikular.quantimium.init;

import com.kadikular.quantimium.unrealised.MatterHistory;
import com.kadikular.quantimium.Quantimium;
import com.kadikular.quantimium.item.DockBinding;
import com.kadikular.quantimium.item.SophonBinding;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import com.kadikular.quantimium.fold.FoldedStructure;
import com.kadikular.quantimium.reactor.ReactorLedger;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Quantimium.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> BOUND_POS =
            COMPONENTS.registerComponentType("bound_pos",
                    builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Direction>> BOUND_SIDE =
            COMPONENTS.registerComponentType("bound_side",
                    builder -> builder.persistent(Direction.CODEC).networkSynchronized(Direction.STREAM_CODEC));

    /**
     * The bound block's registry name, captured at bind time so the tesseract can show what it is
     * entangled with even when the target is unloaded or in another dimension. Stored as an id rather
     * than a stack or state because components must be immutable and implement equals and hashCode.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> BOUND_BLOCK =
            COMPONENTS.registerComponentType("bound_block",
                    builder -> builder.persistent(Identifier.CODEC)
                            .networkSynchronized(Identifier.STREAM_CODEC));

    /** Stored FE on a charged item such as the Decoherence Lance. Read through NeoForge's item energy capability. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            COMPONENTS.registerComponentType("energy",
                    builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** An item superposed with an Entangled Dock, which charges it wherever it is carried. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DockBinding>> DOCK_BINDING =
            COMPONENTS.registerComponentType("dock_binding",
                    builder -> builder.persistent(DockBinding.CODEC).networkSynchronized(DockBinding.STREAM_CODEC));

    /** A Sophon: whose double it holds folded up. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SophonBinding>> SOPHON =
            COMPONENTS.registerComponentType("sophon",
                    builder -> builder.persistent(SophonBinding.CODEC).networkSynchronized(SophonBinding.STREAM_CODEC));

    /** What Unrealised Matter has been through without being observed: see MatterHistory. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MatterHistory>> MATTER_HISTORY =
            COMPONENTS.registerComponentType("matter_history",
                    builder -> builder.persistent(MatterHistory.CODEC).networkSynchronized(MatterHistory.STREAM_CODEC));

    /** Charge left in an Anomalite Cell that has been used; a new one is full. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CELL_CHARGE =
            COMPONENTS.registerComponentType("cell_charge",
                    builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** A Folded Tesseract's multiblock: see FoldedStructure. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FoldedStructure>> FOLDED =
            COMPONENTS.registerComponentType("folded",
                    builder -> builder.persistent(FoldedStructure.CODEC).networkSynchronized(FoldedStructure.STREAM_CODEC));

    /** A Horizon Core broken out of its Reactor: everything its horizon held. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ReactorLedger.Entry>>> HORIZON_LEDGER =
            COMPONENTS.registerComponentType("horizon_ledger",
                    builder -> builder.persistent(ReactorLedger.CODEC)
                            .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(ReactorLedger.CODEC)));

    private ModDataComponents() {}

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
