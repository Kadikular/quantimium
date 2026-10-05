package com.kadikular.quantimium.gametest;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * NeoForge's fake player, but one that can be hurt. The stock one is invulnerable, which is right
 * for a machine breaking blocks and wrong for testing a rift's lightning or the Veiled's strike.
 *
 * <p>Like any fake player it never ticks: it stays exactly where it is put, looking where it is
 * aimed, and effects and hurt immunity never wear off. Tests only check the first hit.
 */
public class TestPlayer extends FakePlayer {

    public TestPlayer(ServerLevel level, GameProfile profile) {
        super(level, profile);
        // A new player is untouchable for three seconds, counted down in tick(), which a fake player
        // never runs: without this it could never be hurt at all.
        this.invulnerableTime = 0;
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return isCreative() || isSpectator();
    }
}
