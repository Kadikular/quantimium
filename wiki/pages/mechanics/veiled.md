# The Veiled

<div class="qinfo">
<img class="qinfo-icon" src="../assets/veiled.png" alt="">
<table>
<tr><th>Type</th><td>Mirror-world stalker</td></tr>
<tr><th>Form</th><td>The Veiled</td></tr>
<tr><th>Exists in</th><td>The mirror only</td></tr>
<tr><th>Health</th><td>Cannot be killed</td></tr>
<tr><th>Released by</th><td>Stage 4 <a href="flux-rifts.html">flux rifts</a> at Critical flux</td></tr>
<tr><th>Countered by</th><td>Decoherence Lance, Decoherence Projector, Anomaly Containment Hall</td></tr>
<tr><th>One per</th><td>{{c:Veiled.AREA}} blocks</td></tr>
</table>
</div>

*The Veiled* is a hooded, three-block-tall figure with a rift where its face
should be. It comes through the worst [flux rifts](flux-rifts.md) and haunts whoever is nearest:
it feeds on their machines, or on the charge in their gear when they have no base. It cannot be
killed. It can be driven off with a [[item:decoherence_lance]], and worn down near a powered
[Anomaly Containment Hall](../multiblocks/containment-hall.md) it can be taken and held there.

Its body exists only in the [mirror](../concepts/mirror-phase.md). Players in the real world never
see it directly, only **sightings**: a dark silhouette that appears on the landscape for one
player at a time and fades when watched.

!!! info "Every rule on this page is marked"
    Badges like [[mechanic:veiled.release]] show whether an in-game test backs the rule next
    to them. See [Mechanics coverage](../reference/coverage.md).

## At a glance

| State | What it is doing | How it gets there |
| --- | --- | --- |
| **Wander** | Drifting in the mirror, sending sightings to players in the real world | Released, driven off, or lost interest |
| **Presence** | Standing at one of your machines and draining its power | Within {{c:Veiled.ARRIVED}} blocks of a running Quantimium machine |
| **Shadowing** | Following a player who has no base, draining their gear | Within {{c:Veiled.ARRIVED}} blocks of a player with no machine nearby |
| **Aggravated** | Fighting whoever hit it with the Lance | Hit by a Decoherence Lance |

It moves between them like this: a rift releases it into **Wander**. Sightings draw it closer to its
target until it arrives, when it switches to **Presence** or **Shadowing**. The Lance makes it
**Aggravated** from any state; the fight ends with it held by a hall or driven off, and driven off
it goes back to **Wander** after a cooldown.

## Release

[[mechanic:veiled.release]] Once a second, every uncontained stage 4 rift whose chunk is at
**Critical flux or higher** has a 1 in {{c:VeiledManager.RELEASE_MEAN_SECONDS}} chance of
releasing one: on average once every {{c:VeiledManager.RELEASE_MEAN_SECONDS}} seconds. A
contained or [stabilised](../multiblocks/stabilised-rift.md) rift never releases it.

[[mechanic:veiled.one_per_area]] Only one can exist per area. A rift will not release one if
there is already a free Veiled within {{c:Veiled.AREA}} blocks, or one held in a hall within
{{c:VeiledManager.HELD_PEACE}}.

[[mechanic:veiled.despawn]] It needs someone to haunt. If no player is within
{{c:Veiled.AREA}} blocks it is gone, and it remembers nothing, so leaving the area is a real
escape. It never despawns while someone is near.

## Sightings

In the real world it is only ever seen as a sighting: a personal image drawn for one player, which
nobody else can see.

[[mechanic:veiled.sighting.spawn]] While it wanders and is not on cooldown, each unphased player
between 16 and {{c:Veiled.AREA}} blocks of it may get a sighting. It is placed:

- **ahead of them**, towards where it really is if that is within 60° of where they look, otherwise
  35° to the side it really lies on;
- **at a distance that tracks the real one**, clamped between {{c:VeiledManager.SIGHT_MIN}} and
  {{c:VeiledManager.SIGHT_MAX}} blocks (±6), never closer than 16;
- **on open ground**, with three blocks of air to stand in and a clear line from the player's eyes.

If no spot fits, it tries again 10 seconds later.

[[mechanic:veiled.sighting.end]] A sighting ends in one of four ways:

| Ending | Rule |
| --- | --- |
| **Stared down** | Crosshair held on it for {{c:VeiledSightingClient.DWELL_TO_FADE|s}} (within its own width, never tighter than 2°): it fades over {{c:VeiledSightingClient.FADE_TICKS|s}}. |
| **Looked away from** | Once seen, look more than 35° away and it is gone when you look back. |
| **Ignored** | After {{c:VeiledSightingClient.IGNORED_TICKS|s}} unwatched, it goes the next time it is out of view. |
| **Walked up to** | It thins from {{c:VeiledSightingClient.FADE_FROM}} blocks and is gone by {{c:VeiledSightingClient.TOO_CLOSE}}. |

