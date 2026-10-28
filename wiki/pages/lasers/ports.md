# Laser Ports

[[mechanic:laser.port.collect]] A [[block:laser_port]] passes light, in straight by its back and out of its face, as a relay does,
and holds nine slots. It starts out **collecting**: it catches what a Pickup lens further along brings
back, for pipes and hoppers to take from, or take from it yourself. Put it between the emitter and the
mount: emitter, Laser Port, Lens Mount with Harvest and Pickup, then the blocks to break. What one port
can't take goes on up the beam to the next. A stream from one side of a join goes up its own side, or to
ports after the join. [[mechanic:laser.port.filter]] Open it to set a filter on what it catches, nine
entries, as a whitelist or a blacklist, with tags: what it turns away goes on up the beam.

[[mechanic:laser.port.send]] Set to **send**, it works the other way round: what's in its slots goes down
the beam, to a Placement, Insert or Interact lens further along, or once a second a stack at a time to the
nearest collecting port down the beam with room, not past an Isolator. The light reaching it pays to
carry it, and only while it's lit: a switched-off relay before it, or on the way, stops it. A bound
Tesseract in a slot sends from the inventory it links to.

[[mechanic:laser.port.mode]] Its mode tab steps through **collect**, **send** and **both**. In both, what
comes back up the beam can go straight back down: a port of seeds on a wheat field catches the harvest
and sows the field again from it.

Use the **Sides** tab to set, face by face, what pipes and hoppers may do there (hand things out, take
them in; things only go in while it sends), and which faces push to, or pull from, what's beside them,
once a second.
