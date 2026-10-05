---
navigation:
  title: The Veiled
  parent: mechanics/index.md
  position: 7
---

# The Veiled

| | |
| --- | --- |
| **Type** | Mirror-world stalker |
| **Form** | The Veiled |
| **Exists in** | The mirror only |
| **Health** | Cannot be killed |
| **Released by** | Stage 4 [flux rifts](flux-rifts.md) at Critical flux |
| **Countered by** | Decoherence Lance, Decoherence Projector, Anomaly Containment Hall |
| **One per** | 256 blocks |

*The Veiled* is a hooded, three-block-tall figure with a rift where its face
should be. It comes through the worst [flux rifts](flux-rifts.md) and haunts whoever is nearest:
it feeds on their machines, or on the charge in their gear when they have no base. It cannot be
killed. It can be driven off with a <ItemLink id="quantimium:decoherence_lance" />, and worn down near a powered
[Anomaly Containment Hall](../multiblocks/containment-hall.md) it can be taken and held there.

Its body exists only in the [mirror](../concepts/mirror-phase.md). Players in the real world never
see it directly, only **sightings**: a dark silhouette that appears on the landscape for one
player at a time and fades when watched.

> **Every rule on this page is marked**
>
> Badges like show whether an in-game test backs the rule next
> to them. See Mechanics coverage.

## At a glance

| State | What it is doing | How it gets there |
| --- | --- | --- |
| **Wander** | Drifting in the mirror, sending sightings to players in the real world | Released, driven off, or lost interest |
| **Presence** | Standing at one of your machines and draining its power | Within 14 blocks of a running Quantimium machine |
| **Shadowing** | Following a player who has no base, draining their gear | Within 14 blocks of a player with no machine nearby |
| **Aggravated** | Fighting whoever hit it with the Lance | Hit by a Decoherence Lance |

It moves between them like this: a rift releases it into **Wander**. Sightings draw it closer to its
target until it arrives, when it switches to **Presence** or **Shadowing**. The Lance makes it
**Aggravated** from any state; the fight ends with it held by a hall or driven off, and driven off
it goes back to **Wander** after a cooldown.

## Release

Once a second, every uncontained stage 4 rift whose chunk is at
**Critical flux or higher** has a 1 in 300 chance of
releasing one: on average once every 300 seconds. A
contained or [stabilised](../multiblocks/stabilised-rift.md) rift never releases it.

Only one can exist per area. A rift will not release one if
there is already a free Veiled within 256 blocks, or one held in a hall within
128.

It needs someone to haunt. If no player is within
256 blocks it is gone, and it remembers nothing, so leaving the area is a real
escape. It never despawns while someone is near.

## Sightings

In the real world it is only ever seen as a sighting: a personal image drawn for one player, which
nobody else can see.

While it wanders and is not on cooldown, each unphased player
between 16 and 256 blocks of it may get a sighting. It is placed:

- **ahead of them**, towards where it really is if that is within 60° of where they look, otherwise
  35° to the side it really lies on;
- **at a distance that tracks the real one**, clamped between 20 and
  64 blocks (±6), never closer than 16;
- **on open ground**, with three blocks of air to stand in and a clear line from the player's eyes.

If no spot fits, it tries again 10 seconds later.

A sighting ends in one of four ways:

| Ending | Rule |
| --- | --- |
| **Stared down** | Crosshair held on it for 3 s (within its own width, never tighter than 2°): it fades over 1 s. |
| **Looked away from** | Once seen, look more than 35° away and it is gone when you look back. |
| **Ignored** | After 45 s unwatched, it goes the next time it is out of view. |
| **Walked up to** | It thins from 30 blocks and is gone by 18. |

The server also drops a sighting that is never reported after 120 s.

**Every ended sighting moves its true body closer** to what it
wants: up to 10 blocks towards its target, stopping short of arriving. Any
player's sighting counts, so a group draws it in faster. After a sighting ends, that player gets no
new one for 45–120 seconds.

## Flickering lights

Lamps falter for real-world players near its true body: strongest
right beside it, nothing beyond 40 blocks. Players in the mirror
see nothing; there are no lamps there to falter. Lights stay steady while it is on cooldown.

