---
navigation:
  title: Quantimium Reactor
  parent: multiblocks/index.md
  position: 21
item_ids:
- quantimium:horizon_core
- quantimium:reactor_plinth
- quantimium:ring_emitter
- quantimium:catalyst_bay
- quantimium:reactor_input_port
- quantimium:reactor_output_port
- quantimium:reactor_energy_port
- quantimium:reactor_materialiser_port
---

# Quantimium Reactor

*Everything goes in. Nothing is known. Anything you have the catalysts for comes out.*

A captive black hole. Everything you put in it is held in one horizon. Through the machines installed
around it, it can become anything those machines can make, and nothing is made until something takes
it out. Think of it as a [Quantum Crafter](../machines/quantum-crafter.md) that crafts recursively
through every catalyst at once, and always knows what it could give you.

It is a first version. Nothing it holds is ever at risk: a full horizon refuses more, and an
unpowered one just waits.

## The Singularity

Put an **unbound** <ItemLink id="quantimium:tesseract" /> in the core of an **empty**
[Fold Chamber](fold-chamber.md) at least 9 blocks every way and press
Fold. With nothing inside to hold the volume open it collapses into a <ItemLink id="quantimium:singularity" />, for
1,000,000 FE.

The Singularity *is* the horizon: everything a Reactor holds is
held in it. Seat it in a <ItemLink id="quantimium:horizon_core" /> by using it on the core. Sneak-use the core with an
empty hand to take it out, or break the core and it drops; either way it carries everything with it,
and seated in another core it has it all back. Its tooltip says it's safe. Carrying one that holds
something earns *Should you be holding that?*

## Building it

<GameScene zoom="2.0" interactive={true}>
  <ImportStructure src="../assets/structures/reactor.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

Fixed in size:

- a disc of <ItemLink id="quantimium:reactor_plinth" /> 11 blocks across, its corners rounded off, with the
  <ItemLink id="quantimium:horizon_core" /> on its centre. The plinth's circuit traces run on from block to block
  wherever it's built, and light up when the Reactor forms;
- **Ring Emitters** on the plinth around the core, in facing pairs four blocks out: east and west,
  north and south, or diagonally three out each way. Each whole pair drives one ring, and each ring
  quadruples what the horizon holds: 1,000,000 items with one ring,
  four million with two, sixteen million with three. Each emitter draws
  1,000 FE/t;
- up to eight <ItemLink id="quantimium:catalyst_bay" />s in place of plinth, anywhere inside the rim but under the core;
- **ports** in place of plinth on the disc's rim, as many as you like: Input, Output, Energy and
  Materialiser, each marked on every face by a socket in its colour.

A Catalyst Bay is a window in the floor onto a pocket of void, and it holds
up to 4 catalysts, one in each quarter of the window: machines,
crafting tables, <ItemLink id="quantimium:folded_tesseract" />s. Use one on a quarter to install it there (or in the next
free quarter); use an empty hand on a quarter to take it back. Each catalyst also orbits the horizon as
a moon, which flares when a craft uses it. A bay left standing on the plinth, where they used to go, sinks
into the plinth block under it, keeping its catalyst.

Unpowered, the rings go down: the Reactor takes nothing in and makes
nothing, but keeps everything. The core stores 20,000,000 FE,
taking up to 500,000 FE/t through Energy ports.

## What it holds and what it can make

Items go in exactly as they are, data and all, through Input ports, the Materialiser Port, or both.
Nothing merges or breaks down by itself: a hopper stays a hopper, and iron blocks, ingots and nuggets
are three different things until a crafting table in a bay turns one into another.

The core's screen shows one list, like an ME terminal: everything
it holds, and everything it could make from what it holds, with how many. Anything it can't make right
now isn't listed. Sort it by count or by name and search it; the tooltip splits what's held from what
could be made.

It recounts whenever what it holds or its catalysts change, at most
once a second, off the server thread. The counts are estimates of how many you could have *if you took
only that*; two things made from the same iron can't both be had.

Matter is never counted back into a form it came from. If ingots
turn into dust and dust into ingots, 100 ingots and 25 dust count as 125 of each, never 150. A recipe
loop that would make more than it starts with is a duplication loop in the pack: it's never used, and
it's logged.

Things made from the same source share it. An anvil is three iron
blocks and four ingots, 31 iron: from 40 ingots and 5 blocks the list says 2, not 3.

## Taking things out

Click an item in the core's screen for one, shift-click for a stack. It
comes from what's held if it's there; if not, the Reactor plans the whole tree through its catalysts,
uses held items and leftovers first, and makes the rest. A log and three raw iron become an iron
pickaxe through a crafting table and a furnace; sand becomes Quantum Attuned Glass through a furnace and
a folded Foundry. It runs to the end or not at all: if anything is missing it says what, and nothing is
used. It costs each recipe's energy at the Crafter's Singularity rate, and what it makes goes to the
**Output ports**; whatever they can't hold stays in the horizon.

With two recipes for the same thing it uses the one that takes the
fewest items per item made, energy only breaking ties: stone stairs come from a stonecutter, one stone
each, not six stone for four.

Some recipes need something that isn't used up, such as an
Inscriber's press. The press only has to be in the horizon, or be makeable, in which case it's made
once and kept. It never limits how many can be made.

A tool a recipe wears instead of using up, like AE2's cutting
knives, works the same way, one durability a craft: worn ones on hand go first, and a new one is made
only when they run out. Worn tools come back to the horizon as they are.

### Unrealised Matter

<ItemLink id="quantimium:unrealised_matter" /> in the horizon is observed into whatever it
could be, with no catalyst: everything a [Materialiser](../blocks/materialiser.md) could make of it,
as its history would make it, from commons to rares, and never a very rare ore, whatever field the
Reactor stands in. One Matter makes what one makes at a Materialiser and costs
4,000 FE, and no Trace or flux. So a horizon of Matter shows raw
iron, dusts and the rest on its list, and a hopper asks for iron the Matter becomes on the way.

## The Materialiser Port

A pipe or a storage bus on a **Materialiser Port** sees
everything in the core's list, with its counted amount, as if it were all there. Taking something
makes it, exactly: the Reactor plans what was asked, gives as many as really can be made and paid for,
and uses nothing until the transfer goes through. Pipes and storage buses can put items in through it
too, unless a pack turns that off (`reactor.materialiserPortAcceptsItems`).

On an [AE2](../concepts/partner-mods.md) network, one
storage bus on the port shows the whole list in the terminal as stock. Because the counts are
estimates that share their sources, asking for 64 of something can bring fewer, and other counts drop
as one is taken.

## Cost to the server

The heavy work, counting, runs on its own thread: a few milliseconds
for a typical base, under 40 for 50,000 recipes. On the server thread a Reactor costs well under a
microsecond a tick, idle or with items streaming in (see tick timing). A
storage bus reading a full port of several hundred items takes about a tenth of a millisecond; each
real extraction runs one plan, typically under a millisecond.
