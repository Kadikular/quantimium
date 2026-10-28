# Emitters


[[mechanic:laser.power]] An emitter takes power on any face and sends it out as light from its
outputs. Placed, it faces you, and its light comes out toward you.

[[mechanic:laser.demand]] It sends only what the blocks its beams reach ask for, up to its rate: point
one at a full battery, or at nothing, and it keeps its power, but for a small cost of being ready.

| Emitter | Rate | One run reaches | Holds | Ready cost |
| --- | --- | --- | --- | --- |
| [[block:flash_emitter]] | 200 FE/t | 8 blocks | 20,000 FE | none |
| [[block:laser_emitter_1]] | 2,000 FE/t | 16 blocks | 200,000 FE | 2 FE/t |
| [[block:laser_emitter_2]] | 32,000 FE/t | 32 blocks | 3,200,000 FE | 16 FE/t |
| [[block:laser_emitter_3]] | 512,000 FE/t | 64 blocks | 51,200,000 FE | 128 FE/t |

[[mechanic:laser.flash]] The **Flash Emitter** can also burn furnace fuel: each tick of burn time
makes {{c:LaserTuning.FLASH_FE_PER_BURN_TICK}} FE, so a coal gives 160,000. It's power for a base that
has no generator yet. It only starts a new fuel once it has room for what that makes.

[[mechanic:laser.solar]] A [[block:solar_emitter]] gathers sunlight through its glass into a small focus
and sends it out of a simple emitter on one side as light: {{c:LaserTuning.SOLAR_FE}} FE/t in full sun,
less as the sky dims (it reads the sky as a daylight detector does: dawn, dusk and rain give less), none
at night or under a roof. It holds nothing. Light that reaches it from any laser (another Solar Emitter,
a Laser Emitter, by mirrors or not) joins its own and leaves by its emitter, but no more than
{{c:LaserTuning.SOLAR_CHAIN_MAX}} FE/t leaves it in all, what it makes included: so a row of eight in full
sun is as much as one row gives. Its light reaches {{c:LaserTuning.SOLAR_RANGE}} blocks a run. Placed, it
shines toward you; sneak-click a side (by hand or with a wrench) to send its light there, but never up,
through its glass.

[[mechanic:laser.outputs]] Sneak-click a face with an empty hand, or use any wrench on it, to add an
output there, or take one away. One emitter can shine from all six faces; it always keeps at least one.

[[mechanic:laser.redstone]] Open an emitter to see what it's sending and to set how it listens to
redstone: not at all, on only with a signal, or off with a signal.

