---
navigation:
  title: Mirrors and splitters
  parent: lasers/index.md
  position: 20
---

# Mirrors and splitters

A <ItemLink id="quantimium:laser_mirror" /> is a silver plate on a steel stand. A laser reflects off it as off a real mirror: it bounces and goes on as the same laser, with its own actions, so lasers crossing one mirror never mix.

- A laser on the back or edge of the plate stops.
- A redstone signal turns the mirror off, and then it stops lasers.
- Each bounce takes 0.25% of what the laser carries (a pack can change it). It becomes flux where the laser starts.
- A mirror refocuses the laser to its source's range: each run after a bounce reaches as far as a run from the emitter that sent it.

## Setting the plate

Each face of the mirror has three parts: a middle square, a ring around it, and an outer ring, each ring split four ways by the diagonals. Click a part (by hand or with a wrench):

| Where | The plate |
| --- | --- |
| The middle square of a face | Flat, facing that face: the laser goes straight back the way it came |
| The inner ring, toward a side | Facing that face, turned 22.5° toward that side |
| The outer ring, toward a side | Halfway between that face and that side (45°): the laser turns through a right angle |

- Looking at a face outlines its parts, and a white ghost shows where the plate would turn if you clicked.
- A plate never faces toward its stand, not even a little.
- Sneak-click to move the stand: a face's middle puts it on that face, a ring puts it on the side it is toward.

## Angles

A laser travels in 26 directions: the 6 straight, the 12 flat diagonals (45° in one plane) and the 8 corner diagonals.

- Off a 45° plate a straight laser turns a right angle. Off a 22.5° plate it leaves at 45°, and a 45° laser off a 22.5° plate leaves straight.
- What leaves a mirror is always one of the 26 (the nearest, where a true reflection isn't one). A Prism's laser, at any angle, is bent onto them only there.
- A diagonal step counts its true length toward the laser's reach.
- A diagonal laser slips between two blocks that meet only at a corner, unless the `diagonalsPassCorners` config is off.
- A block takes a diagonal laser if it would take a laser through any face it comes in by.

## Laser Splitter

A <ItemLink id="quantimium:laser_splitter" /> is a Laser Mirror with a half-silvered plate, set the same way. A laser reaching it goes straight on and is reflected, off both sides of the plate: one laser becomes two, each its own, sharing the power by what each end asks for.

- Two lasers through one splitter stay apart.
- A laser never leaves a mirror or splitter through its stand. Straight into the stand, it stops.
- A splitter closes no [cavity](cavities.md).
- It refocuses and taxes the laser as a mirror does.
- **One-way.** Sneak and use a wrench on a splitter to make it one-way: its plate's
  back is steel, with the window through it. Light from the front turns and goes straight on as before; light from
  behind only goes straight through. Use it to combine two lasers without one spilling off the back of the plate.

## Entangled Mirror

An <ItemLink id="quantimium:entangled_mirror" /> sends on the laser of another laser block, any distance away, through anything, in the same dimension. It is made in pairs in the <ItemLink id="quantimium:quantum_foundry_controller" />, from a <ItemLink id="quantimium:tesseract" /> and two Laser Mirrors. Its muzzle faces you as you place it.

- Bind a Tesseract to the laser block the laser should come from, a <ItemLink id="quantimium:laser_focus" /> say (sneak and use it on that block), then use it on the Entangled Mirror. It floats in the mirror's window.
- A laser reaching that block reaches the mirror too, at once, and leaves by its muzzle, refocused, as from any mirror. The block still sends its own laser on as well.
- The jump takes 5% of what crosses (a few relays' worth), as flux. While a laser leaves it, its housing glows and the Tesseract turns faster.
- Several mirrors can draw on one block. Their lasers share what reaches it, as two outputs of a relay would.
- No other laser gets into it. It only gives out what it draws.
- Both blocks must be loaded for a laser to cross. The Tesseract stays bound while one isn't, and the laser crosses again when it is back. A Tesseract bound to a block in another dimension sends nothing.
- Sneak with an empty hand to take the Tesseract out. A redstone signal switches it off.
- A Laser Focus with all its emitters switched off still passes its laser to the mirrors drawing on it. A Laser Mirror switched off by redstone passes nothing.
