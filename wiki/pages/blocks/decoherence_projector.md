The Lance's beam on a pedestal. Every half-second it picks the most pressing target within
{{c:DecoherenceProjectorBlockEntity.RANGE}} blocks in clear sight of its orb: the
[Veiled](../mechanics/veiled.md#the-decoherence-projector) first, then mirror mites
({{c:DecoherenceProjectorBlockEntity.MITE_DAMAGE}} damage each half-second), then a
[rift](../mechanics/flux-rifts.md#closing-it) ({{c:DecoherenceProjectorBlockEntity.RIFT_DRAIN_PER_TICK}}
coherence a tick). Stabilised rifts are left alone.

| | |
| --- | --- |
| Watching | {{c:DecoherenceProjectorBlockEntity.IDLE_FE_PER_TICK}} FE/t |
| Beaming | {{c:DecoherenceProjectorBlockEntity.ACTIVE_FE_PER_TICK}} FE/t |
| Storage | {{c:DecoherenceProjectorBlockEntity.ENERGY_CAPACITY}} FE, up to {{c:DecoherenceProjectorBlockEntity.MAX_RECEIVE}} FE/t in |

Like any Quantimium machine it emits **1 flux per 1,000 FE** it spends, a quarter of that as anomaly,
once a second.

[[mechanic:projector.mounting]] **Mount it anywhere.** It stands on a floor, hangs from a ceiling or
sticks out of a wall, pointing away from what it's on, and it only looks out that way: it never
beams back through its own mount. Anything placed in front of it shuts it down.

[[mechanic:projector.cells]] **Its light comes from [[item:anomalite_cell]]s.** Watching costs no
charge; beaming does. The Veiled burns a Cell fastest (about one Cell for a full pin, 960 of its
1,000 charge), mites 5 a shot and rifts 1 a tick. When a Cell runs out the next loads, used up whole,
so there are no empties to collect. With no Cell it still watches, but can't beam. Put Cells in by
hand (use one on it; sneak with an empty hand to take them out), by pipe or hopper, or put a bound
[[item:tesseract]] in to draw Cells from the inventory it links to. The [[item:flux_meter]] and Jade
show the Cell's charge and how many are spare. The real world sees only the block light up; the beam
itself is drawn for phased players.
