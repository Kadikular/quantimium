---
navigation:
  title: Networks and rules
  parent: lasers/index.md
  position: 34
---

# Networks and rules

## Networks

Emitters, mirrors, relays, prisms, cavities, photodetectors, Lens Mounts and Laser Ports joined by lasers make one network.

- Power goes where it is asked for. Every block the network's lasers reach is charged as far as the network's emitters have power, shared between the emitters whose lasers reach it in proportion to what each has. Emitters whose lasers reach the same block share a network, so that block draws on all of them by what each has.
- An emitter only pays for the blocks its own laser reaches.
- A laser reaches a block by the fewest relays it can.
- When a laser's action changes the world along it (a block cut, one placed, a door opened), only the network that laser belongs to is worked out again. Every other network in the level carries on as it was. Placing or breaking a laser block works everything out again.
- Lasers glow brighter the more they carry. A switched-on emitter with power keeps a dim laser lit even when nothing at its end needs power, along with the mirrors, relays and prisms that laser reaches. It is checking for somewhere to send power, and that is what its small ready cost pays for.

Use the Flux Meter, or look with Jade, at an emitter, mirror or relay to see what it is sending or passing on.

## What stops a laser

Anything solid in the way stops a laser. It ends on that block, and charges it only if it takes power. Lasers pass air, glass, glass panes, water, and blocks with nothing to bump into. A laser is checked again every 5 s or so, so building across it cuts it off within a moment, and clearing it lets the laser through again.

## Reach, joining and payment

A run of laser reaches as far as its source's range. Mirrors, relays, prisms and mounts refocus it to that, not to a range of their own. A block beyond the reach gets nothing.

- A laser with an action reaches 8 blocks per run instead, and the power it carries goes no farther.
- Each <ItemLink id="quantimium:range_lens" /> doubles a run, up to 64 blocks by default (`maxRange` in the `lasers` config).
- Where lasers join, the farther reach wins.

**Joining.** Lasers from two sides joined at a relay or prism do everything either side did. Harvest from one emitter and Damage from another, joined, both work from there on. A modifier from before the join stays with its own side's actions. One after the join changes them all.

**Who pays.** An action is paid for by the sources whose laser reached the lens that gave it, and only for work done: a cut as it is made, a hit when it lands, an item as it is carried. Harvest laser joined with plain laser is still paid for by its own emitter alone. A Harvest Lens after the join is paid for by both. Actions only work while their laser is lit.

## Reading a laser

The <ItemLink id="quantimium:spectroscope" /> splits a laser into its lines. Use it on any laser block to read the
laser leaving it, or aim it at a laser in the air (up to 16 blocks off, not through walls) to read that laser. Lasers laid
over each other where you aim, like two joined at a Laser Splitter, are read together.

- **The spectrum.** Each lens the laser carries is a line at its own wavelength, from the ultraviolet on the left (the
  UV Lens) to the infrared on the right (the Phantom Lens). Power and logistics are low bands over their own
  wavelengths. Hover over a line for its lens, wavelength and channels.
- **Channels.** Pick All, or one channel the laser carries, to see only what's on it.
- **Below**, what's going on: each action's lens, then the lenses changing it (×2 for two), and its channel; then each
  Laser Port, Tank or Controller the actions use, with the actions using it. Hover over an icon for its name.
- At the bottom: how far a run reaches, from how many sources, the power it carries, and whether it's in the mirror
  or unpowered.

## Looking into a laser

A laser carrying power blinds anything whose eyes are in it, for 15 s. The <ItemLink id="quantimium:mirror_lens" /> protects the wearer.

## Into the mirror

A laser along a <ItemLink id="quantimium:rift_lens" />'s axis goes through the tear in its middle into the mirror, and through another rift back out of it. The tear opens while a laser passes. A rift doesn't refocus the laser or take anything for it.

A laser in the mirror:

- **Acts only on what is in the mirror.**
  - Harvest takes a fully grown <ItemLink id="quantimium:anomalite_crystal" /> apart for its shards (15 s at 60 FE/t). The tear leaks anomaly, and now and then a mirror mite.
  - Without Pickup, the shards fall on the mirror's side: only a phased player sees them or can take them, and only mirrored Pickup carries them off.
  - Damage hits only the Veiled and mirror mites.
  - Placement does nothing.
- **Draws <ItemLink id="quantimium:veil_thread" /> with Pickup.** Through a Containment Hall's glass, into the cell where a Veiled is held, it draws a thread every 3 min at 120 FE/t, one draw to a hall at a time. The thread goes up the laser to a Laser Port.
- **Charges nothing in the real world.** A machine just stops it.
- **Is stopped by solid blocks** as any laser is. Blocks are solid in the mirror too.
- **Can only be seen** from in the mirror, or through a <ItemLink id="quantimium:mirror_lens" />.

A laser passes a Containment Hall's attuned glass, as it does any glass.
