[[mechanic:laser.lens.place]] Placement Lens adds the Placement action to a laser. The laser places a block a Laser Port up the laser sends, in the space where it ends, as you would against the block it ends on.

## How it works

- Places at most one block every {{c:LaserTuning.PLACE_COOLDOWN|s}}.
- Seeds count: on farmland they are sown. A bucket of water or lava empties there, and the empty bucket goes back to the port.
- An Filter Lens in the mount says what it places.
- Through an [[block:area_prism]] it fills each free space resting on a block.
- It does nothing in the mirror.

## Energy

{{c:LaserTuning.PLACE_FE}} FE per block placed (x{{c:LaserTuning.AREA_COST}} through an Area Prism).

## Works with

| Modifier | Effect |
| --- | --- |
| [[item:amplify_lens]] | No effect. |
| [[item:speed_lens]] | Places twice as often per lens (10 ticks at base). |
| [[item:filter_lens]] | Places only the listed items. |
| [[item:pickup_lens]] | No effect. |
| [[item:void_lens]] | No effect. Placement never destroys what it can't place. |
| [[item:smelting_lens]] | No effect. |
| [[item:crushing_lens]] | No effect. |
| [[item:precision_lens]] | No effect. |
| [[item:harmonic_lens]] | No effect. |
| [[item:unrealising_lens]] | No effect. |

Range, Phantom, UV and Isolator work on every action: see [Lenses](../lasers/lenses/overview.md#every-action).

## Examples

- **Wheat farm.** Placement re-sows what Harvest cuts. See [Examples](../lasers/lenses/examples.md#wheat-farm).
