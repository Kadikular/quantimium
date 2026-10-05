package com.kadikular.quantimium.block.entity;

import com.kadikular.quantimium.block.entity.simulation.SideConfig;
import com.kadikular.quantimium.block.entity.simulation.SideAutomationProfile;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/** Shared side-automation surface for simulator, crafter, stabilizer, and future machines. */
public interface SideConfigurable {
    List<SideConfig> getSideConfigs();
    void applySideConfigs(List<SideConfig> configs);
    boolean isUsableBy(Player player);
    SideAutomationProfile sideAutomationProfile();
}
