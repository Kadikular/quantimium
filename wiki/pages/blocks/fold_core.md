The Fold Core is the control block of a [Fold Chamber](../multiblocks/fold-chamber.md). It goes in the middle of any bottom edge of the frame, in place of a rail, and a Tesseract floats over its socket on top.

- Its screen says what the chamber makes of the socket: why it can't fold, or how many blocks a fold would take and what it costs.
- **Fold** seals the chamber for {{c:FoldCoreBlockEntity.SEAL_TICKS|s}} and folds the whole volume into the Tesseract, for {{c:Folding.FE_PER_BLOCK}} FE a block. **Unfold** is free.
- Buffer {{c:FoldCoreBlockEntity.ENERGY_CAPACITY}} FE, taking up to {{c:FoldCoreBlockEntity.MAX_RECEIVE}} FE/t from any side.
- An unbound Tesseract in an empty chamber at least 9 blocks every way collapses into a [[item:singularity]].