The server also drops a sighting that is never reported after {{c:VeiledManager.SIGHTING_TIMEOUT|s}}.

[[mechanic:veiled.sighting.advance]] **Every ended sighting moves its true body closer** to what it
wants: up to {{c:Veiled.STEP}} blocks towards its target, stopping short of arriving. Any
player's sighting counts, so a group draws it in faster. After a sighting ends, that player gets no
new one for 45–120 seconds.

## Flickering lights

[[mechanic:veiled.flicker]] Lamps falter for real-world players near its true body: strongest
right beside it, nothing beyond {{c:VeiledManager.FLICKER_RANGE}} blocks. Players in the mirror
see nothing; there are no lamps there to falter. Lights stay steady while it is on cooldown.

## Choosing a target

[[mechanic:veiled.target]] Every 5 seconds while it wanders, it picks the nearest player in the
area (not creative or spectator). If that player has a running Quantimium machine within
{{c:Veiled.BASE_RADIUS}} blocks, that machine is its target; otherwise the player is. But what it wants
most is a [body double](../multiblocks/superposition-pod.md) with nobody in it and nothing guarding it:
anyone's, anywhere in its area.

## Presence: feeding on a machine

[[mechanic:veiled.presence]] Once within {{c:Veiled.ARRIVED}} blocks of its target machine, it walks
up to it and feeds:

- it drains **{{c:Veiled.MACHINE_FE_PER_TICK}} FE/t** from the machine;
- each second it adds 2 anomaly to the machine's chunk;
- the real world's only tell is **violet motes stuttering off that machine**, visible to everyone.

If the machine is broken it goes back to wandering. If it is drawn more than 40 blocks away, it
gives up and wanders.

## Presence: taking a double

[[mechanic:veiled.takes_double]] An **unguarded double** comes before any machine: a field double left
by a Tether, or a double in a pod without a Ward module. It walks up to it, settles beside it, and violet
pours off the body where everyone can see it. After {{c:Veiled.TAKE_TICKS|s}} the body is gone and it
carries the double's Sophon away, circling its chest as a small azure cell (one for each it has); its owner
is told. Watched from the mirror, it attends to the watcher and leaves the double be; shadowing someone who
stared at it through a Lens, it goes back to a double once they have not looked at it for
{{c:Veiled.SHADOW_FORGETS|s}}. Nothing reaches a taken Sophon, not even a Recovery
module, until it lets go: **driven off** (the Lance or a Projector), **held** in a hall, or gone for want of
anyone to haunt, it drops everything it carries where it is, as knocked-out Sophons, and Recovery pods
bring them home if they can.

[[mechanic:veiled.ward]] A pod's Ward module keeps it off, as it keeps off mobs; and a Projector on the
pod's crown freezes it before it gets to work.

[[mechanic:veiled.blink]] **Walls do not keep it out.** Walking to a double or a machine and getting no
nearer for three seconds (a wall, a shut door, a sealed room) it does not look for a way round: it blinks
in beside it. [[mechanic:veiled.blink_keeps_off]] Never in beside someone in the mirror, though: it will
not blink within its wary distance of anyone phased, only walk as near as it can. Building a double in
behind stone is no defence; guarding it is.

## Shadowing: following a player

[[mechanic:veiled.shadow]] Once within {{c:Veiled.ARRIVED}} blocks of a player with no base, it follows
them, keeping about 6 blocks behind where they are looking. It only stalks players in the **real
world**: if they step into the mirror, where it can be seen, it stops shadowing and is curious or wary
instead. If it falls more than 24 blocks
behind while they are not looking, it blinks back into place. Every 5 seconds it checks for a
machine within {{c:Veiled.BASE_RADIUS}} blocks of them and switches to feeding on it if there is one.

[[mechanic:veiled.gear_drain]] Within 8 blocks and **veiled** (outside a 70° cone around where
the player is looking, or with a wall in between), it drains **{{c:Veiled.GEAR_FE_PER_TICK}} FE/t**
between all their charged items and armour, the Lance included. An [[block:entangled_dock]] keeps an item
topped up from home while it follows you.

[[mechanic:veiled.glimpse]] While it follows them in the real world, the player now and then catches it at
the **edge of their view**: every 15–30 seconds, 8–14 blocks away and 55–75° off where they look,
with a quiet stare sound from the spot. A glimpse vanishes the instant they turn towards it, or after
{{c:VeiledSightingClient.GLIMPSE_TICKS|s}} whatever they do. It does not move its true body.

## Through a Mirror Lens

A [[item:mirror_lens]] lets a slice of the mirror through, and the Veiled is part of it.

- [[mechanic:lens.stalk]] **You can see it.** Its true body shows in the real world, faint. Stare at
  it through the lens (crosshair within about 10°, within {{c:Veiled.INTEREST_RANGE}} blocks, line of
  sight) and it wants to know how: a stare sound only you hear, and it starts **shadowing** you. While
  you keep the lens on it stays fixed on you rather than turning to your machines.
