---
navigation:
  title: Lasers
  parent: machines/index.md
  position: 16
---

# Lasers

*Light kept in step carries power across a room.*

Laser emitters send power as light. A beam crosses open air, glass and anything you can walk
through (grass, torches, flowers), and charges the first block it meets that takes power, from any
mod. Mirrors turn the light around corners, and prisms split and join it at any angle. This is the first part of Quantimium's lasers; lenses that
turn a beam's end into a tool, and items carried by light, come later.

> **Badges**
>
> Badges like show whether an in-game test backs the rule next to them. See
> Mechanics coverage.

## Emitters

An emitter takes power on any face and sends it out as light from its
outputs. Placed, it faces you, and its light comes out towards you.

It sends only what the blocks its beams reach ask for, up to its rate: point
one at a full battery, or at nothing, and it keeps its power, but for a small cost of being ready.

| Emitter | Rate | One run reaches | Holds | Ready cost |
| --- | --- | --- | --- | --- |
| <ItemLink id="quantimium:flash_emitter" /> | 100 FE/t | 8 blocks | 20,000 FE | none |
| <ItemLink id="quantimium:laser_emitter_1" /> | 2,000 FE/t | 16 blocks | 200,000 FE | 2 FE/t |
| <ItemLink id="quantimium:laser_emitter_2" /> | 32,000 FE/t | 32 blocks | 3,200,000 FE | 16 FE/t |
| <ItemLink id="quantimium:laser_emitter_3" /> | 512,000 FE/t | 64 blocks | 51,200,000 FE | 128 FE/t |

These are starting values for the first playtest.

The **Flash Emitter** can also burn furnace fuel: each tick of burn time
makes 40 FE, so a coal gives 64,000. It's power for a base that
has no generator yet. It only starts a new fuel once it has room for what that makes.

Sneak-click a face with an empty hand, or use any wrench on it, to add an
output there, or take one away. One emitter can shine from all six faces; it always keeps at least one.

Open an emitter to see what it's sending and to set how it listens to
redstone: not at all, on only with a signal, or off with a signal.

## Mirrors

A <ItemLink id="quantimium:laser_mirror" /> sends every beam that reaches it out of its face.
Placed, it faces you; sneak-click or wrench a face to point it there. It refocuses
the light, so a run from a mirror reaches 32 blocks whatever sent it.
Each mirror a beam passes takes 1% of what passes, and that becomes flux
where the mirror stands.

## Prisms

A <ItemLink id="quantimium:laser_prism" /> sends the light that reaches it to each block
it's linked to, along a straight line at any angle, sharing it by what each asks for.

Light can reach it from any side, from any number of beams, so a prism
also joins several emitters into one network: a block it's linked to can take more than any one of
them could send. It refocuses and taxes light as a mirror does.

Link it with the <ItemLink id="quantimium:laser_linker" />: use the Linker on the prism to
pick it, then on each block its light should reach, once to link it and again to unlink it. A prism
links up to 8 blocks, within 24 blocks. Sneak-use
the Linker in the air to let the prism go, and sneak-click the prism with an empty hand to let go of
all its links. A link to a block that's been removed, even one light passes through such as grass, is let go of by itself. Holding the Linker, or a wrench, shows every beam faintly,
even ones carrying nothing, so you can see where light would go.

## Optical cavities

A <ItemLink id="quantimium:cavity_mirror" /> facing a <ItemLink id="quantimium:laser_mirror" />, or another Cavity
Mirror, across a clear gap of up to 16 blocks holds light between them,
as a real laser's cavity does: a battery for a laser network. Only one end needs to be a Cavity Mirror.
Each Cavity Mirror holds 10,000,000 FE for each block of gap, and takes or
gives up to 512,000 FE/t; a Laser Mirror at the other end holds nothing.

With nothing facing it, a Cavity Mirror is just a mirror: light that reaches it leaves by its back.

- Light that reaches a cavity mirror from outside charges it, but only with what the network's
  emitters have left over once its blocks are served. One cavity never fills another.
- Light leaves by the mirror's **back**, its output coupler, for the blocks that ask for it. A network
  serves its blocks from its emitters first, then from its cavities. Use a wrench on a mirror to turn
  its back output off, or on again: a mirror with its output off only stores. Sneak-click a face with
  an empty hand to point the mirror there.
- With two Cavity Mirrors, the light bounces between them, so what either holds evens out between
  them: both glow, and both can give it out.
- Light reaching the Laser Mirror at the far end is passed into the cavity, so that end can feed it.
- A cavity leaks: each tick it loses 0% of what it holds, and that
  becomes flux where it stands. Break a charged mirror, or block its gap, and the light it held all goes
  into the field at once.

The held light glows in one beam across the gap, brighter the more there is.

## Photodetectors

A <ItemLink id="quantimium:photodetector" /> sits in a beam: light passes through it and
out of its face, and it takes nothing for it. It doesn't refocus the light, so the light goes on only as
far as it had left. It lights up while light passes, and gives a redstone
signal, and a comparator reading, for how much: 1 for a trickle, two more for each tenfold, up to 15.

## What stops light

Anything solid in the way stops a beam: it ends on that block, and charges
it only if it takes power. Light passes air, glass, glass panes, and blocks with nothing to bump into.
A beam is checked again every 0.5 s or so, so building across it cuts it
off within a moment, and clearing it lets the light through again.

A run of light only reaches so far: an emitter's range, or a mirror's. A
block beyond it gets nothing.

## Networks

Emitters, mirrors, prisms, cavities and photodetectors joined by light make one network. Power goes where it's asked for: every block
the network's light reaches is charged as far as the network's emitters have power, shared between
the emitters whose light reaches it in proportion to what each has. An emitter only ever pays for the
blocks its own light reaches. Light reaches a block by the fewest mirrors it can.

Use the Flux Meter, or look with Jade, at an emitter or mirror to see what it's sending or passing on.
Beams glow brighter the more they carry. A switched-on emitter with power keeps
a dim beam lit even when nothing at its end needs power, along with the mirrors and prisms that light
reaches: it's checking for somewhere to send power, and that's what its small ready cost pays for.

Emitters whose light reaches the same block share a network, so that block
draws on all of them by what each has.
