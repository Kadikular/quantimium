[[mechanic:laser.lens.phantom]] Phantom Lens lets a laser pass through blocks. Only laser blocks, and blocks the laser charges, stop it.

## How it works

- A Harvest laser with it cuts the first block on its way it is allowed to. With a Block Filter Lens set to ores, that is only the ore in the ground. With Pickup, it passes over what no Laser Port up the laser would take.
- An [[block:area_prism]] with it reaches what it couldn't see.
- It stays on the laser.

## Energy

- Upkeep: a configurable FE (20 by default, `phantomFePerBlock`) each tick for each block the laser passes through. For an Area Prism, for each block between it and what it works on.
- The actions the laser carries cost twice as much.

## Examples

- **Ore miner.** Harvest, Pickup, Phantom and a Block Filter Lens set to ores.
