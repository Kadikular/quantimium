---
navigation:
  title: Emitters
  parent: lasers/index.md
  position: 19
---

# Emitters

An emitter takes power on any face and sends it out as a laser from its outputs. Placed, it faces you, and its laser comes out toward you.

It sends only what the blocks its lasers reach ask for, up to its rate. Point one at a full battery, or at nothing, and it keeps its power apart from a small ready cost.

| Emitter | Rate | Reach per run | Holds | Ready cost |
| --- | --- | --- | --- | --- |
| <ItemLink id="quantimium:flash_emitter" /> | 200 FE/t | 8 blocks | 20,000 FE | none |
| <ItemLink id="quantimium:laser_emitter_1" /> | 2,000 FE/t | 16 blocks | 200,000 FE | 2 FE/t |
| <ItemLink id="quantimium:laser_emitter_2" /> | 32,000 FE/t | 32 blocks | 3,200,000 FE | 16 FE/t |
| <ItemLink id="quantimium:laser_emitter_3" /> | 512,000 FE/t | 64 blocks | 51,200,000 FE | 128 FE/t |

## Flash Emitter

The Flash Emitter can burn furnace fuel as well as take FE. Each tick of burn time makes 100 FE, so a coal gives 160,000 FE. It starts a new fuel only when it has room for what that makes. Use it for power before you have a generator.

## Solar Emitter

The <ItemLink id="quantimium:solar_emitter" /> turns sunlight into a laser through its glass.

- It makes 20 FE/t in full sun. It reads the sky as a daylight detector does, so dawn, dusk and rain give less. It makes none at night or under a roof.
- It holds no power.
- Lasers that reach it from any source (another Solar Emitter or a Laser Emitter, with or without mirrors) join its own and leave by its emitter. No more than 160 FE/t leaves it in all, its own included, so a row of eight in full sun gives as much as a row can.
- Its laser reaches 8 blocks per run.
- Placed, it shines toward you. Sneak-click a side (by hand or with a wrench) to send its laser there. It can't shine up, through its glass.

## Outputs and redstone

Sneak-click a face with an empty hand, or use any wrench on it, to add or remove an output there. One emitter can shine from all six faces. It always keeps at least one.

Open an emitter to see what it is sending and to set how it listens to redstone: not at all, on only with a signal, or off with a signal.
