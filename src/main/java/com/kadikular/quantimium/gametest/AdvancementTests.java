package com.kadikular.quantimium.gametest;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;

/**
 * The advancements, a guided path on their own tab. Their ids are what packs build quests on, so the
 * tree is checked whole. See wiki: guides/pack-makers.
 */
public final class AdvancementTests {

    private AdvancementTests() {}

    // covers: advancements.path
    @GameTest(template = TestSupport.FLOOR_17)
    public static void everyAdvancementLeadsBackToTheRoot(GameTestHelper helper) {
        ServerAdvancementManager advancements = helper.getLevel().getServer().getAdvancements();
        Identifier root = Identifier.fromNamespaceAndPath(Quantimium.MODID, "root");
        helper.assertTrue(advancements.get(root) != null, "the root should load");
        int count = 0;
        for (AdvancementHolder holder : advancements.getAllAdvancements()) {
            if (!holder.id().getNamespace().equals(Quantimium.MODID)) continue;
            count++;
            AdvancementNode node = advancements.tree().get(holder);
            helper.assertTrue(node != null, holder.id() + " should be in the tree");
            helper.assertTrue(node.root().holder().id().equals(root), holder.id() + " should lead back to the root");
        }
        helper.assertTrue(count >= 24, "all the advancements should load, got " + count);
        helper.succeed();
    }
}