## Choosing a target

Every 5 seconds while it wanders, it picks the nearest player in the
area (not creative or spectator). If that player has a running Quantimium machine within
32 blocks, that machine is its target; otherwise the player is. But what it wants
most is a [body double](../multiblocks/superposition-pod.md) with nobody in it and nothing guarding it:
anyone's, anywhere in its area.

## Presence: feeding on a machine

Once within 14 blocks of its target machine, it walks
up to it and feeds:

- it drains **80 FE/t** from the machine;
- each second it adds 2 anomaly to the machine's chunk;
- the real world's only tell is **violet motes stuttering off that machine**, visible to everyone.

If the machine is broken it goes back to wandering. If it is drawn more than 40 blocks away, it
gives up and wanders.

## Presence: taking a double

An **unguarded double** comes before any machine: a field double left
by a Tether, or a double in a pod without a Ward module. It walks up to it, settles beside it, and violet
pours off the body where everyone can see it. After 15 s the body is gone and it
carries the double's Sophon away, circling its chest as a small azure cell (one for each it has); its owner
is told. Watched from the mirror, it attends to the watcher and leaves the double be; shadowing someone who
stared at it through a Lens, it goes back to a double once they have not looked at it for
30 s. Nothing reaches a taken Sophon, not even a Recovery
module, until it lets go: **driven off** (the Lance or a Projector), **held** in a hall, or gone for want of
anyone to haunt, it drops everything it carries where it is, as knocked-out Sophons, and Recovery pods
bring them home if they can.

A pod's Ward module keeps it off, as it keeps off mobs; and a Projector on the
pod's crown freezes it before it gets to work.

**Walls do not keep it out.** Walking to a double or a machine and getting no
nearer for three seconds (a wall, a shut door, a sealed room) it does not look for a way round: it blinks
in beside it. Never in beside someone in the mirror, though: it will
not blink within its wary distance of anyone phased, only walk as near as it can. Building a double in
behind stone is no defence; guarding it is.

## Shadowing: following a player

Once within 14 blocks of a player with no base, it follows
them, keeping about 6 blocks behind where they are looking. It only stalks players in the **real
world**: if they step into the mirror, where it can be seen, it stops shadowing and is curious or wary
instead. If it falls more than 24 blocks
behind while they are not looking, it blinks back into place. Every 5 seconds it checks for a
machine within 32 blocks of them and switches to feeding on it if there is one.

Within 8 blocks and **veiled** (outside a 70° cone around where
the player is looking, or with a wall in between), it drains **40 FE/t**
between all their charged items and armour, the Lance included. An <ItemLink id="quantimium:entangled_dock" /> keeps an item
topped up from home while it follows you.

While it follows them in the real world, the player now and then catches it at
the **edge of their view**: every 15–30 seconds, 8–14 blocks away and 55–75° off where they look,
with a quiet stare sound from the spot. A glimpse vanishes the instant they turn towards it, or after
2.5 s whatever they do. It does not move its true body.

## Through a Mirror Lens

A <ItemLink id="quantimium:mirror_lens" /> lets a slice of the mirror through, and the Veiled is part of it.

- **You can see it.** Its true body shows in the real world, faint. Stare at
  it through the lens (crosshair within about 10°, within 48 blocks, line of
  sight) and it wants to know how: a stare sound only you hear, and it starts **shadowing** you. While
  you keep the lens on it stays fixed on you rather than turning to your machines.
- **It sees you too.** Sightings come twice as often while you wear one,
  so it steps closer twice as fast.

## In the mirror

Seen from the mirror it behaves like a wary cat.

- **Curious.** Stare at it (crosshair within about 10°, within
  48 blocks) and it notices you with a stare sound, then walks towards you,
  stopping about 10 blocks away. Its attention stays on you for
  12 s after you look away. Only while wandering or feeding.
- **Wary.** Come within 8 blocks and it backs away.
- **Cornered.** Close within 3.5 blocks while it has nowhere
  left to go and it **strikes**, then blinks 12–18 blocks away. It can strike again after 2 seconds.

