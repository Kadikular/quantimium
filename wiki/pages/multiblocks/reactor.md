# Quantimium Reactor

*Everything goes in. Nothing is known. Anything you have the catalysts for comes out.*

A captive black hole. Everything you put in it is held in one horizon. Through the machines installed
around it, it can become anything those machines can make, and nothing is made until something takes
it out. Think of it as a [Quantum Crafter](../machines/quantum-crafter.md) that crafts recursively
through every catalyst at once, and always knows what it could give you.

It is a first version. Nothing it holds is ever at risk: a full horizon refuses more, and an
unpowered one just waits.

## The Singularity

[[mechanic:fold.singularity]] Put an **unbound** [[item:tesseract]] in the core of an **empty**
[Fold Chamber](fold-chamber.md) at least {{c:Folding.SINGULARITY_MIN_SIDE}} blocks every way and press
Fold. With nothing inside to hold the volume open it collapses into a [[item:singularity]], for
{{c:Folding.SINGULARITY_FE}} FE.

[[mechanic:reactor.singularity_holds]] The Singularity *is* the horizon: everything a Reactor holds is
held in it. Seat it in a [[block:horizon_core]] by using it on the core. Sneak-use the core with an
empty hand to take it out, or break the core and it drops; either way it carries everything with it,
and seated in another core it has it all back. Its tooltip says it's safe. Like a Sophon, a dropped
Singularity never despawns and doesn't burn, in fire or lava; only the void takes it. Carrying one that
holds something earns *Should you be holding that?*

## Building it

[[render:reactor_parts]]

[[structure:reactor]]

[[mechanic:reactor.structure]] Fixed in size:

- a disc of [[block:reactor_plinth]] 11 blocks across, its corners rounded off, with the
  [[block:horizon_core]] on its centre. The plinth's circuit traces run on from block to block
  wherever it's built, and light up when the Reactor forms;
- **Ring Emitters** on the plinth around the core, in facing pairs four blocks out: east and west,
  north and south, or diagonally three out each way. Each whole pair drives one ring, and each ring
  quadruples what the horizon holds: {{c:HorizonCoreBlockEntity.BASE_CAPACITY}} items with one ring,
  four million with two, sixteen million with three. Each emitter draws
  {{c:HorizonCoreBlockEntity.EMITTER_FE_PER_TICK}} FE/t;
- up to eight [[block:catalyst_bay]]s in place of plinth, anywhere but under the core;
- **ports** in place of plinth on the disc's rim, as many as you like: Input, Output, Energy and
  Materialiser, each marked on every face by a socket in its colour.

[[mechanic:reactor.bays]] A Catalyst Bay is a window in the floor onto a pocket of void, and it holds
up to {{c:CatalystBayBlockEntity.SLOTS}} catalysts, one in each quarter of the window: machines,
crafting tables, [[item:folded_tesseract]]s. Use the bay to open it: its catalysts go in and out of a
2x2 grid laid out as the window's quarters. Each catalyst also orbits the horizon as a moon, which
flares when a craft uses it. A bay left standing on the plinth, where they used to go, sinks into the
plinth block under it, keeping its catalyst.

[[mechanic:reactor.bays.filter]] Each bay has a filter, as the ME Superposition Crafter does: two lists
of {{c:CatalystBayBlockEntity.FILTER_SLOTS}} entries, each a whitelist or a blacklist. The output list
says which of its catalysts' recipes the Reactor may use; the input list, which items they may use up
(a blacklist of oak logs leaves birch logs to make planks from). An empty list filters nothing. Set an
entry by clicking it with an item or dragging one in from JEI; shift-click it to match one of the
item's tags instead.

[[mechanic:reactor.input]] Unpowered, the rings go down: the Reactor takes nothing in and makes
nothing, but keeps everything. The core stores {{c:HorizonCoreBlockEntity.ENERGY_CAPACITY}} FE,
taking up to {{c:HorizonCoreBlockEntity.MAX_RECEIVE}} FE/t through Energy ports.

## What it holds and what it can make

Items go in exactly as they are, data and all, through Input ports, the Materialiser Port, or both.
Nothing merges or breaks down by itself: a hopper stays a hopper, and iron blocks, ingots and nuggets
are three different things until a crafting table in a bay turns one into another.

