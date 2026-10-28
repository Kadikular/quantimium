The Observation Chamber collapses [[item:unrealised_matter]] into real ores. Put Matter in and pulse it with redstone.

![The Observation Chamber's screen](../assets/gui/observation_chamber.png){ .qgui }

- It needs no power. Each rising redstone edge collapses up to {{c:ObservationChamberBlockEntity.MEASUREMENT_BATCH}} Matter.
- Each Matter becomes one of your pack's ores, rarer in a hotter field. See [Unrealized Matter](../items/unrealised_matter.md).
- Each collapse has a {{c:ObservationChamberBlockEntity.TRACE_CHANCE|pct}} chance of a [[item:quantimium_trace]] as well, in its own slot. The **void** tab decides what happens when that slot is full: **Void** destroys the extra Trace and carries on, **Keep** stops and says **Full** until there is room, so no Trace is ever lost.
- Slots: one for Matter, one for Trace, nine for results.
- It works in a Quantum Simulator as any machine does.

For an automated version that runs on power, see the [[block:quantum_observation_chamber]].
