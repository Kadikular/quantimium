---
navigation:
  title: Flux rifts
  parent: mechanics/index.md
  position: 8
---

# Flux rifts

A flux rift is a wound between the realms: a tear that failed to close and stayed. Unlike a tear,
which is a door, a rift grows, leaks anomaly, throws lightning, and at its worst lets things through.
It can only be closed from the [mirror](../concepts/mirror-phase.md), with a
<ItemLink id="quantimium:decoherence_lance" />.

## Birth

When a tear the field opened by itself expires in an **uncontained chunk at High anomaly
or higher**, it fails to close one time in four, leaving a stage 1 rift. Tears opened by commands or
by a rift never do. Rifts are limited where they are, not across the world: at least 64
blocks apart, and at most 3 within 256 blocks of each other (all configurable), so one base's rifts
never stop another's.

**Planting one.** A <ItemLink id="quantimium:rift_seed" /> used on the ground opens a wild
stage 1 rift standing there, but only where there's anomaly for it to feed on: an uncontained chunk
at **Medium anomaly or higher**. Anywhere else it fizzles, *nothing here for it to feed on*, and you
keep the seed. Planted rifts count towards the same limit and spacing as the rest. No hot field? A
<ItemLink id="quantimium:quantum_exciter" /> will make one. Used on a <ItemLink id="quantimium:rift_anchor" /> instead, a seed opens a held
rift anywhere: see the [stabilised rift](../multiblocks/stabilised-rift.md).

## Growth

Rifts grow through **four stages**, each drawn larger. Anomaly sets how fast, flux how far:

| Anomaly in its chunk | Time per stage |
| --- | --- |
| Low | Frozen |
| Medium | 20 min |
| High | 10 min |
| Critical | 5 min |
| Singularity | 2.5 min |

| Flux in its chunk | Largest stage |
| --- | --- |
| Low, Medium | 2 |
| High | 3 |
| Critical, Singularity | 4 |

A contained chunk freezes a rift's growth and its leak. Nothing shrinks a rift on its own.

## What it does

- **Leak.** An uncontained rift bleeds 5 anomaly per stage per second into the 3×3 of chunks
  around it.
- **Solid.** It fills its space: one block column at stage 1, the 3×3 around it by
  stage 3, up to its top. Nothing can be placed there, and it breaks what fills it (with drops) as it
  opens and grows. It leaves fluids, unbreakable blocks and anything with a block entity alone.
- **Touch.** Walking into it burns and throws you: 2 / 3 / 4 / 5 damage by stage, and
  from the real world it drags you through into the mirror. One touch is one throw
  (1 s cooldown).
- **Swinging at it** before you have a Lance throws you back, stings for 2 and blinds
  you for 2 seconds.
- **Lightning.** Anything within 2 blocks of its surface at stage 1, up to 8 at
  stage 4, draws lightning out of the wound: every 2.5 s at the edge of range down to every
  0.5 s point blank, for 3 / 3.5 / 4 / 4.5 damage by stage. Mirror mites
  are left alone.
- **A way in.** It keeps a real-world tear open 10–20 blocks away, the safer door
  beside the dangerous thing.
- **Breach (stage 3+).** Mirror mites come through into the real world, visible and
  hunting everyone: 7.5% a second at stage 3 (up to 2 at once), 15% at stage 4 (up to 4), and only
  with a player within 48 blocks.
- **The Veiled.** A stage 4 rift at Critical flux can [release it](veiled.md).
- **Bleed and corruption.** Mirror flora shows through around it, and real grass and leaves turn
  a dusty violet. Both fade when it closes.

## Closing it

From the mirror, hold a Decoherence Lance beam on its centre. Each stage holds 400
coherence (configurable) and the Lance drains 5 a tick: 4 / 8 / 12 / 16 seconds of unbroken beam.
Break contact for more than 2 seconds and it heals 3 a tick, so it
cannot be worn down across visits. It narrows as it weakens.

While being drained it **fights back**: each second it may spit a mirror mite at you
(up to 1 + stage alive), and every mite within 24 blocks that can see
you turns on you, however calm the field.

When it gives, you get <ItemLink id="quantimium:rift_residue" />: **nothing / 3 / 6 / 10** by stage.
A stage 1 rift pays nothing, so a planted seed has to be left to grow in a fed field first, and after
that letting it grow is a real temptation. Every mite it made pops back into the mirror, and everyone phased within
24 blocks is thrown back to the real world.

A <ItemLink id="quantimium:decoherence_projector" /> can drain a rift unattended at
2 a tick, but from stage 3 the rift lashes it
with lightning down the beam, burning out its whole charge.
The rift's own anomaly comes back with it: the Projector bursts out
**20 anomaly** into the 3×3 of chunks round it.