[[mechanic:reactor.counts.reachable]] The core's screen shows one list, like an ME terminal: everything
it holds, and everything it could make from what it holds, with how many. Anything it can't make right
now isn't listed. Sort it by count or by name and search it; the tooltip splits what's held from what
could be made.

[[mechanic:reactor.counts.live]] It recounts whenever what it holds or its catalysts change, at most
once a second, off the server thread. The counts are estimates of how many you could have *if you took
only that*; two things made from the same iron can't both be had.

[[mechanic:reactor.counts.loops]] Matter is never counted back into a form it came from. If ingots
turn into dust and dust into ingots, 100 ingots and 25 dust count as 125 of each, never 150. A recipe
loop that would make more than it starts with is a duplication loop in the pack: it's never used, and
it's logged.

[[mechanic:reactor.counts.shared]] Things made from the same source share it. An anvil is three iron
blocks and four ingots, 31 iron: from 40 ingots and 5 blocks the list says 2, not 3.

## Taking things out

[[mechanic:reactor.planning]] Click an item in the core's screen for one, shift-click for a stack. It
comes from what's held if it's there; if not, the Reactor plans the whole tree through its catalysts,
uses held items and leftovers first, and makes the rest. A log and three raw iron become an iron
pickaxe through a crafting table and a furnace; sand becomes Quantum Attuned Glass through a furnace and
a folded Foundry. It runs to the end or not at all: if anything is missing it says what, and nothing is
used. It costs each recipe's energy at the Crafter's Singularity rate, and what it makes goes to the
**Output ports**; whatever they can't hold stays in the horizon.

[[mechanic:reactor.planning.choice]] With two recipes for the same thing it uses the one that takes the
fewest items per item made, energy only breaking ties: stone stairs come from a stonecutter, one stone
each, not six stone for four.

[[mechanic:reactor.planning.tools]] Some recipes need something that isn't used up, such as an
Inscriber's press. The press only has to be in the horizon, or be makeable, in which case it's made
once and kept. It never limits how many can be made.

[[mechanic:reactor.planning.wear]] A tool a recipe wears instead of using up, like AE2's cutting
knives, works the same way, one durability a craft: worn ones on hand go first, and a new one is made
only when they run out. Worn tools come back to the horizon as they are.

### Unrealised Matter

[[mechanic:reactor.matter]] [[item:unrealised_matter]] in the horizon is observed into whatever it
could be, with no catalyst: everything a [Materialiser](../blocks/materialiser.md) could make of it,
as its history would make it, from commons to rares, and never a very rare ore, whatever field the
Reactor stands in. One Matter makes what one makes at a Materialiser and costs
{{c:MaterialiserBlockEntity.FE_PER_MATTER}} FE, and no Trace or flux. So a horizon of Matter shows raw
iron, dusts and the rest on its list, and a hopper asks for iron the Matter becomes on the way.

## The Materialiser Port

[[mechanic:reactor.materialiser_port]] A pipe or a storage bus on a **Materialiser Port** sees
everything in the core's list, with its counted amount, as if it were all there. Taking something
makes it, exactly: the Reactor plans what was asked, gives as many as really can be made and paid for,
and uses nothing until the transfer goes through. Pipes and storage buses can put items in through it
too, unless a pack turns that off (`reactor.materialiserPortAcceptsItems`).

[[mechanic:reactor.materialiser_port.ae2]] On an [AE2](../concepts/partner-mods.md) network, one
storage bus on the port shows the whole list in the terminal as stock. Because the counts are
estimates that share their sources, asking for 64 of something can bring fewer, and other counts drop
as one is taken. A Materialiser Port only ever shows what the Reactor could make of what *it* holds,
never of what a network linked through an ME Superposition Port holds.

## The ME Superposition Port

With AE2 installed, the [[block:reactor_me_port]] joins the Reactor to an ME network both ways. It
goes on the rim like the other ports and takes a channel.

[[mechanic:reactor.me_port.storage]] What the Reactor holds is storage on the network: it shows in
the terminal and can be taken like anything in a drive. Nothing goes in this way; use Input ports, or
an export bus onto one.

