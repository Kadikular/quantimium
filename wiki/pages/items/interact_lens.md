[[mechanic:laser.lens.interact]] Interact Lens adds the Interact action to a laser. The laser uses an item a Laser Port sends, or an empty hand, on what it ends on, as a player would.

## How it works

- Acts once every {{c:LaserTuning.PLACE_COOLDOWN|s}}.
- With nothing to do, it waits twice as long each time it looks, up to 4 seconds (the `laserIdleSeconds` config option). After a use, it's back to its own pace.
- It works with what the port holds where it lies, trying each kind of thing in turn (carrots, then wheat, then shears) until one is used. Nothing moves unless using it changes it.
- [[mechanic:laser.lens.interact_fluid]] When nothing a Laser Port sends is used, it tries a bucket of what a sending [[block:laser_tank]] holds: it fills a cauldron, or pours the fluid out where it ends. Only a bucket that was emptied drains the tank.
- What it used goes back where it belongs: the same thing (a worn sword) to the sending port, anything new (a filled bucket) up the laser to a collecting one.
- Through an [[block:area_prism]] it works on the area: on living things first if it holds something for them, otherwise on blocks in turn, each from the side facing the prism.
- It repeats: behind a pulse mirror it acts once per signal.
- A tool bound to an [[block:entangled_dock]] is charged from the dock as it is used.

## Settings

Use the lens in the air, or its button in a Lens Mount.

- **Use** (right click): with something in hand, on the first living thing in the laser that takes it (feeding, shearing, milking). Otherwise on the block the laser ends on, or something it passes just before (a lever, a button): opens doors, flips levers, works items on blocks. An empty bucket fills from the fluid it ends on.
- **Hit** (left click): a swing at the first living thing in the laser, as hard as what it holds. With nothing living there, it mines the block the laser ends on as a player holding that item would: drops fall there, the tool wears, its enchantments count.
- **Works on**: blocks and living things (default), blocks only, or living things only.

## Energy

{{c:LaserTuning.PLACE_FE}} FE per use. Mining with Hit costs a Harvest Lens's FE by hardness instead.

## Works with

| Modifier | Effect |
| --- | --- |
| [[item:amplify_lens]] | No effect. |
| [[item:speed_lens]] | Acts twice as often per lens (10 ticks at base). |
| [[item:entity_filter_lens]] | Works only on the listed living things, for example only grown animals. |
| [[item:pickup_lens]] | No effect. |
| [[item:void_lens]] | Destroys what it hands back (an empty bucket, a worn tool) if no Laser Port takes it. |
| [[item:smelting_lens]] | No effect. |
| [[item:crushing_lens]] | No effect. |
| [[item:precision_lens]] | No effect. |
| [[item:harmonic_lens]] | No effect. |
| [[item:unrealising_lens]] | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md#every-action).

## Examples

- **Bone meal field.** A sending [[block:laser_port]] of bone meal, Interact in Use mode, an [[block:area_prism]] over the field.
