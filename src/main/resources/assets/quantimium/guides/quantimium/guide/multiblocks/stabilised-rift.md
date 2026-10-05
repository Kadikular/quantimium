---
navigation:
  title: Stabilised rift
  parent: multiblocks/index.md
  position: 18
item_ids:
- quantimium:rift_anchor
- quantimium:rift_stabiliser
---

# Stabilised rift

A rift held open on purpose, as a steady supply of <ItemLink id="quantimium:rift_residue" /> for your
[Containment Halls](containment-hall.md).

## Building one

1. Place a <ItemLink id="quantimium:rift_anchor" /> with two blocks of air above it.
2. Use a <ItemLink id="quantimium:rift_seed" /> on it to open a stage 1 rift. Or place the anchor under a natural rift:
   it takes over any rift whose footing is one or two blocks above it and at most one block to the
   side.
3. Stand <ItemLink id="quantimium:rift_stabiliser" />s around it:
   - between 2 and
     6 blocks out horizontally (counted like a square, so the
     diagonal corner at six out counts too), from 2 below to
     4 above the anchor;
   - each with a clear line from its emitter to the rift. Other
     stabilisers and the anchor do not block it; walls do. A stabiliser that cannot see the rift is
     simply not used, so it never takes a place from one that can;
   - the anchor uses the nearest 6
     that can see it. Any more stand idle and draw nothing.
4. Power them.

**Anomalite crystals don't block sight.** They grow on the array's
powered blocks near a hot rift, but they are mirror growth showing through, not real matter: a
stabiliser under crystals still sees the rift. The crystals still drain its power, so an array left
to crystallise can still fall short of three paying stabilisers.

Each stabiliser's emitter is a small compound of two cubes, each one's
corners poking through the other's faces, spinning a third of a block over its cap. It is murky while
idle and burns bright while the stabiliser beams at the rift, so a pillar that isn't paying stands out.

With at least **3** stabilisers powered and in sight, the
rift is **held**. Stabilisers only draw power once enough of them can see it. Each costs
**60 + 40 × stage FE/t**: 100 at stage 1, 220 at stage 4.

Stabilisers emit **1 flux per 1,000 FE** they spend, once a second, as a
generator does: pure flux, no anomaly, so a held rift's field stays free of anomaly. Four at stage 4 (880 FE/t)
put about 18 flux a second into the neighbourhood.

## A held rift

- It grows a stage every 10 minutes up to stage 4, whatever the flux, and even inside a hall's field.
- It has no mites, no tears, no anomaly leak and no Veiled. It still burns and throws
  you on contact, but never through into the mirror, and its lightning only reaches
  1.5 blocks.
- Decoherence Projectors ignore it; a Lance can still close it.

## Residue

It makes residue at **stage × paying stabilisers**. At stage 4, four stabilisers make one
every 4 minutes (a small surplus over one hall's 5) and six make one every 2.7. The anchor stores up
to 64; take it from the anchor's screen, by hopper, or through a
bound Tesseract in a hall. Full, the rift stays held but makes nothing until some is taken.

## The anchor's screen

Use the anchor (without a seed in hand) to open it:

- the rift's stage, out of 4;
- how many stabilisers are paying, out of six, or how many more it needs;
- the rate in residue an hour, and a bar and countdown to the next one;
- the first reason any stabiliser in reach isn't helping (without power, can't see the rift, or spare
  beyond six); hover it for the rest when there's more than one;
- the residue slot. Residue can be taken out but not put back.

| Status | Meaning |
| --- | --- |
| No rift | Nothing stands on the anchor |
| Loose | Fewer than three stabilisers paying: not held, no residue |
| Held | Held, and making residue |
| Full | Held, but the residue slot is full |

## When it fails

If fewer than three stabilisers are paying (power, a blocked line, a broken pillar) for
3 seconds, it becomes an **ordinary rift again**, at the stage it reached, hazards and all.
