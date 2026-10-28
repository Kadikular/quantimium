# Laser Ports

[[mechanic:laser.port.collect]] A [[block:laser_port]] passes a laser, in straight by its back and out of its face, as a relay does. It holds nine slots and starts out **collecting**.

- It catches what a Pickup Lens further along brings back, for pipes and hoppers to take from, or you can take from it yourself.
- Put it between the emitter and the mount: emitter, Laser Port, Lens Mount with Harvest and Pickup, then the blocks to break.
- What one port can't take goes on up the laser to the next. A stream from one side of a join goes up its own side, or to ports after the join.
- [[mechanic:laser.port.filter]] Open it to set a filter on what it catches: nine entries, as a whitelist or a blacklist, with tags. What it turns away goes on up the laser.

[[mechanic:laser.port.send]] Set to **send**, it works the other way round. What is in its slots goes down the laser, to a Placement, Insert or Interact lens further along. Otherwise, once a second a stack goes to the nearest collecting port down the laser with room, not past an Isolator.

- The laser reaching it pays to carry the items, and only while it is lit. A switched-off relay before it, or on the way, stops it.
- A bound Tesseract in a slot sends from the inventory it links to.

[[mechanic:laser.port.mode]] The mode tab steps through **collect**, **send** and **both**. In both, what comes back up the laser can go straight back down: a port of seeds on a wheat field catches the harvest and sows the field again from it.

## Sides

Use the **Sides** tab to set, face by face, what pipes and hoppers may do there (hand things out, take them in; things only go in while it sends), and which faces push to, or pull from, what is beside them, once a second.

## Laser Tanks

[[mechanic:laser.tank.collect]] A [[block:laser_tank]] is a Laser Port for fluids. It passes a laser the same way and holds {{c:LaserTankBlockEntity.TANKS}} tanks of {{c:LaserTankBlockEntity.CAPACITY}} mB, one fluid in each. Collecting, it catches what an Extract Lens drains.

[[mechanic:laser.tank.send]] Sending, what it holds goes down the laser to an Insert Lens. Otherwise, once a second up to {{c:LaserTuning.SHIP_FLUID_MB}} mB go to the nearest collecting tank down the laser with room, not past an Isolator.

[[mechanic:laser.tank.pipes]] Pipes drain it on any face, and fill it only while it sends. A bucket used on it, or clicked on a tank in its screen, fills or empties it in any mode.

[[mechanic:laser.tank.filter]] Its filter lists fluids it catches: nine entries, as a whitelist or a blacklist. Click an entry with a bucket, or drag a fluid in from JEI. What it turns away goes on up the laser.
