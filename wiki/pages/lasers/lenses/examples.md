# Lens examples

## Wheat farm

[[structure:laser_farm]]

A laser aimed down at farmland, through a Laser Port set to both and holding seeds, and a Lens Mount with Harvest, Pickup, Placement and a Block Filter Lens set to ripe only (in that order). Only ripe wheat is cut. It comes back up to the port, and the spot is sown again. Spread it over a whole field with an [[block:area_prism]].

A cut waits until the ports up the laser would take all of its drops, seeds included, so give the seeds somewhere to go. Above, a collecting port catches the harvest and a sending port below it holds the seed.

## Ore miner

An [[block:area_prism]] over the ground with a Lens Mount of Harvest, Pickup, Phantom and a Block Filter Lens set to ores. Phantom lets the laser pass through rock, the filter picks out only the ore, and Pickup sends it to a collecting [[block:laser_port]].

## Mob grinder

Damage, Pickup and an Entity Filter Lens set to hostile mobs, aimed across the kill zone. What dies drops straight back up the laser to a collecting Laser Port. Add Harmonic Lenses for Looting.

## Auto-smelter

Extract, Smelting, with a collecting Laser Port: the laser ends on a chest, and items leave it smelted on the way to the port, for 200 FE per stack plus 25 FE per item.

## Trash can

Insert with a Void Lens set to Void all, with the laser ending on any inventory. Whatever a sending Laser Port sends is destroyed.

## Personal healing aura

An [[block:area_prism]] with Healing and an Entity Filter Lens listing only you: it heals you and nobody else in the area.

## Faster machine

Acceleration after one Speed Lens runs the machine the laser ends on at 4x for 1,500 FE/t.
