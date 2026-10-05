package com.kadikular.quantimium.client;

import com.kadikular.quantimium.init.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The whoosh of crossing into or out of the mirror: the start of vanilla's portal travel sound, faded
 * out over its second second rather than played to the end.
 */
public final class MirrorTravelSound extends AbstractTickableSoundInstance {

    private static final int HOLD_TICKS = 20;
    private static final int FADE_TICKS = 20;

    private final float full;
    private int age;

    public MirrorTravelSound(float volume, float pitch) {
        super(ModSounds.MIRROR_TRAVEL.get(), SoundSource.AMBIENT, RandomSource.create());
        this.full = volume;
        this.volume = volume;
        this.pitch = pitch;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick() {
        age++;
        if (age > HOLD_TICKS) volume = full * Math.max(0.0f, 1.0f - (age - HOLD_TICKS) / (float) FADE_TICKS);
        if (age >= HOLD_TICKS + FADE_TICKS) stop();
    }
}
