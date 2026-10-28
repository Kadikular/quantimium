# Networks and rules

## Into the mirror


[[mechanic:laser.rift]] Laser light along a [[block:rift_lens]]'s axis goes through the tear in its middle
into the mirror, and through another rift back out of it. The tear opens while lit light passes. A rift
doesn't refocus the light or take anything for it. Light in the mirror:

- acts only on what's in the mirror. Harvest takes a fully grown [[block:anomalite_crystal]] apart, on the
  mirror's side, for its shards (fifteen seconds at 60 FE/t), and the tear leaks anomaly and now and
  then a mirror mite. Without Pickup the shards fall on the mirror's side: only a phased
  player sees them or can take them, and only mirrored Pickup light carries them off. Damage hits only the
  Veiled and mirror mites. Placement does nothing.
- draws [[item:veil_thread]] with Pickup: through a Containment Hall's glass, into the cell where a Veiled
  is held, it draws a thread every three minutes at 120 FE/t, one draw to a hall at a time, and the thread goes
  up the beam to a Laser Port.
- charges nothing of the real world: a machine just stops it.
- is stopped by solid blocks as any light is: blocks are solid in the mirror too.
- can only be seen from in the mirror, or through a [[item:mirror_lens]].

Light passes a Containment Hall's attuned glass, as it does any glass.

## What stops light


[[mechanic:laser.blocked]] Anything solid in the way stops a beam: it ends on that block, and charges
it only if it takes power. Light passes air, glass, glass panes, water, and blocks with nothing to bump into.
A beam is checked again every {{c:LaserNetworks.RECHECK_TICKS|s}} or so, so building across it cuts it
off within a moment, and clearing it lets the light through again.

[[mechanic:laser.range]] A run of light only reaches so far: its source's range, or less for light with an
action, or more with Range lenses. A block beyond it gets nothing.

## Networks


Emitters, mirrors, relays, prisms, cavities, photodetectors, Lens Mounts and Laser Ports joined by light make one network. Power goes where it's asked for: every block
the network's light reaches is charged as far as the network's emitters have power, shared between
the emitters whose light reaches it in proportion to what each has. An emitter only ever pays for the
blocks its own light reaches. Light reaches a block by the fewest relays it can.

[[mechanic:laser.network.incremental]] When a beam's action changes the world along it (a block cut, one
placed, a door opened), only the network that beam belongs to is worked out again; every other network in
the level carries on as it was. Placing or breaking a laser block works everything out again.

Use the Flux Meter, or look with Jade, at an emitter, mirror or relay to see what it's sending or passing on.
[[mechanic:laser.live]] Beams glow brighter the more they carry. A switched-on emitter with power keeps
a dim beam lit even when nothing at its end needs power, along with the mirrors, relays and prisms that light
reaches: it's checking for somewhere to send power, and that's what its small ready cost pays for.

[[mechanic:laser.share]] Emitters whose light reaches the same block share a network, so that block
draws on all of them by what each has.
