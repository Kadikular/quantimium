package com.kadikular.quantimium.init;

import com.kadikular.quantimium.Quantimium;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, Quantimium.MODID);

    /** Placeholder: vanilla thunder, slowed 75%. Edit the oggs in assets/quantimium/sounds/mirror/. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_THUNDER =
            SOUNDS.register("mirror.thunder", () -> SoundEvent.createVariableRangeEvent(
                    Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror.thunder")));

    /** Placeholder: vanilla portal travel, faded out after ~2s. Edit sounds/mirror/travel.ogg. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_TRAVEL =
            SOUNDS.register("mirror.travel", () -> SoundEvent.createVariableRangeEvent(
                    Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror.travel")));
    /** Placeholder: vanilla explosion used as lightning impact, slowed 75%. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MIRROR_LIGHTNING_IMPACT =
            SOUNDS.register("mirror.lightning_impact", () -> SoundEvent.createVariableRangeEvent(
                    Identifier.fromNamespaceAndPath(Quantimium.MODID, "mirror.lightning_impact")));

    // Placeholders: these point at existing files through sounds.json, repitched, so real audio can
    // replace them without a code change.
    public static final DeferredHolder<SoundEvent, SoundEvent> LANCE_BEAM = event("lance.beam");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_DRAIN = event("rift.drain");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_COLLAPSE = event("rift.collapse");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_AMBIENT = event("rift.ambient");
    /** Vanilla wind burst, lowered. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_LAUNCH = event("rift.launch");
    /** Our own lightning impact, pitched up and quiet: a crackle, not thunder. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFT_ZAP = event("rift.zap");
    /** Vanilla enderman teleport: a mite coming through or being pulled back. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MITE_RECALL = event("mite.recall");
    /** Vanilla enderman scream, lowered: the Veiled striking. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VEILED_STRIKE = event("veiled.strike");
    /** Vanilla enderman teleport, lowered: the Veiled blinking away. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VEILED_BLINK = event("veiled.blink");
    /** Vanilla enderman stare, lowered: it has noticed you. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VEILED_STARE = event("veiled.stare");
    /** Vanilla enderman death, lowered: driven off by the Lance, it comes apart where it stood. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VEILED_UNRAVEL = event("veiled.unravel");
    /** Vanilla conduit and beacon power-down, lowered: a hall closing on its occupant. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VEILED_CONTAINED = event("veiled.contained");
    /** Vanilla beacon hum, lowered: a hall out of residue, its hold failing. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HALL_STARVING = event("hall.starving");

    private static DeferredHolder<SoundEvent, SoundEvent> event(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(Quantimium.MODID, name)));
    }

    private ModSounds() {}

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
