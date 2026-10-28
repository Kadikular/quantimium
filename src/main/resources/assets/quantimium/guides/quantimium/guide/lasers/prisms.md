---
navigation:
  title: Prisms
  parent: lasers/index.md
  position: 21
---

# Prisms

A <ItemLink id="quantimium:laser_prism" /> sends the light that reaches it to each block
it's linked to, along a straight line at any angle, sharing it by what each asks for. Its light runs to the middle of
the face you linked the block by and goes in by that face (which side of a machine it charges, or a Laser Port's or
Lens Mount's back). From a Prism level with that face, or in front of it, the light meets the side it comes to
first instead, and does what light would there. A Laser Focus, Mirror or Prism, whose focus is at its middle, is lit at its middle whatever face
you clicked.

Light can reach it from any side, from any number of beams, so a prism
also joins several emitters into one network: a block it's linked to can take more than any one of
them could send. It refocuses and taxes light as a relay does.

Link it with the <ItemLink id="quantimium:laser_linker" />: use the Linker on the prism to
pick it, then on each block its light should reach, once to link it and again to unlink it. A prism
links up to 8 blocks, within 24 blocks. Sneak-use
the Linker in the air to let the prism go, and sneak-click the prism with an empty hand to let go of
all its links. A link to a block that's been removed, even one light passes through such as grass, is let go of by itself.
Use an empty hand on a prism to make it keep those links instead: it goes on aiming at the
spot, so a Harvest lens's light breaks whatever comes back there.

Holding the Linker, or a wrench, shows every beam faintly, even ones carrying nothing, so you can see
where light would go.

## Area Prism

An <ItemLink id="quantimium:area_prism" /> spreads the actions of light reaching it over an
area of any size: Harvest cuts each block there in turn, Damage hurts and Pickup gathers whatever's in it,
Placement fills each free space resting on a block. It works only on what the light reaching it
reaches (8 blocks for light with an action, more with Range lenses) and can see, along a clear line from
the prism, and while it's working it shows a beam into what it's working on, following what it's hitting as it moves. Work in an area costs
1.5 times as much. Use the <ItemLink id="quantimium:laser_linker" /> in the air to switch it to area mode, use it on two
corners, then on the prism; the area shows as you pick it, and holding the Linker shows the area of any
prism you look at. Sneak-click the prism with an empty hand to clear its area. Or open
the prism and set its area to a **cube** or a **sphere** around it instead, with a radius. Its actions
work the area **nearest first** (the prism's own height outwards, nearest columns first in each layer),
**top down** (a quarry: nothing falls into what's cleared) or **bottom up**, as set in its screen; after a
reload they carry on where they were.

Work that happens at once (a hit, an item carried, a block placed) is paid for first each tick, so
machines on the same light can't starve it; blocks are charged from what's left.
