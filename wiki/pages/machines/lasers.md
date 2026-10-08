# Lasers

*Light kept in step carries power across a room.*

Laser emitters send power as light. A beam crosses open air, glass and anything you can walk
through (grass, torches, flowers), and charges the first block it meets that takes power, from any
mod. Mirrors turn the light around corners. This is the first part of Quantimium's lasers; lenses that
turn a beam's end into a tool, and items carried by light, come later.

!!! info "Badges"
    Badges like [[mechanic:laser.power]] show whether an in-game test backs the rule next to them. See
    [Mechanics coverage](../reference/coverage.md).

## Emitters

[[mechanic:laser.power]] An emitter takes power on any face and sends it out as light from its
outputs. Placed, its light goes the way you were looking.

[[mechanic:laser.demand]] It sends only what the blocks its beams reach ask for, up to its rate: point
one at a full battery, or at nothing, and it keeps its power, but for a small cost of being ready.

| Emitter | Rate | One run reaches | Holds | Ready cost |
| --- | --- | --- | --- | --- |
| [[block:flash_emitter]] | 100 FE/t | 8 blocks | 20,000 FE | none |
| [[block:laser_emitter_1]] | 2,000 FE/t | 16 blocks | 200,000 FE | 2 FE/t |
| [[block:laser_emitter_2]] | 32,000 FE/t | 32 blocks | 3,200,000 FE | 16 FE/t |
| [[block:laser_emitter_3]] | 512,000 FE/t | 64 blocks | 51,200,000 FE | 128 FE/t |

These are starting values for the first playtest.

[[mechanic:laser.flash]] The **Flash Emitter** can also burn furnace fuel: each tick of burn time
makes {{c:LaserTuning.FLASH_FE_PER_BURN_TICK}} FE, so a coal gives 64,000. It's power for a base that
has no generator yet. It only starts a new fuel once it has room for what that makes.

[[mechanic:laser.outputs]] Sneak-click a face with an empty hand, or use any wrench on it, to add an
output there, or take one away. One emitter can shine from all six faces; it always keeps at least one.

[[mechanic:laser.redstone]] Open an emitter to see what it's sending and to set how it listens to
redstone: not at all, on only with a signal, or off with a signal.

## Mirrors

[[mechanic:laser.mirror]] A [[block:laser_mirror]] sends every beam that reaches it out of its face.
Placed, it faces the way you were looking; sneak-click or wrench a face to point it there. It refocuses
the light, so a run from a mirror reaches {{c:LaserTuning.RELAY_RANGE}} blocks whatever sent it.
Each mirror a beam passes takes {{c:LaserTuning.HOP_TAX|pct}} of what passes, and that becomes flux
where the mirror stands.

## What stops light

[[mechanic:laser.blocked]] Anything solid in the way stops a beam: it ends on that block, and charges
it only if it takes power. Light passes air, glass, glass panes, and blocks with nothing to bump into.
A beam is checked again every {{c:LaserNetworks.RECHECK_TICKS|s}} or so, so building across it cuts it
off within a moment, and clearing it lets the light through again.

[[mechanic:laser.range]] A run of light only reaches so far: an emitter's range, or a mirror's. A
block beyond it gets nothing.

## Networks

Emitters and mirrors joined by light make one network. Power goes where it's asked for: every block
the network's light reaches is charged as far as the network's emitters have power, shared between
them in proportion to what each has. Light reaches a block by the fewest mirrors it can.

Use the Flux Meter, or look with Jade, at an emitter or mirror to see what it's sending or passing on.
Beams glow brighter the more they carry; light that carries nothing isn't drawn.
