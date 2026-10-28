---
navigation:
  title: Fold Core
  parent: blocks/index.md
  position: 21
  icon: quantimium:fold_core
---

# Fold Core

<ItemImage id="quantimium:fold_core" scale="2" />

Part of the [Fold Chamber](../multiblocks/fold-chamber.md).

The Fold Core is the control block of a [Fold Chamber](../multiblocks/fold-chamber.md). It goes in the middle of any bottom edge of the frame, in place of a rail, and a Tesseract floats over its socket on top.

- Its screen says what the chamber makes of the socket: why it can't fold, or how many blocks a fold would take and what it costs.
- **Fold** seals the chamber for 3 s and folds the whole volume into the Tesseract, for 2,000 FE a block. **Unfold** is free.
- Buffer 10,000,000 FE, taking up to 100,000 FE/t from any side.
- An unbound Tesseract in an empty chamber at least 9 blocks every way collapses into a <ItemLink id="quantimium:singularity" />.

## Recipes

<RecipesFor id="quantimium:fold_core" />
