---
navigation:
  title: Anomaly Containment Hall
  parent: multiblocks/index.md
  position: 17
item_ids:
- quantimium:anomaly_containment_hall
---

# Anomaly Containment Hall

The hall is the answer to a base too hot to work in. It holds its field up to all of Singularity and
pulls anomaly down, and **only** anomaly, so a deliberately hot field keeps its flux. Its cell can also
hold [the Veiled](../mechanics/veiled.md).

<GameScene zoom="2.9" interactive={true}>
  <ImportStructure src="../assets/structures/hall.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

## Structure

Five blocks tall:

- a 3×3 ring of <ItemLink id="quantimium:quantum_foundry_plinth" /> with the <ItemLink id="quantimium:anomaly_containment_hall" />
  controller in the middle;
- three layers of <ItemLink id="quantimium:quantum_attuned_glass" /> around a hollow 1×1×3 cell;
- a 3×3 plinth cap;
- one to four attunement arms, the same as the [Foundry](quantum-foundry.md)'s.

The cell must stay clear: a formed hall breaks anything solid inside it each second.

![The Anomaly Containment Hall's screen: arms, rating, upkeep and the residue slot](../assets/gui/anomaly_containment_hall.png)

## What the arms buy

More arms, more field:

| Arms | FE/t | Holds all of | Capacity | Field | Anomaly pulled a second | Capture reach | Capture time |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 180 | High | 10,000 | 3×3 | 600 | 17 blocks | 7 s |
| 2 | 240 | Critical | 100,000 | 3×3 | 1,200 | 22 blocks | 5.75 s |
| 3 | 300 | Critical | 100,000 | 5×5 | 1,800 | 27 blocks | 4.5 s |
| 4 | 360 | Singularity | 1,000,000 | 5×5 | 2,400 | 32 blocks | 3.25 s |

Its capacity covers its field and fades sharply past it: the 5×5 hall is nearly full to its sides,
two thirds at its corners, and half three chunks out (see
[containment](../concepts/flux-and-anomaly.md)). Its screen shows the
load on its chunk. Draw is 120 +
60 per arm; the pull is
600 a second per arm from its whole field, a fifth
of it from the hall's own chunk on a 3×3. Stores 400,000 FE,
taking up to 4,000 FE/t.

## Holding the Veiled

A formed, powered hall with room takes hold of a worn-down Veiled in its reach (see
[Ending a fight](../mechanics/veiled.md)). Once held:

- it burns **1 <ItemLink id="quantimium:rift_residue" /> every 5 min**
  while holding (and only then), whatever the arm count;
- residue goes in by hand, or by hopper or pipe into **any claimed arm block**. The hall
  keeps up to 16, which is 80 minutes of holding;
- a **bound Tesseract** in the residue slot draws residue from the linked
  inventory instead. Point it at a [Rift Anchor](stabilised-rift.md) and the hall runs off a rift;
- a hall with no residue cannot take hold of anything.

Its screen says what state its **cell** is in: *waiting* (it can take one),
*taken*, *dark* (unformed or unpowered), *armless* or *hungry* (no residue); hover it for more. If a
pinned Veiled gets away beside a hall that couldn't take it, the lancer is told why: *"It slips
away. The hall nearby is hungry: it had no Rift Residue to hold it with."*, or that it was just out
of the hall's reach.

What holding one buys:

- **A stronger field.** An occupied hall drains anomaly
  1.5× as fast: a four-arm hall pulls 3,600 anomaly
  a second instead of 2,400.
- **Peace in the area.** No rift within 128
  blocks releases another Veiled, and any free one inside that radius comes apart where it
  stands and is gone. A fight with it ends there.

**Running out** starts a 30 s
grace: the field stutters, the occupant presses against the glass, and an alarm sounds every
2 seconds. Feed it in time and nothing happens; otherwise it walks out, feeding on the hall.

**Losing power** does the same, after 10 seconds. **Breaking the controller**
lets it out at once.

## Veil Thread

A <ItemLink id="quantimium:harvest_laser" /> can draw <ItemLink id="quantimium:veil_thread" /> out of the Veiled a hall holds, firing through
a <ItemLink id="quantimium:rift_lens" /> and the middle of any wall of glass. A lens right against the glass seats onto it
with a studded collar. One laser draws from a hall at a time.
