# Channels

Channels let several setups share one laser path without getting in each other's way. There are sixteen, 0 to 15, and
**All**. Everything starts on channel 0, so a laser where nothing is set works as it always has.

## Lenses

[[mechanic:laser.channels.streams]] Each slot of a [[block:lens_mount]] has a channel, shown above its lens. Click it
for the next, right-click for the one before. An emitter on a Laser Focus or Relay has its lens's channel on a tab of its
screen.

- An action lens gives the laser its action on that channel. Harvest on 0 and Harvest on 3 are two separate actions.
- A modifier lens changes only the actions on its channel: a Filter on 3 filters channel 3's Harvest, not channel 0's.
  On **All**, it changes every action, as before channels.
- An action on **All** belongs to every channel, and only modifiers on All change it.
- Range, UV and Phantom change the laser itself: their slot shows All, grayed out.
- Where lasers join, the same action on the same channel becomes one, paid for by both. On different channels they stay
  apart.
- A Cavity Mirror keeps each action's channel.

[[mechanic:laser.channels.isolator]] An [[item:isolator_lens]] stops only what's on its slot's channel; the rest passes
untouched. On **All**, it stops everything it's set to.

## Ports and logistics

[[mechanic:laser.channels.ports]] A [[block:laser_port]] or [[block:laser_tank]] has a channel too, on a tab on its
screen. Insert, Extract, Pickup and the rest use only the ports and tanks on their own channel up the laser. A port on
**All** serves every channel.

[[mechanic:laser.channels.logistics]] A Logistics Receptacle joins a Logistics Controller only on the same channel, or a
controller on **All**. A receptacle belongs to one network, so it can't be on All itself.

A controller starts on **All**: it runs a separate network for each channel its receptacles are on, each with its own
stock and moves, all paid from its store. Receptacles all on channel 0 are one network, as before channels. Set to a
channel, it runs only that one. Two controllers on different channels can share mirrors and Focuses, each with its own
receptacles. Set a Receptacle's channel on its screen, and a controller's on its own screen.

- A [[block:logistics_terminal]] shows the network on its channel, set on a tab of its screen.
- A [[block:logistics_materialiser]] offers every network whose laser reaches it, whatever their channels: what each
  holds, together, and what it takes in goes to the first with room. Speed lenses quicken only the receptacles on their channel.

Power is shared: channels don't divide it.
