---
navigation:
  title: Optical cavities
  parent: lasers/index.md
  position: 22
---

# Optical cavities

A <ItemLink id="quantimium:cavity_mirror" /> is an attachment of a <ItemLink id="quantimium:laser_focus" />: a flat half-silvered
plate on one face, facing out. Light out of it that comes back to it is held there,
as in a real laser's cavity: a battery for a laser network. It comes back off a flat <ItemLink id="quantimium:laser_mirror" /> facing
it, or from another Cavity Mirror facing back; and it can run round any
number of plain Laser Mirrors on the way, to a corner or several. Only one end needs to be a Cavity Mirror. A Laser
Splitter, a mirror switched off by redstone, or anything else in the way, and there's no cavity: what it held goes
as flux. The light may run up to 16 blocks one way (a loop of twice that), counting
each mirror it turns at as a block. Each Cavity Mirror holds 10,000,000 FE for each of
those blocks, and takes or gives up to 512,000 FE/t; a Laser Mirror at the other end holds nothing.

One focus can hold several Cavity Mirrors, each on its own face, each finding its own cavity and holding its own
light. What they hold leaves by the focus's Emitters, for the blocks that ask for it; with no Emitter it only stores.
Light reaching the focus passes on by its Emitters as ever, and what the network's emitters have left over charges
its cavities. Cavity Mirror blocks from before become a Laser Focus with a Stand, a Cavity Mirror on the same face,
and an Emitter on each of their outputs, holding what they held.

- Light that reaches a cavity mirror from outside charges it, but only with what the network's
  emitters have left over once its blocks are served. One cavity never fills another.
- A network serves its blocks from its emitters first, then from its cavities.
- With two Cavity Mirrors, the light bounces between them, so what either holds evens out between
  them: both glow, and both can give it out.
- Light reaching the Laser Focus at the far end is passed into the cavity, so that end can feed it.
- A cavity can leak: a pack can set it to lose a share of what it holds
  each tick, up to a cap, as flux where it stands. Out of the box it doesn't leak. Break a charged
  mirror, or block its gap, and the light it held all goes into the field at once.

The held light glows in one beam across the gap, brighter the more there is.

**A cavity keeps what its light does.** Fill it with Harvest light and
the light it gives out harvests too; it reaches as far as the light that filled it. The cavity pays for
what its light does, from what it holds. Light from different sides mixes in it: it does all of it,
until it's empty and the next light to fill it decides.
