The Quantum Exciter turns FE into flux on purpose, to hold a chunk's field at a band. It is the early way to raise the field.

- **Yield:** {{c:QuantumExciterBlockEntity.EFFICIENCY}} times as much flux per FE as ordinary work.
- **Aim:** click it to switch between Medium and High.
  - Medium: aims for {{c:QuantumExciterBlockEntity.TARGET}} flux for {{c:QuantumExciterBlockEntity.FE_PER_TICK}} FE/t.
  - High: aims for {{c:QuantumExciterBlockEntity.TARGET_HIGH}} flux for {{c:QuantumExciterBlockEntity.FE_PER_TICK_HIGH}} FE/t. A thousand FE/t holds a chunk there.
- It works flat out at no flux and eases off to nothing as the chunk reaches its aim.
- Buffer {{c:QuantumExciterBlockEntity.ENERGY_CAPACITY}} FE, taking up to {{c:QuantumExciterBlockEntity.MAX_RECEIVE}} FE/t.

The next steps up are the [Flux Maintainer and Field Regulator](../field-control/overview.md). To hold flux down instead, see the [[block:flux_suppressor]].
