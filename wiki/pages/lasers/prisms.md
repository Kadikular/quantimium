# Prisms

[[mechanic:laser.prism.split]] A [[block:laser_prism]] sends the laser that reaches it to each block it is linked to, in a straight line at any angle. It shares the laser by what each block asks for.

- The laser runs to the middle of the face you linked the block by, and goes in by that face: the side of a machine it charges, or a Laser Port's or Lens Mount's back.
- From a Prism level with that face, or in front of it, the laser meets the side it comes to first instead, and does what a laser would there.
- A Laser Focus, Mirror or Prism, whose focus is at its middle, is lit at its middle whatever face you clicked.

[[mechanic:laser.prism.join]] Lasers can reach a prism from any side and from any number of emitters, so a prism also joins emitters into one network: a linked block can take more than any one emitter could send. It refocuses and taxes the laser as a relay does.

## Linking

[[mechanic:laser.prism.links]] Link a prism with the [[item:laser_linker]].

- Use the Linker on the prism to pick it, then on each block its laser should reach: once to link it, again to unlink it.
- A prism links up to {{c:LaserTuning.PRISM_LINKS}} blocks, within {{c:LaserTuning.PRISM_RANGE}} blocks.
- Sneak-use the Linker in the air to let the prism go. Sneak-click the prism with an empty hand to drop all its links.
- A link to a block that has been removed, even one a laser passes through such as grass, is dropped by itself.

[[mechanic:laser.prism.keep]] Use an empty hand on a prism to make it keep its links instead. It keeps aiming at the spot, so a Harvest laser breaks whatever comes back there.

Holding the Linker or a wrench shows every laser faintly, even ones carrying nothing, so you can see where lasers would go.

## Area Prism

[[mechanic:laser.area_prism]] An [[block:area_prism]] spreads the actions of the laser reaching it over an area of any size.

- **Harvest** cuts each block in the area in turn. **Damage** hurts, and **Pickup** gathers, whatever is in it. **Placement** fills each free space resting on a block.
- It works only on what the laser reaching it reaches (8 blocks for a laser with an action, more with Range Lenses) and can see along a clear line from the prism.
- While it works, it shows a laser into what it is working on, following what it is hitting as it moves.
- Work in an area costs {{c:LaserTuning.AREA_COST}} times as much for a cut or a placement.

### Setting the area

- Use the [[item:laser_linker]] in the air to switch it to area mode, use it on two corners, then on the prism. The area shows as you pick it, and holding the Linker shows the area of any prism you look at. Sneak-click the prism with an empty hand to clear its area.
- [[mechanic:laser.area_prism.shape]] Or open the prism and set the area to a **cube** or a **sphere** around it, with a radius.

### Order

Set in the prism's screen: **nearest first** (the prism's own height outwards, nearest columns first in each layer), **top down** (a quarry: nothing falls into what is cleared) or **bottom up**. After a reload it carries on where it was.