[[mechanic:reactor.me_port.patterns]] What it can make is patterns on the network, so AE2 plans with
them from everything on the network, the Reactor's holdings included: it knows exactly how much can be
made and what's missing, and asking for one anvil too many says how much more iron that needs. Each run
is paid for from the core's power, at the Reactor's price. What it could make never shows as stock:
those counts share their sources, and AE2 would plan to use the same iron twice.

[[mechanic:reactor.me_port.settings]] Use the port to open its screen, where it's set up like a
storage bus:

- **Priority**, for its storage and its patterns, in steps of 1, 10 and 100.
- **Storing**: off by default. On, the network can store items in the Reactor as in a drive, at the
  port's priority, as far as there's room in the horizon.
- **Filter**, two lists of {{c:ReactorMePortBlockEntity.FILTER_SLOTS}} entries, each a whitelist or a
  blacklist: the *pattern* list says which things it offers patterns for; the *storage* list says which
  held items the network sees, takes and stores. An empty list filters nothing. Set an entry by clicking
  it with an item or dragging one in from JEI; shift-click it to match one of the item's tags instead.

[[mechanic:reactor.me_port.modes]] The screen's mode button chooses how it offers what it can make:

- **Both** (the default): whole trees for speed, and every recipe as a step for AE2 to fall back on.
- **Whole trees**: one pattern for each thing it can make, planned from the stock of the moment, with
  the raw things it uses up as inputs. An anvil of Matter is one pattern, 31 Matter in, and AE2 sends
  it once: fast, even on a crafting CPU without co-processors. A tree is planned as if one of every tool
  it needs were on hand, so it asks AE2 for a knife or a press, which AE2 makes once and reuses, rather
  than making one on every run. A tree whose inputs have gone from the network is planned again within
  a second, and every tree is looked over again now and then, so a better route turns up. A tree
  replaced while a crafting job is running stays offered until no CPU is busy, so the job still finds
  it. Each run is one of the thing, so what a run leaves over (spare planks, say) goes to the network
  rather than into the next run.
- **Steps**: one pattern for each recipe, as the [ME Superposition Crafter](../machines/me-superposition-crafter.md)
  offers. AE2 plans the tree itself and runs each step, using whatever intermediates are in storage. A
  crafting CPU sends one step every few ticks unless it has co-processors. AE2 chooses its own routes
  through every recipe the catalysts know, not the Reactor's least wasteful one, and when no route works
  it lists what one of them was missing, which can look unrelated to what you asked for.

[[mechanic:reactor.me_port.tools]] A tool a recipe keeps, such as an Inscriber's press, is an input
of its pattern that comes back after each run, so AE2 hands it over and uses it again: one press prints
any number of circuits. A tool that wears, such as a cutting knife, comes back worn, and a whole tree
takes any knife that fits, not the one it was planned with.

The Flux Meter or Jade on the port shows the mode and how many patterns it offers.

[[mechanic:reactor.me_port.inputs]] What the network holds, the Reactor counts and uses as its own
inputs on its own screen: logs in a drive count towards planks on the core's list, and a request
there takes them from the network as it needs them. Unrealised Matter in the network is observed just as Matter held is. The
network's own items aren't listed again on the core's screen.

[[mechanic:reactor.me_port.loops]] A storage bus on one of the Reactor's own Materialiser Ports, on
the same network, would show the network the Reactor twice, and let the Reactor count its own
holdings back in as network stock. So that port goes dark while the ME port is linked: it shows that
network nothing, its socket goes unlit, and the Flux Meter (or Jade) on it says so. The ME port shows everything the bus would have. And while the Reactor reads or takes
from the network, its own ports show nothing at all, so no route the Reactor can't see makes it count
or take anything twice either.

## Cost to the server

[[mechanic:reactor.counts.speed]] The heavy work, counting, runs on its own thread: a few milliseconds
for a typical base, under 40 for 50,000 recipes. On the server thread a Reactor costs well under a
microsecond a tick, idle or with items streaming in (see [tick timing](../reference/tick-timing.md)). A
storage bus reading a full port of several hundred items takes about a tenth of a millisecond; each
real extraction runs one plan, typically under a millisecond.
