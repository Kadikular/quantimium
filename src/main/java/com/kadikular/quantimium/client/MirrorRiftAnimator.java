package com.kadikular.quantimium.client;

import com.kadikular.quantimium.phase.MirrorRift;
import net.minecraft.util.Mth;

/**
 * Client-only open/close for any {@link MirrorRift}. Opening and closing both take
 * {@link MirrorRift#OPEN_TICKS} so a later anomaly-spawned entry tear can share this path.
 */
public final class MirrorRiftAnimator {

    private MirrorRift rift;
    private float width;
    private float previousWidth;
    private boolean closing;

    /** @return true if this started a new open animation. */
    public boolean open(MirrorRift next) {
        if (next.equals(rift) && !closing) return false;
        rift = next;
        closing = false;
        width = 0.0f;
        previousWidth = 0.0f;
        return true;
    }

    public void appearFull(MirrorRift next) {
        rift = next;
        closing = false;
        width = 1.0f;
        previousWidth = 1.0f;
    }

    /** @return true if this started a new close animation. */
    public boolean close() {
        if (rift == null || closing) return false;
        closing = true;
        return true;
    }

    public void clear() {
        rift = null;
        width = 0.0f;
        previousWidth = 0.0f;
        closing = false;
    }

    public void tick() {
        if (rift == null) return;
        previousWidth = width;
        float step = 1.0f / MirrorRift.OPEN_TICKS;
        if (closing) {
            width = Math.max(0.0f, width - step);
            if (width <= 0.0f) clear();
        } else {
            width = Math.min(1.0f, width + step);
        }
    }

    public MirrorRift rift() {
        return rift;
    }

    public boolean visible() {
        return rift != null;
    }

    public boolean openEnoughForParticles() {
        return visible() && !closing && width > 0.35f;
    }

    /** Smoothstep so the tear eases out of a slit instead of growing linearly. */
    public float width(float partialTick) {
        float linear = Mth.lerp(partialTick, previousWidth, width);
        return linear * linear * (3.0f - 2.0f * linear);
    }
}
