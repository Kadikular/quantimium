# Fold Chamber

A multiblock left standing as a catalyst would still tick and still be usable, and nothing stops
another mod's machines from doing either. So the Fold Chamber **folds** it: the whole structure goes
into a [[item:tesseract]], which becomes a [[item:folded_tesseract]]. Folded, a machine can't tick or
be touched, and in a [Quantum Crafter](../machines/quantum-crafter.md) it is a catalyst for
everything the machine makes. Unfold it in a chamber to stand it up again.

For now the chamber folds our own [Quantum Foundry](quantum-foundry.md). Other mods' multiblocks
come one by one, each with its own *fold adapter* that knows when that machine is formed, idle and
empty, and what about it to keep.

[[render:fold_chamber]]

[[structure:fold_chamber]]

## The frame

[[mechanic:fold.chamber_frame]] The frame marks out a box and **is** the volume:

- a [[block:fold_pylon]] on each of the eight corners;
- [[block:fold_rail]]s along the twelve edges between them. A rail turns to its edge as you place
  it, and rails in a row read as one tube;
- the [[block:fold_core]] in the middle of any bottom edge, in place of a rail. Place it from
  outside the chamber, facing you; the Tesseract floats over its socket on top.

Each side is {{c:FoldChamber.MIN_SIZE}} to {{c:FoldChamber.MAX_SIZE}} blocks long, built to size on
its own, and the width (along the core's edge) is odd so the core sits in the middle. The core reads
the width from the rails either side of it, then the height and depth from the rails up and back
from a front corner; every other edge has to match. A whole frame lights up.

**Everything inside the box folds, its faces included; only the edges are frame.** So build the
frame *round* the machine, a block clear of it on every side it reaches. A Foundry, 7×7 and two tall,
takes a **9 × 3 × 9** chamber, standing on the ground level with the bottom rails and centred four
blocks back from the core. A 7-wide chamber is too narrow: the Foundry's arms would reach the side
faces, and their tanks would sit on the top edges where rails have to go. If a block stands where
the frame's edge should be, the core says which.

## Folding

1. Build the machine inside and let it form.
2. **Bind a [[item:tesseract]] to its controller**: sneak-use it on the controller. That tells the
   chamber which block is the machine.
3. Put the Tesseract in the core's socket and press **Fold**.

[[mechanic:fold.core]] The core's screen says what the chamber makes of its socket, twice a second:
why it can't fold, or how many blocks a fold would take and what it costs. Nothing moves until you
press the button. Then the chamber **seals** for {{c:FoldCoreBlockEntity.SEAL_TICKS|s}}, with the
socket locked, checks everything again and folds the whole volume at once. Folding costs
**{{c:Folding.FE_PER_BLOCK}} FE a block**, from a {{c:FoldCoreBlockEntity.ENERGY_CAPACITY}} FE
buffer that takes up to {{c:FoldCoreBlockEntity.MAX_RECEIVE}} FE/t from any side; **unfolding is
free**. The screen keeps it short; hover the status for the whole message.

[[mechanic:fold.fold_and_unfold]] The Tesseract comes out a **Folded Tesseract**, with the
controller turning inside its violet shell. Its tooltip says what it holds, the box it was folded
from and how much room it needs, east–west × height × north–south.

### When it won't fold

[[mechanic:fold.refusals]] The rules for this first version are strict on purpose: moving contents
across a fold is the classic way to duplicate them, so **contents never cross a fold**. The chamber
refuses, and says why, when:

- the Tesseract isn't bound, or is bound to something outside the chamber, or to a block no fold
  adapter knows. (An unbound Tesseract in an empty chamber would collapse into a Singularity. Not
  yet.)
- the machine isn't formed, is part way through a recipe, or holds items;
- any other block in the volume holds items, fluid or energy;
- anything in the volume is unbreakable, or on the `#quantimium:unfoldable` tag (for pack makers).

A Foundry's own energy doesn't refuse a fold: the Foundry only takes power in, so there would be no
way to empty it. **It is lost** when the Foundry folds.

## Unfolding

[[mechanic:fold.unfold_rules]] Put a Folded Tesseract in a core and press **Unfold**. It needs:

- a chamber with **room for the machine's blocks**, clear of its frame. The chamber it was folded
  in doesn't matter: a Foundry folded in a roomy 13 × 5 × 13 still unfolds in a 9 × 3 × 9. Nor
  does which edge the core is on;
- every cell the machine needs to be **empty**. The core names the first block in the way.

A fold never rotates: the machine stands up the way it was built, north still north, centred in
the chamber, as high off its floor as it stood before if there's room, else as low as fits. It forms again from scratch, so nothing in it remembers where it
used to be. You get the Tesseract back, **bound to the controller** where it
now stands, ready to fold it again.

## In a Quantum Crafter

[[mechanic:fold.crafter_catalyst]] A Folded Tesseract in a [Quantum Crafter](../machines/quantum-crafter.md)'s
catalyst slot runs every recipe the machine does, at once. A folded Foundry makes the Foundry's
recipes, but only those its arms allow: one folded with two arms won't make a three-arm recipe. Like
any multiblock catalyst it needs a Critical field. It is priced at the energy the Foundry itself
would spend on the recipe, taxed like any instant craft, and shows up as patterns for an
[ME Superposition Crafter](../machines/me-superposition-crafter.md) too.
