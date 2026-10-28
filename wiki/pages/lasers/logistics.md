# Logistics

A logistics network moves items between inventories by laser. A Logistics Controller makes the network, Receptacles put inventories on it, and a Terminal shows what it holds.

## The Logistics Controller

[[mechanic:laser.logistics.network]] A [[block:logistics_controller]] is a relay with a back. A laser in by its back, where a focus is set, goes on out of its outputs (set as an emitter's are, never the back), with a laser of its own. A laser on any other side only charges it.

- Everything its laser reaches, through mirrors, splitters and relays, joined with other lasers or not, is one **logistics network**, as any laser is a laser network.
- An Isolator lets power through but not the network.
- One controller to a network: where lasers from two meet, both stop, go dark and say why.

[[structure:logistics]]

*A controller's laser through storage, a provider and a requester, each on a chest, to a Logistics Lectern.*

[[mechanic:laser.logistics.controller]] The controller has its own store, up to {{c:LaserTuning.LOGISTICS_CAPACITY}} FE. It fills it from a laser ending on it, as any block that takes power does, or from cables on any face.

- It pays {{c:LaserTuning.LOGISTICS_FE_PER_MOVE}} FE for each move its receptacles make (up to a stack), and for nothing else.
- It gives no power, so it charges no machines. Lens work past it is paid for by the other sources whose lasers reach the lens, as ever.
- Out of power, its receptacles wait.

[[mechanic:laser.logistics.lenses]] **Lenses past the controller use the network.** Up the laser from a lens, the controller stands for the whole network, after any Laser Port nearer the lens.

- What a Pickup or Extract Lens brings back goes into the network (to requesters listing it, then storage), so they work as active providers.
- What an Insert, Placement or Interact Lens uses comes from the network (providers, then storage), but only through an Filter Lens, so they work as active requesters. Unfiltered, they take nothing from the network (a Laser Port up the laser still feeds them).
- A stream from one side of a join reaches back up its own side. Put the controller before the lenses, or on their side of the join.

## Logistics Receptacle

A [[item:logistics_receptacle]] is an attachment for a [[item:laser_focus]]. It goes on the face against the inventory it serves (a chest, a machine, a drawer), and works while a laser reaches the focus.

- One focus can hold several, each serving its own inventory with its own settings.
- On a [[item:clear_laser_focus]] the laser passes straight through, so one laser along a row of machines joins them all. On a Laser Focus the laser stops there.
- Logistics Receptacle blocks from before become a Clear Laser Focus with a Receptacle on the same face.
- Its plate lights in its role's color while a controller's laser reaches it.
- Open it to set what the inventory is to the network, its priority, and a filter of nine entries, as a whitelist or a blacklist, with tags. For storage and providers, an empty filter takes or gives everything. A requester asks for nothing as a whitelist, and for everything as a blacklist.
- Each active receptacle moves as an Insert or Extract Lens does: up to 16 items a second, four times as many for each Speed Lens the laser met on its way to it (up to three). Idle, it looks less often, waiting twice as long each time up to 4 seconds (a config option), and half as long again after each move.

### Roles

- **Storage**: where the network keeps things. [[mechanic:laser.logistics.priority]] What comes in fills the highest priority first, as far as its filter lets in. What is taken goes from the lowest first.
- **Provider**: [[mechanic:laser.logistics.provider]] passive, the network may take what its filter lets go (a furnace's output, say). Active, it pushes it out itself, to requesters listing it first, then into storage.
  - On a machine that makes things to order (a Quantum Crafter, or a [[block:materialiser]] with no output chosen), what it would make is offered as craftable. It makes them from what is really stored, one step at a time.
  - A tab on its screen turns that off, offering only what is really in the machine's outputs.
- **Requester**: [[mechanic:laser.logistics.requester]] passive, it takes what active providers push, as its filter lets in. Active, it pulls what its filter lists from providers first, then storage: what is stored before anything is made.
  - A tab on an active requester says whether machines may make what isn't stored for it, or it takes only what is stored.
  - [[mechanic:laser.logistics.filter]] Only what its filter lists comes, as far as the inventory takes it. With **Keep stocked** on, each entry's count (the stack you set it with; scroll over it to change, shift for 16) is how many it keeps there: it takes only what it is short of.

[[mechanic:laser.logistics.receptacle]] A receptacle is lit while it is joined to a controller with the power to move things. On a machine that takes power, a tab on its screen sets it to power that machine too: the laser reaching the receptacle (an emitter's, through the controller) charges what it is on, as a laser ending on it would.

[[mechanic:laser.logistics.port]] A collecting [[block:laser_port]] on the network is a passive provider of what it has caught. A receptacle on a Laser Port works with its nine slots whatever its faces are set to, in any role.

A laser from a controller is drawn a deeper blue, and always as if at work.

### Fluids

[[mechanic:laser.logistics.fluids]] A network moves fluids as it moves items. A receptacle on a tank (a [[block:laser_tank]], or any mod's) puts the tank on the network in its role.

- A receptacle's filter lists fluids too: right-click an entry with a bucket, or drag a fluid in from JEI. Fluid entries only match fluids. With no fluid listed, storage and providers take or give any fluid.
- An active receptacle moves up to 2,000 mB a second, four times as much for each Speed Lens, at the same cost per move as items.
- With **Keep stocked** on, a fluid entry's count is in buckets.
- A collecting Laser Tank on the network is a passive provider, as a Laser Port is.
- An Extract Lens past the controller pours into the network. Insert and Interact Lenses take fluid from it only through a Filter Lens that lists a fluid.

## Logistics Terminal

[[mechanic:laser.logistics.terminal]] A [[block:logistics_terminal]] where a controller's laser ends shows everything the network's storage holds and its providers offer, searchable and sorted by count or name, like an ME terminal. Sort and view are tabs on its left.

- Click an item for a stack on the cursor, right-click for half, shift-right-click for one, shift-click for a stack into your inventory.
- While shift is held the grid holds still. Shift with the wheel takes one more onto the cursor (down) or puts one back (up).
- Click the grid holding something to put it away into storage (right-click: one), or shift-click it in your inventory.
- What is stored comes out first. If any is, that is all you get, and only when none is left does a machine make more.
- Each take or put costs the controller as a move does.
- Fluids show with their amounts. Click one holding a bucket (or any fluid container) to fill it. Right-click the grid with a filled one to pour it into storage.

A [[block:logistics_lectern]] is the same terminal on a stand, its screen tilted over the focus the laser comes in to. Craft one from the other.

### Search

The search reads as JEI's does: words must all match, in any order; `a|b` matches either; `-word` leaves out what matches. `@mod` matches a mod's id or name, `#text` its tooltip, `$tag` its tags: `@mekanism ingot`, `-@minecraft`, `$c:ingots|$c:gems`.

### Crafting grid

The crafting table tab shows a 3×3 grid under a shorter item grid, as an ME crafting terminal has. What is in the grid stays in the terminal, for anyone who opens it next.

- Craft in it as at a crafting table. After each craft, what it used is topped up from what the network stores, so shift-clicking the result crafts again and again into your inventory until something runs out.
- With JEI, the **+** on a crafting recipe fills the grid from what is stored, then your inventory. What neither has but a machine on the network can make (one step) goes in as **Craft**: nothing is made until you take the result, and then just what that craft needs.
- One craft's worth goes in (shift-+ too), the grid being refilled after each. A slot whose item runs out becomes a Craft, if the network can make it.
- In JEI, what will be made shows blue. Items that are nowhere show red, and the rest still go in.
- Drag something the network can only make from the item grid onto the crafting grid to put it in as Craft too. Click a Craft with nothing in hand to clear it.
- A Craft the network can no longer make or supply goes, and a craft that can't make its Crafts isn't given.
- The × beside the grid puts it all back into the network.

## Logistics Materializer

[[mechanic:laser.logistics.materialiser]] A [[block:logistics_materialiser]] where a controller's laser ends offers the whole network to any mod, as the Reactor's Materializer Port offers the Reactor. An AE2 storage bus or a pipe on any face sees everything storage holds and providers offer (a Reactor on the network, with what it can make), takes from it, and puts things away into storage.

- Moves happen as part of the other side's transaction, so one called off puts everything back and looking is free. Each move costs the controller as a receptacle's does.
- Click it to choose what it offers: stored and craftable items (the default), stored items, or craftable items.
- A provider Receptacle on another network's Materializer brings that network's offers into its own, stored as stored and craftable as craftable.
- A receptacle of the same network on its Materializer is ignored: it would read the network into itself.

## Reactor Logistics Port

[[mechanic:laser.logistics.reactor]] A [[block:reactor_logistics_port]] on the Reactor's plinth rim, where a controller's laser ends, joins the Reactor to that network both ways, as the ME Superposition Port does for an ME network. Open it to set it. A laser ending on it also powers the Reactor.

- To the network it is storage, at its priority. It offers what the Reactor holds, what its ME network holds (passed through), and what could be made from all of it, made when it is taken. A terminal shows those as craftable. Its filter says what it offers. Set to store, what the network puts in feeds the horizon.
- Set to share (the default), what the network holds is the Reactor's to make with, as an ME network's is, and shows on through an ME Superposition Port.

## Big networks

[[mechanic:work.queue]] What is stored shows at once. What could be made from it, on a network and in its Crafters, is worked out in the background, a little each tick within a time budget (config `performance.workBudgetMicros`, 2 ms a tick by default), most urgent first.

- Work a player is waiting on (a terminal or Crafter screen open, something just taken or put in) goes first and gets more of the tick (`performance.urgentWorkBudgetMicros`, 4 ms), so it catches up within a few ticks.
- A crafting table on a network with a lot in it can preview a thousand crafts. Its list fills in over a few ticks, a millisecond's work at a time, rather than stalling the server.
- Taking or crafting something never waits on it.