- [[mechanic:lens.sightings]] **It sees you too.** Sightings come twice as often while you wear one,
  so it steps closer twice as fast.

## In the mirror

Seen from the mirror it behaves like a wary cat.

- [[mechanic:veiled.curious]] **Curious.** Stare at it (crosshair within about 10°, within
  {{c:Veiled.INTEREST_RANGE}} blocks) and it notices you with a stare sound, then walks towards you,
  stopping about {{c:Veiled.CURIOUS_STOP}} blocks away. Its attention stays on you for
  {{c:Veiled.INTEREST_TICKS|s}} after you look away. Only while wandering or feeding.
- [[mechanic:veiled.wary]] **Wary.** Come within {{c:Veiled.WARY}} blocks and it backs away.
- [[mechanic:veiled.cornered]] **Cornered.** Close within {{c:Veiled.CORNERED}} blocks while it has nowhere
  left to go and it **strikes**, then blinks 12–18 blocks away. It can strike again after 2 seconds.

A **strike** deals 4 damage (its own damage type, so it reaches phased players) and gives
**Blindness** and **Slowness II** for 3 seconds.

## The Lance: aggravating it

[[mechanic:veiled.lance.aggravate]] The first touch of a [[item:decoherence_lance]] beam from a phased
player turns it on them, from any state. Every mite within 24 blocks that can see the lancer turns
on them too.

[[mechanic:veiled.lance.freeze]] While the beam holds, it is **frozen**. Each tick of contact
wears it down; it fades as it goes. Contact need not be unbroken: **{{c:Veiled.PIN_TICKS}} ticks
({{c:Veiled.PIN_TICKS|s}}) of total contact** ends the fight.

[[mechanic:veiled.lance.charge]] When the beam breaks, it charges the lancer, strikes them from
2.5 blocks, and blinks 6–10 blocks off to come again. It can strike again after 1.5 seconds.

[[mechanic:veiled.lance.lose_interest]] It loses interest and is driven off if the lancer leaves
the mirror, gets more than {{c:Veiled.INTEREST_RANGE}} blocks away, or goes
{{c:Veiled.LOSE_INTEREST|s}} without a beam on it.

[[mechanic:veiled.recover]] Left alone and not fighting, it pulls itself back together: one tick
of wear heals every 3 ticks, so a full pin heals in 24 seconds. Wear already done carries into
the next fight.

## Ending a fight

[[mechanic:veiled.capture]] **Held.** Once it is worn **past halfway**, a Containment Hall
in reach takes hold of it the moment the beam breaks (or at the end of a full pin). It freezes, a
violet field beam joins it to the hall's cell, and it is drawn in:

| Hall arms | Reach | Time to draw it in |
| --- | --- | --- |
| 1 | 17 blocks | 7 s |
| 2 | 22 blocks | 5.75 s |
| 3 | 27 blocks | 4.5 s |
| 4 | 32 blocks | 3.25 s |

The hall must be formed, powered, empty and stocked with [[item:rift_residue]]. If it loses any of
those while drawing it in, the hold breaks and the fight carries on. See the
[Containment Hall](../multiblocks/containment-hall.md) for keeping it held.

[[mechanic:veiled.drive_off]] **Driven off.** Fully pinned with no hall in reach (or when it
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

[[mechanic:veiled.projector]] A [[block:decoherence_projector]] in range freezes it and wears it
down at the same rate as the Lance, **without aggravating it**: there is nobody for it to turn on.
Past halfway, a hall in reach takes hold as usual, so a Projector beside a hall is a trap. It burns
about one [[item:anomalite_cell]] a full pin. A full pin
with no hall drives it off.

## Another one, held nearby

[[mechanic:veiled.held_dispels]] A Veiled held in a hall tolerates no other within
{{c:VeiledManager.HELD_PEACE}} blocks. Rifts in that area release none, and a free one that is already there
(or wanders in) comes apart with the unravel sound and is gone, not driven off: it does not come
back after a cooldown. See the [Containment Hall](../multiblocks/containment-hall.md#holding-the-veiled).

## Drawing thread from it

[[mechanic:harvester.thread]] A held Veiled can be made to give up [[item:veil_thread]]: a
[[block:harvest_laser]] fired through a [[block:rift_lens]] and one wall of the hall's glass reaches it
in the mirror and draws a thread out of it every three minutes. See the
[Harvest Laser](../blocks/harvest_laser.md#veil-thread). It costs the hall nothing for now.

## After release from a hall

[[mechanic:veiled.hall_release]] If its hall loses power for 10 seconds, runs out of Rift Residue
for 30 seconds, or is broken, it walks out already **feeding on the hall** (Presence, with the hall as
its target).

## Saving

[[mechanic:veiled.persistence]] Its state, cooldown and target are saved. A fight is not:
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
