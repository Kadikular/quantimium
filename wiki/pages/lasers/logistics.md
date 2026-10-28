# Logistics


[[mechanic:laser.logistics.network]] A [[block:logistics_controller]] is a relay with a back: light in by
its back, where a focus is set, goes on out of its outputs (set as an emitter's are, never the back), with
light of its own. Light on any other side only charges it. Everything its light reaches, through mirrors, splitters and relays, joined with other light or not, is one
**logistics network**, as any light is a laser network. An Isolator lets power through but not the
network. One controller to a network: where light from two meets, both stop, go dark and say why.

[[structure:logistics]]

*A controller's light through storage, a provider and a requester, each on a chest, to a Logistics Lectern.*
[[mechanic:laser.logistics.controller]] It fills its own store, up to {{c:LaserTuning.LOGISTICS_CAPACITY}} FE,
from light ending on it as any block that takes power does, or from cables on any face. From that store it
pays {{c:LaserTuning.LOGISTICS_FE_PER_MOVE}} FE for each move its receptacles make (up to a stack), and for
nothing else: it gives no power, so it charges no machines, and lens work past it is paid for by the other
sources whose light reaches the lens, as ever. Out of power, its receptacles wait.

[[mechanic:laser.logistics.lenses]] **Lenses past the controller use the network.** Up the beam from a lens,
the controller stands for the whole network, after any Laser Port nearer the lens: what a Pickup or Extract
lens brings back goes into it (to requesters listing it, then storage), so they work as active providers;
what an Insert, Placement or Interact lens uses comes from it (providers, then storage), but only through a
Item Filter Lens, so they work as active requesters: unfiltered, they take nothing from the network (a Laser Port
up the beam still feeds them). As ever, a stream from one side of a join reaches back up its own side:
put the controller before the lenses, or on their side of the join.

A [[item:logistics_receptacle]] is an attachment of a [[item:laser_focus]]: it goes on the face against the inventory
it serves (a chest, a machine, a drawer), and works while light reaches the focus. One focus can hold several, each
serving its own inventory with its own settings. On a [[item:clear_laser_focus]] light passes straight through, so
one beam along a row of machines joins them all; on a Laser Focus the light stops there. Logistics Receptacle blocks
from before become a Clear Laser Focus with a Receptacle on the same face. Open it to set what that
inventory is to the network, its priority, and a filter of nine entries, as a whitelist or a blacklist,
with tags (empty, storage and providers take or give everything; a requester asks for nothing as a whitelist,
everything as a blacklist). Its plate lights in its role's color while a controller's
light reaches it. Each active part moves as an Insert or Extract lens does: up to 16 items a second, four times as many for each Speed lens the light met on its way to it (up to three). Idle, it looks less often, waiting twice as long each time up to 4 seconds (a config option), and half as long again after each move:

- **Storage**: where the network keeps things. [[mechanic:laser.logistics.priority]] What comes in fills
  the highest priority first, as far as its filter lets in; what's taken goes from the lowest first.
- [[mechanic:laser.logistics.provider]] **Provider**: passive, the network may take what its filter lets go
  (a furnace's output, say); active, it pushes it out itself, to requesters listing it first, then into
  storage.
  On a machine that makes things to order (a Quantum Crafter, or a [[block:materialiser]] with no output
  chosen), what it would make is offered as craftable.
  It makes them from what's really stored, one step at a time. A tab on its screen turns that off, offering
  only what's really in the machine's outputs.
- [[mechanic:laser.logistics.requester]] **Requester**: passive, it takes what active providers push, as its
  filter lets in; active, it pulls what its filter lists from providers first, then storage, what's stored
  before anything is made. A tab on an active requester says whether machines may make what isn't stored for
  it, or it takes only what's stored.
  [[mechanic:laser.logistics.filter]] Only what its filter lists comes, as far as the inventory takes it. With **Keep stocked** on, each
  entry's count (the stack you set it with; scroll over it to change, shift for 16) is how many it keeps
  there: it takes only what it's short of.

[[mechanic:laser.logistics.receptacle]] A receptacle is lit while it's joined to a controller with the power to
move things. On a machine that takes power, a tab on its screen sets it to power that machine too: the light
reaching the receptacle (an emitter's, through the controller) charges what it's on, as light ending on it would.

[[mechanic:laser.logistics.port]] A [[block:laser_port]] on the network, collecting, is a passive provider of
what it's caught. A receptacle on a Laser Port works with its nine slots whatever its faces are set to, in
any role.

Light from a controller is drawn a deeper blue, and always as if at work.

[[mechanic:laser.logistics.terminal]] A [[block:logistics_terminal]] where a controller's light ends shows
everything the network's storage holds and its providers offer, searchable and sorted by count or name,
like an ME terminal; sort and view are tabs on its left. Click an item for a stack on the cursor,
right-click for half, shift-right-click for one, shift-click for a stack into your inventory; while
shift is held the grid holds still, and shift with the wheel takes one more onto the cursor (down) or
puts one back (up). Click the grid holding something to put it away into storage (right-click: one), or
shift-click it in your inventory. What's stored comes out first: if any is, that's all you get, and only when
none is left does a machine make more. Each take or put costs the controller as a move does.

The search reads as JEI's does: words must all match, in any order; `a|b` matches either; `-word` leaves out
what matches. `@mod` matches a mod's id or name, `#text` its tooltip, `$tag` its tags: `@mekanism ingot`,
`-@minecraft`, `$c:ingots|$c:gems`.

**Crafting grid.** The crafting table tab shows a 3×3 grid under a shorter item grid, as an ME crafting terminal
has; what's in it stays in the terminal, for anyone who opens it next. Craft in it as at a crafting table: after
each craft, what it used is topped up from what the network stores, so shift-clicking the result crafts again and
again into your inventory until something runs out. With JEI, the **+** on a crafting recipe fills the grid from
what's stored, then your inventory; what neither has but a machine on the network can make (one step) goes in as
**Craft**: nothing is made until you take the result, and then just what that craft needs. One craft's worth goes
in (shift-+ too), the grid being refilled after each; a slot whose item runs out becomes a Craft, if the network can
make it. In JEI, what will be made shows blue. Drag something the network can only make from the item grid onto the
crafting grid to put it in as Craft too; click a Craft with nothing in hand to clear it. A Craft the network can no
longer make or supply goes, and a craft that can't make its Crafts isn't given. Items that are nowhere show red in
JEI, and the rest still go in. The × beside the grid puts it all back into the network.

A [[block:logistics_lectern]] is the same terminal on a stand, its screen tilted over the focus the light comes in to: craft one from the other.

[[mechanic:laser.logistics.materialiser]] A [[block:logistics_materialiser]] where a controller's light ends
offers the whole network to any mod, as the Reactor's Materializer Port offers the Reactor: an AE2 storage bus
or a pipe on any face sees everything storage holds and providers offer (a Reactor on the network, with what it
can make), takes from it, and puts things away into storage. Moves happen as part of the other side's transaction,
so one called off puts everything back and looking is free; each move costs the controller as a receptacle's does.
Click it to choose what it offers: stored and craftable items (the default), stored items, or craftable items.
A provider Receptacle on another network's Materializer brings that network's offers into its own, stored as
stored and craftable as craftable. A receptacle of the same network on its Materializer is ignored: it would read
the network into itself.

[[mechanic:laser.logistics.reactor]] A [[block:reactor_logistics_port]] on the Reactor's plinth rim, where a
controller's light ends, joins the Reactor to that network both ways, as the ME Superposition Port does for
an ME network. Open it to set it.

- To the network it's storage, at its priority. It offers what the Reactor holds, what its ME network holds
  (passed through), and what could be made from all of it, made when it's taken: a terminal shows those as
  craftable. Its filter says what it offers. Set to store, what the network puts in feeds the horizon.
- Set to share (the default), what the network holds is the Reactor's to make with, as an ME network's is,
  and shows on through an ME Superposition Port.

Light ending on it also powers the Reactor.

[[mechanic:work.queue]] **Big networks.** What's stored shows at once. What could be made from it, on a network
and in its Crafters, is worked out in the background, a little each tick within a time budget (config
`performance.workBudgetMicros`, 2 ms a tick by default), most urgent first. Work a player is waiting on (a
terminal or Crafter screen open, something just taken or put in) goes first and gets more of the tick
(`performance.urgentWorkBudgetMicros`, 4 ms), so it catches up within a few ticks. A crafting table on a
network with a lot in it can preview a thousand crafts; its list fills in over a few ticks, a millisecond's work at
a time, rather than stalling the server. Taking or crafting something never waits on it.

