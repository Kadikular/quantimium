---
navigation:
  title: Optical cavities
  parent: lasers/index.md
  position: 24
---

# Optical cavities

A <ItemLink id="quantimium:cavity_mirror" /> is an attachment for a <ItemLink id="quantimium:laser_focus" />: a flat half-silvered plate on one face, facing out. A laser out of it that comes back to it is held there, as in a real laser's cavity. A cavity is a battery for a laser network.

## Building one

- The laser comes back off a flat <ItemLink id="quantimium:laser_mirror" /> facing the plate, or from another Cavity Mirror facing back. Only one end needs to be a Cavity Mirror.
- It can run round any number of plain Laser Mirrors on the way, to a corner or several.
- A Laser Splitter, a mirror switched off by redstone, or anything else in the way breaks the cavity: what it held goes into the field as flux.
- The gap can be up to 16 blocks one way (a loop of twice that), counting each mirror it turns at as a block.
- Each Cavity Mirror holds 10,000,000 FE per block of gap and takes or gives up to 512,000 FE/t. A Laser Mirror at the other end holds nothing.
- With two Cavity Mirrors, the laser bounces between them, so what either holds evens out: both glow, and both can give it out.
- The held laser glows across the gap, brighter the more there is.

## Using one

- One focus can hold several Cavity Mirrors, each on its own face, each finding its own cavity and holding its own laser.
- What they hold leaves by the focus's Emitters, for the blocks that ask for it. With no Emitter, a cavity only stores.
- A laser that reaches the focus passes on by its Emitters as usual. What the network's emitters have left over, once its blocks are served, charges the cavities. One cavity never fills another.
- A network serves its blocks from its emitters first, then from its cavities.
- A laser reaching the Laser Focus at the far end is passed into the cavity, so that end can feed it.
- Old Cavity Mirror blocks become a Laser Focus with a Stand, a Cavity Mirror on the same face, and an Emitter on each of their outputs, holding what they held.

A cavity can leak: a pack can set it to lose a share of what it holds each tick, up to a cap, as flux where it stands. Out of the box it doesn't leak. Break a charged mirror, or block its gap, and everything it held goes into the field at once.

A cavity keeps what its laser does. Fill it with Harvest laser and the laser it gives out harvests too, reaching as far as the laser that filled it. The cavity pays for what its laser does, from what it holds. Lasers from different sides mix in it: it does all of it, until it is empty and the next laser to fill it decides.