A **strike** deals 4 damage (its own damage type, so it reaches phased players) and gives
**Blindness** and **Slowness II** for 3 seconds.

## The Lance: aggravating it

The first touch of a <ItemLink id="quantimium:decoherence_lance" /> beam from a phased
player turns it on them, from any state. Every mite within 24 blocks that can see the lancer turns
on them too.

While the beam holds, it is **frozen**. Each tick of contact
wears it down; it fades as it goes. Contact need not be unbroken: **160 ticks
(8 s) of total contact** ends the fight.

When the beam breaks, it charges the lancer, strikes them from
2.5 blocks, and blinks 6–10 blocks off to come again. It can strike again after 1.5 seconds.

It loses interest and is driven off if the lancer leaves
the mirror, gets more than 48 blocks away, or goes
30 s without a beam on it.

Left alone and not fighting, it pulls itself back together: one tick
of wear heals every 3 ticks, so a full pin heals in 24 seconds. Wear already done carries into
the next fight.

## Ending a fight

**Held.** Once it is worn **past halfway**, a Containment Hall
in reach takes hold of it the moment the beam breaks (or at the end of a full pin). It freezes, a
violet field beam joins it to the hall's cell, and it is drawn in:

| Hall arms | Reach | Time to draw it in |
| --- | --- | --- |
| 1 | 17 blocks | 7 s |
| 2 | 22 blocks | 5.75 s |
| 3 | 27 blocks | 4.5 s |
| 4 | 32 blocks | 3.25 s |

The hall must be formed, powered, empty and stocked with <ItemLink id="quantimium:rift_residue" />. If it loses any of
those while drawing it in, the hold breaks and the fight carries on. See the
[Containment Hall](../multiblocks/containment-hall.md) for keeping it held.

**Driven off.** Fully pinned with no hall in reach (or when it
loses interest), it comes apart with a sound where it stood and blinks 32–48 blocks from the nearest
player. It then stays away for a **cooldown set by the anomaly where it was driven off**. A hot base
calls it back sooner:

| Anomaly band | Cooldown |
| --- | --- |
| Low | 5 min |
| Medium | 3 min |
| High | 2 min |
| Critical | 1 min |
| Singularity | 30 s |

On cooldown it sends no sightings, flickers no lights and does not advance.

## The Decoherence Projector

A <ItemLink id="quantimium:decoherence_projector" /> in range freezes it and wears it
down at the same rate as the Lance, **without aggravating it**: there is nobody for it to turn on.
Past halfway, a hall in reach takes hold as usual, so a Projector beside a hall is a trap. It burns
about one <ItemLink id="quantimium:anomalite_cell" /> a full pin. A full pin
with no hall drives it off.

## Another one, held nearby

A Veiled held in a hall tolerates no other within
128 blocks. Rifts in that area release none, and a free one that is already there
(or wanders in) comes apart with the unravel sound and is gone, not driven off: it does not come
back after a cooldown. See the [Containment Hall](../multiblocks/containment-hall.md).

## Drawing thread from it

A held Veiled can be made to give up <ItemLink id="quantimium:veil_thread" />: a
<ItemLink id="quantimium:harvest_laser" /> fired through a <ItemLink id="quantimium:rift_lens" /> and one wall of the hall's glass reaches it
in the mirror and draws a thread out of it every three minutes. See the
[Harvest Laser](../blocks/harvest_laser.md). It costs the hall nothing for now.

## After release from a hall

If its hall loses power for 10 seconds, runs out of Rift Residue
for 30 seconds, or is broken, it walks out already **feeding on the hall** (Presence, with the hall as
its target).

## Saving

Its state, cooldown and target are saved. A fight is not:
an aggravated Veiled loads as wandering.

## Sounds

| Sound | When | Heard by |
| --- | --- | --- |
| Stare | It notices you (and quietly, with a glimpse) | Phased players; the glimpse's target |
| Strike | It strikes; a hall takes hold | Phased players |
| Blink | It teleports | Phased players |
| Unravel | It is driven off | Phased players |
| Contained | A hall closes on it | Everyone |

All are placeholder sounds: repitched vanilla enderman and beacon files.
