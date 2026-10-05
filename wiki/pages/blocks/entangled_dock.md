A pedestal that holds an item in two places at once. Use it with anything that holds FE, a
[[item:decoherence_lance]] or another mod's tool or battery, and the item is **entangled** with it: it
stays in your hand, and a copy of it turns over the dock's cradle inside a small tesseract cell.

[[mechanic:dock.bind]] From then on the item carries the dock's mark (its tooltip says where the dock is),
and the dock looks after it.

## Charging

[[mechanic:dock.charge]] Once a second the dock finds its item on you, anywhere in your inventory, and tops
it up from its own buffer: up to **{{c:EntangledDockBlockEntity.CHARGE_PER_TICK}} FE/t**, wherever you
are, as long as the dock's chunk is loaded. Feed the dock ({{c:EntangledDockBlockEntity.ENERGY_CAPACITY}}
FE buffer, up to {{c:EntangledDockBlockEntity.ENERGY_MAX_RECEIVE}} FE/t in) and your gear never runs dry,
which is the answer to the [Veiled](../mechanics/veiled.md) draining it while it follows you.

Like any Quantimium machine it emits **1 flux per 1,000 FE** it passes on, at the dock.

The cell over the cradle shows how it is doing: **azure** while charging, **pale** while it waits (the
item is full, or not with you), **red** when the dock is out of power. Use the dock with an empty hand to
read the same on the action bar.

## Letting go

[[mechanic:dock.release]] Sneak and use the dock with an empty hand to let the item go. Entangling another
item does the same for the first: a dock holds one entanglement, and an item from an earlier one keeps
its mark but is never charged again.

[[mechanic:dock.decohere]] The entanglement does not survive a **Critical** field: at that much anomaly in
the dock's chunk it decoheres, the dock lets go, and the item simply stops being charged. Keep docks
somewhere quiet, or contained.

## Recipe

A [[item:tesseract]] over an [[item:anomaly_fragment]] between two blocks of redstone, on three
polished deepslate.
