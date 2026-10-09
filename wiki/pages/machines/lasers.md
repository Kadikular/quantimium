# Lasers

*Light kept in step carries power across a room.*

Laser emitters send power as light. A beam crosses open air, glass and anything you can walk
through (grass, torches, flowers), and charges the first block it meets that takes power, from any
mod. Mirrors turn the light around corners, and prisms split and join it at any angle. This is the first part of Quantimium's lasers; lenses that
turn a beam's end into a tool, and items carried by light, come later.

!!! info "Badges"
    Badges like [[mechanic:laser.power]] show whether an in-game test backs the rule next to them. See
    [Mechanics coverage](../reference/coverage.md).

## Emitters

[[mechanic:laser.power]] An emitter takes power on any face and sends it out as light from its
outputs. Placed, it faces you, and its light comes out towards you.

[[mechanic:laser.demand]] It sends only what the blocks its beams reach ask for, up to its rate: point
one at a full battery, or at nothing, and it keeps its power, but for a small cost of being ready.

| Emitter | Rate | One run reaches | Holds | Ready cost |
| --- | --- | --- | --- | --- |
| [[block:flash_emitter]] | 100 FE/t | 8 blocks | 20,000 FE | none |
| [[block:laser_emitter_1]] | 2,000 FE/t | 16 blocks | 200,000 FE | 2 FE/t |
| [[block:laser_emitter_2]] | 32,000 FE/t | 32 blocks | 3,200,000 FE | 16 FE/t |
| [[block:laser_emitter_3]] | 512,000 FE/t | 64 blocks | 51,200,000 FE | 128 FE/t |

These are starting values for the first playtest.

[[mechanic:laser.flash]] The **Flash Emitter** can also burn furnace fuel: each tick of burn time
makes {{c:LaserTuning.FLASH_FE_PER_BURN_TICK}} FE, so a coal gives 64,000. It's power for a base that
has no generator yet. It only starts a new fuel once it has room for what that makes.

[[mechanic:laser.outputs]] Sneak-click a face with an empty hand, or use any wrench on it, to add an
output there, or take one away. One emitter can shine from all six faces; it always keeps at least one.

[[mechanic:laser.redstone]] Open an emitter to see what it's sending and to set how it listens to
redstone: not at all, on only with a signal, or off with a signal.

## Mirrors

[[mechanic:laser.mirror]] A [[block:laser_mirror]] sends every beam that reaches it out of its face.
Placed, it faces you; sneak-click or wrench a face to point it there. It refocuses
the light, so a run from a mirror reaches {{c:LaserTuning.RELAY_RANGE}} blocks whatever sent it.
Each mirror a beam passes takes {{c:LaserTuning.HOP_TAX|pct}} of what passes, and that becomes flux
where the mirror stands.

## Prisms

[[mechanic:laser.prism.split]] A [[block:laser_prism]] sends the light that reaches it to each block
it's linked to, along a straight line at any angle, sharing it by what each asks for.

[[mechanic:laser.prism.join]] Light can reach it from any side, from any number of beams, so a prism
also joins several emitters into one network: a block it's linked to can take more than any one of
them could send. It refocuses and taxes light as a mirror does.

[[mechanic:laser.prism.links]] Link it with the [[item:laser_linker]]: use the Linker on the prism to
pick it, then on each block its light should reach, once to link it and again to unlink it. A prism
links up to {{c:LaserTuning.PRISM_LINKS}} blocks, within {{c:LaserTuning.PRISM_RANGE}} blocks. Sneak-use
the Linker in the air to let the prism go, and sneak-click the prism with an empty hand to let go of
all its links. A link to a block that's been removed, even one light passes through such as grass, is let go of by itself.
[[mechanic:laser.prism.keep]] Use an empty hand on a prism to make it keep those links instead: it goes on aiming at the
spot, so a Harvest lens's light breaks whatever comes back there. Holding the Linker, or a wrench, shows every beam faintly,
even ones carrying nothing, so you can see where light would go.

## Optical cavities

[[mechanic:laser.cavity]] A [[block:cavity_mirror]] facing a [[block:laser_mirror]], or another Cavity
Mirror, across a clear gap of up to {{c:LaserTuning.CAVITY_MAX_GAP}} blocks holds light between them,
as a real laser's cavity does: a battery for a laser network. Only one end needs to be a Cavity Mirror.
Each Cavity Mirror holds {{c:LaserTuning.CAVITY_FE_PER_BLOCK}} FE for each block of gap, and takes or
gives up to {{c:LaserTuning.CAVITY_RATE}} FE/t; a Laser Mirror at the other end holds nothing.

With nothing facing it, a Cavity Mirror is just a mirror: light that reaches it leaves by its front,
towards where its partner would stand. Once paired, what it holds leaves by its back instead.

- Light that reaches a cavity mirror from outside charges it, but only with what the network's
  emitters have left over once its blocks are served. One cavity never fills another.
