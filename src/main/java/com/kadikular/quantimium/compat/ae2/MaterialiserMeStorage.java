package com.kadikular.quantimium.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.kadikular.quantimium.block.entity.LogisticsMaterialiserBlockEntity;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * A Logistics Materialiser as AE2 storage, for a storage bus on it: what it lists comes from its network's offers as
 * they stand, and a take AE2 only simulates is answered from them too. Through the item handler AE2 would instead
 * check every listed thing by taking it in a transaction it then drops, which for what's made means making it, chain
 * and all, each time it looks.
 */
final class MaterialiserMeStorage implements MEStorage {
    private final LogisticsMaterialiserBlockEntity materialiser;

    MaterialiserMeStorage(LogisticsMaterialiserBlockEntity materialiser) {
        this.materialiser = materialiser;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (materialiser.isRemoved()) return;
        materialiser.forEachListed((item, amount) -> {
            AEItemKey key = AEItemKey.of(item.toStack(1));
            if (key != null && amount > 0) out.add(key, amount);
        });
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!(what instanceof AEItemKey key) || amount <= 0 || materialiser.isRemoved()) return 0;
        ItemResource item = ItemResource.of(key.toStack(1));
        // Only asked: what the offers say, without making anything. A real take may then get less; never more.
        if (mode == Actionable.SIMULATE) return Math.min(amount, materialiser.listed(item));
        var handler = materialiser.itemHandler();
        long got = 0;
        while (got < amount) {
            int ask = (int) Math.min(Integer.MAX_VALUE, amount - got);
            int moved;
            try (Transaction transaction = open()) {
                moved = handler.extract(0, item, ask, transaction);
                transaction.commit();
            }
            if (moved <= 0) break;
            got += moved;
        }
        return got;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (!(what instanceof AEItemKey key) || amount <= 0 || materialiser.isRemoved()) return 0;
        ItemResource item = ItemResource.of(key.toStack(1));
        var handler = materialiser.itemHandler();
        int ask = (int) Math.min(Integer.MAX_VALUE, amount);
        try (Transaction transaction = open()) {
            int put = handler.insert(0, item, ask, transaction);
            if (mode == Actionable.MODULATE) transaction.commit();
            return put;
        }
    }

    /** A transaction of its own, inside any already open. */
    private static Transaction open() {
        TransactionContext outer = Transaction.getCurrentOpenedTransaction();
        return outer == null ? Transaction.openRoot() : Transaction.open(outer);
    }

    @Override
    public Component getDescription() {
        return materialiser.getBlockState().getBlock().getName();
    }
}