- Light leaves by the mirror's **back**, its output coupler, for the blocks that ask for it. A network
  serves its blocks from its emitters first, then from its cavities. Use a wrench on a mirror to turn
  its back output off, or on again: a mirror with its output off only stores. Sneak-click a face with
  an empty hand to point the mirror there.
- With two Cavity Mirrors, the light bounces between them, so what either holds evens out between
  them: both glow, and both can give it out.
- Light reaching the Laser Mirror at the far end is passed into the cavity, so that end can feed it.
- A cavity leaks: each tick it loses {{c:LaserTuning.CAVITY_LEAK|pct}} of what it holds, and that
  becomes flux where it stands. Break a charged mirror, or block its gap, and the light it held all goes
  into the field at once.

The held light glows in one beam across the gap, brighter the more there is.

## Photodetectors

[[mechanic:laser.photodetector]] A [[block:photodetector]] sits in a beam: light passes through it and
out of its face, and it takes nothing for it. It doesn't refocus the light, so the light goes on only as
far as it had left. It lights up while light passes, and gives a redstone
signal, and a comparator reading, for how much: 1 for a trickle, two more for each tenfold, up to 15.

## Lenses

[[mechanic:laser.lens.mount]] A [[block:lens_mount]] is a mirror that holds lenses: light that reaches
it leaves by its face, refocused, through its four lens slots, left to right. The beam is still a beam:
it carries power to whatever it ends on, as before, and does what its lenses say along the way. Open
the mount with an empty hand; sneak-click or wrench a face to point it there.

A beam does **one action**, changed by any number of **modifiers**. Light leaving a mount carries every
lens it's met so far, so mounts in a row add their lenses up; where two actions meet, the later one
wins. Each lensed beam is tinted by what it does.

An action spends power from the beam, asked for through the network like any block's: it only works
while the beam is lit, and an emitter pays only while there's something to do.

| Lens | Kind | What it does | Costs |
| --- | --- | --- | --- |
| [[item:harvest_lens]] | Action | [[mechanic:laser.lens.harvest]] Breaks the block the beam ends on, as a diamond pickaxe would, if it's within {{c:LaserTuning.ACTION_REACH}} blocks of the mount; one block every {{c:LaserTuning.HARVEST_COOLDOWN|s}} at most. It never breaks a block with a block entity (a machine, a chest), Anomalite, or what a wither can't break. With the block gone, the beam reaches on to the next. | {{c:LaserTuning.HARVEST_FE_PER_HARDNESS}} FE for each point of hardness |
| [[item:damage_lens]] | Action | [[mechanic:laser.lens.damage]] Hurts anything living that stands in the beam within {{c:LaserTuning.ACTION_REACH}} blocks of the mount, players included, every {{c:LaserTuning.DAMAGE_INTERVAL|s}}. With Pickup, what it kills drops straight back up the beam. | {{c:LaserTuning.DAMAGE_FE_PER_HIT}} FE a hit |
| [[item:pickup_lens]] | Modifier | [[mechanic:laser.lens.pickup]] What the action frees, and items lying within about a block of the beam, go back up it to the nearest Collector; what that one can't take goes to the next, and light joined at a mirror reaches back up every side. With no Collector, drops fall where they are. | {{c:LaserTuning.PICKUP_FE_PER_STACK}} FE a stack |

[[mechanic:laser.collector]] A [[block:collector]] passes light, out of its face, as a mirror does, and
catches what a Pickup lens further along brings back: nine slots, for pipes and hoppers to take from.
Nothing can be put in from outside. Put it between the emitter and the mount:
emitter, Collector, Lens Mount with Harvest and Pickup, then the blocks to break.

## What stops light

[[mechanic:laser.blocked]] Anything solid in the way stops a beam: it ends on that block, and charges
it only if it takes power. Light passes air, glass, glass panes, and blocks with nothing to bump into.
A beam is checked again every {{c:LaserNetworks.RECHECK_TICKS|s}} or so, so building across it cuts it
off within a moment, and clearing it lets the light through again.

[[mechanic:laser.range]] A run of light only reaches so far: an emitter's range, or a mirror's. A
block beyond it gets nothing.

## Networks

Emitters, mirrors, prisms, cavities, photodetectors, Lens Mounts and Collectors joined by light make one network. Power goes where it's asked for: every block
the network's light reaches is charged as far as the network's emitters have power, shared between
the emitters whose light reaches it in proportion to what each has. An emitter only ever pays for the
blocks its own light reaches. Light reaches a block by the fewest mirrors it can.

Use the Flux Meter, or look with Jade, at an emitter or mirror to see what it's sending or passing on.
[[mechanic:laser.live]] Beams glow brighter the more they carry. A switched-on emitter with power keeps
a dim beam lit even when nothing at its end needs power, along with the mirrors and prisms that light
reaches: it's checking for somewhere to send power, and that's what its small ready cost pays for.

[[mechanic:laser.share]] Emitters whose light reaches the same block share a network, so that block
draws on all of them by what each has.
