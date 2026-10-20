---
navigation:
  title: Lasers
  parent: machines/index.md
  position: 16
---

# Lasers

*Light kept in step carries power across a room.*

Laser emitters send power as light. A beam crosses open air, glass and anything you can walk
through (grass, torches, flowers), and charges the first block it meets that takes power, from any
mod. Mirrors and relays turn the light around corners, and prisms split and join it at any angle; lenses give it
something to do, and a logistics network moves items by it.

<GameScene zoom="2.0" interactive={true}>
  <ImportStructure src="../assets/structures/laser_network.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

*An Emitter II's light, split by a Laser Relay, powering a Quantum Crafter and a Quantum Simulator.*

## Emitters

An emitter takes power on any face and sends it out as light from its
outputs. Placed, it faces you, and its light comes out towards you.

It sends only what the blocks its beams reach ask for, up to its rate: point
one at a full battery, or at nothing, and it keeps its power, but for a small cost of being ready.

| Emitter | Rate | One run reaches | Holds | Ready cost |
| --- | --- | --- | --- | --- |
| <ItemLink id="quantimium:flash_emitter" /> | 200 FE/t | 8 blocks | 20,000 FE | none |
| <ItemLink id="quantimium:laser_emitter_1" /> | 2,000 FE/t | 16 blocks | 200,000 FE | 2 FE/t |
| <ItemLink id="quantimium:laser_emitter_2" /> | 32,000 FE/t | 32 blocks | 3,200,000 FE | 16 FE/t |
| <ItemLink id="quantimium:laser_emitter_3" /> | 512,000 FE/t | 64 blocks | 51,200,000 FE | 128 FE/t |

The **Flash Emitter** can also burn furnace fuel: each tick of burn time
makes 100 FE, so a coal gives 160,000. It's power for a base that
has no generator yet. It only starts a new fuel once it has room for what that makes.

A <ItemLink id="quantimium:solar_emitter" /> gathers sunlight through its glass into a small focus
and sends it out of a simple emitter on one side as light: 20 FE/t in full sun,
less as the sky dims (it reads the sky as a daylight detector does: dawn, dusk and rain give less), none
at night or under a roof. It holds nothing. Light that reaches it from any laser (another Solar Emitter,
a Laser Emitter, by mirrors or not) joins its own and leaves by its emitter, but no more than
160 FE/t leaves it in all, what it makes included: so a row of eight in full
sun is as much as one row gives. Its light reaches 8 blocks a run. Placed, it
shines towards you; sneak-click a side (by hand or with a wrench) to send its light there, but never up,
through its glass.

Sneak-click a face with an empty hand, or use any wrench on it, to add an
output there, or take one away. One emitter can shine from all six faces; it always keeps at least one.

Open an emitter to see what it's sending and to set how it listens to
redstone: not at all, on only with a signal, or off with a signal.

## Mirrors and relays

A <ItemLink id="quantimium:laser_mirror" /> is a silver plate on a steel stand that turns light
through a right angle, both ways. It stands on the block it was placed against, for good. Its plate faces
half way between two sides at right angles, neither the one it stands on: click one side (by hand or with
a wrench) and sneak-click another. Light in by either side leaves by the other; light from anywhere else
hits the back of the plate and stops. Sneak-click the side it already takes light from and it's **flat**, facing that side: light goes straight back the way it came, and facing a
Cavity Mirror across a gap it's the far end of the cavity; click another side and it turns light from there back out where it faced. It never sends light straight on. A redstone
signal turns it off.

A <ItemLink id="quantimium:laser_splitter" /> is a Laser Mirror with a half-silvered plate, set
the same way (never flat): click its **input side**, sneak-click its **output (turn) side**, at right
angles, as you sneak-click a Solar Emitter's output; sneak-clicking its input side swaps the two. Its plate has a light-blue mark in the middle, and a
notch on its rim by the output side; the Flux Meter or Jade names both sides.
Light in by its input side splits: half goes straight on, half turns out of its turn side. Light from
behind its turn side passes straight through the plate and out of the turn side too; with both coming
in, they leave by the turn side only, joined: a combiner. Light from any other side stops on it, and so
does light into a side facing what it stands on. It closes no cavity.

A <ItemLink id="quantimium:laser_relay" /> is a focus on a post, on a base on the block it was
placed against, with an emitter on any of its other sides. Light reaching its focus, from any side but
its base, leaves by each emitter, shared by what each beam's blocks ask for: a prism's job for the six
straight directions, with no linking. Placed, its one emitter faces you. Sneak-click (by hand or with a
wrench) a side of its focus to put an emitter there, or an emitter to take it away. Click an emitter to
open it: it holds one lens, which only the light leaving by that emitter goes through (a Harvest lens on
one emitter cuts only there), and its tip takes the lens's colour. Taking an emitter away drops its lens.

Both refocus the light to its source's range: a run from a mirror or relay reaches as far as a run from
the emitter that sent it, so a Flash Emitter's light goes 8 blocks a run however many it passes. Each
one a beam passes takes 1% of what passes, and that becomes flux where it
stands.

Each of a relay's emitters listens to the redstone reaching the block its
own way, set in its screen: it ignores it, lets light out only with a signal, only without one, or in
**pulse** mode, where each signal it gets lets light out until one action down its beam is done (a block
cut, a hit, an item carried) or two seconds pass. One signal can open one emitter and close another. A
pulse emitter before a Harvest lens breaks one block per pulse. An emitter switched off sends nothing; with
all of them off, the relay passes no light at all, and what's beyond gets only the light that reaches it
some other way. An emitter switched off by redstone gives out none.

An <ItemLink id="quantimium:isolator" /> passes light on as a relay does, but lets only power
through: light leaving it carries nothing it did (actions, Pickup, UV), and nothing comes back up through
it, so Pickup's items stop short of it. It's a plain block: sneak-click or wrench a face to add an output
there or take one away (one always stays), and use an empty hand to step through how it listens to
redstone.

An <ItemLink id="quantimium:isolator_lens" /> does the same in a Lens Mount or a Relay emitter, and can be set to let more through: switch on
a logistics network, actions or modifiers to let them pass, and list lenses to let through only those, or
to stop just those (an Isolator Lens passing actions but stopping Harvest). Power and the light's reach
always pass. Unset, it passes power only, as the Isolator does; an Isolator alone in a crafting grid
makes one. Use it in the air to set it.

## Prisms

A <ItemLink id="quantimium:laser_prism" /> sends the light that reaches it to each block
it's linked to, along a straight line at any angle, sharing it by what each asks for.

Light can reach it from any side, from any number of beams, so a prism
also joins several emitters into one network: a block it's linked to can take more than any one of
them could send. It refocuses and taxes light as a relay does.

Link it with the <ItemLink id="quantimium:laser_linker" />: use the Linker on the prism to
pick it, then on each block its light should reach, once to link it and again to unlink it. A prism
links up to 8 blocks, within 24 blocks. Sneak-use
the Linker in the air to let the prism go, and sneak-click the prism with an empty hand to let go of
all its links. A link to a block that's been removed, even one light passes through such as grass, is let go of by itself.
Use an empty hand on a prism to make it keep those links instead: it goes on aiming at the
spot, so a Harvest lens's light breaks whatever comes back there.

Holding the Linker, or a wrench, shows every beam faintly, even ones carrying nothing, so you can see
where light would go.

## Entangled mirrors

An <ItemLink id="quantimium:entangled_mirror" /> sends on the light of another laser block, any
distance away, through anything, in the same dimension. It's made in pairs in the
<ItemLink id="quantimium:quantum_foundry_controller" />, from a <ItemLink id="quantimium:tesseract" /> and two Laser Mirrors. Its muzzle faces you as you
place it.

- Bind a Tesseract to the laser block the light should come from, a <ItemLink id="quantimium:laser_relay" /> say (sneak and use it on
  that block), then use it on the Entangled Mirror. It floats in the mirror's window.
- Light reaching that block reaches the mirror too, at once, and leaves by its muzzle, refocused, as from any
  mirror; the block still sends its own light on as well. The jump takes 5% of what crosses (a few relays' worth),
  as flux. While light leaves it, its housing glows and the Tesseract turns faster.
- Several mirrors can draw on one block; their light shares what reaches it, as two outputs of a relay would.
- No other light gets into it: it only gives out what it draws.
- Both blocks must be loaded for light to cross; the Tesseract stays bound while one isn't, and light crosses again
  when it's back. A Tesseract bound to a block in another dimension sends nothing.
- Sneak with an empty hand to take the Tesseract out. A redstone signal switches it off.
- A Relay with all its emitters switched off still passes its light to the mirrors drawing on it; a Laser Mirror
  switched off by redstone passes nothing.

## Optical cavities

A <ItemLink id="quantimium:cavity_mirror" /> facing a flat <ItemLink id="quantimium:laser_mirror" />, a <ItemLink id="quantimium:laser_relay" />, or another Cavity
Mirror, across a clear gap of up to 16 blocks holds light between them,
as a real laser's cavity does: a battery for a laser network. Only one end needs to be a Cavity Mirror.
Each Cavity Mirror holds 10,000,000 FE for each block of gap, and takes or
gives up to 512,000 FE/t; a Laser Mirror or Relay at the other end holds nothing.

A Cavity Mirror looks like a <ItemLink id="quantimium:laser_relay" /> with a flat Splitter mirror for its front: it stands on the
block it was placed against, as a Relay does, its mirror faces its partner, and an emitter stands on each face
light leaves by. No light comes in by its base. With nothing facing it, it's just a relay:
light that reaches it leaves by its mirror, towards where its partner would stand. Once paired, what it
holds leaves by its outputs instead.

- Light that reaches a cavity mirror from outside charges it, but only with what the network's
  emitters have left over once its blocks are served. One cavity never fills another.
- Light leaves by its **outputs**, set as a Relay's are (its back, to begin with), for the blocks that ask
  for it. A network serves its blocks from its emitters first, then from its cavities. Sneak-click a face
  with an empty hand to add an output there, or take it away; never on the mirror or the base. With no outputs it only
  stores. Use a wrench on a face to turn the mirror there.
- With two Cavity Mirrors, the light bounces between them, so what either holds evens out between
  them: both glow, and both can give it out.
- Light reaching the Laser Relay at the far end is passed into the cavity, so that end can feed it.
- A cavity can leak: a pack can set it to lose a share of what it holds
  each tick, up to a cap, as flux where it stands. Out of the box it doesn't leak. Break a charged
  mirror, or block its gap, and the light it held all goes into the field at once.

The held light glows in one beam across the gap, brighter the more there is.

**A cavity keeps what its light does.** Fill it with Harvest light and
the light it gives out harvests too; it reaches as far as the light that filled it. The cavity pays for
what its light does, from what it holds. Light from different sides mixes in it: it does all of it,
until it's empty and the next light to fill it decides.

## Photodetectors

A <ItemLink id="quantimium:photodetector" /> sits in a beam: light passes through it and
out of its face, and it takes nothing for it. It doesn't refocus the light, so the light goes on only as
far as it had left. It lights up while light passes, and gives a redstone
signal, and a comparator reading, for how much: 1 for a trickle, two more for each tenfold, up to 15.

## Lenses

A <ItemLink id="quantimium:lens_mount" /> holds four lenses in a row, and light goes straight
through it: in by its back, where half a focus is set in a recess, and out of its front, where half an
emitter is, refocused, through its lenses in order (its four slots, left to right). Light from any other
side stops on it. Down each side, four slot panels show what's fitted: dark when empty, the lens's colour
when there's one. The beam is still a beam: it carries power to whatever it ends on, as before, and does what its
lenses say along the way. Open the mount with an empty hand; sneak-click or wrench a side to point it
there. Under each lens is a button for that lens's own settings (a filter lens's, say), lit when it has
any. Each lensed beam is tinted by what it does.

**Actions** give light something to do, and light can carry several: Harvest and Damage on one beam
both work. An action stays on the light until the beam ends. **Modifiers**
change the actions the light already carries when it meets them: those in slots to their left, and those
from mounts before. Never one that comes after: Harvest then Filter cuts only what the filter lets, Filter
then Harvest cuts anything. Pickup is the exception: it stays on the light, and actions added after it get
it too, so Pickup then Harvest brings back what it cuts as Harvest then Pickup does (and waits when there's
no room).

| Lens | Kind | What it does | Costs |
| --- | --- | --- | --- |
| <ItemLink id="quantimium:harvest_lens" /> | Action | Cuts the block the beam ends on, or one light passes through just before it (a crop on farmland, grass), as an iron pickaxe would (a diamond one with an Amplify lens), and breaks it when the cut's through; the cracks show how far it's got. A block that goes before then takes the cut with it. One block every 0.5 s at most. It never breaks a block with a block entity (a machine, a chest), Anomalite, or what a wither can't break. With the block gone, the beam reaches on to the next. | 400 FE for each point of hardness, as it cuts |
| <ItemLink id="quantimium:damage_lens" /> | Action | Hurts anything living that stands in the beam, players included, every 0.5 s. It charges up for its hits while there's something to hit, over as many ticks as it takes, and keeps what it's charged. With Pickup, what it kills drops straight back up the beam. | 1,000 FE a hit, charged as it goes |
| <ItemLink id="quantimium:pickup_lens" /> | Modifier, and an action of its own | What the light's actions free goes back up it; alone, so do items lying within about a block of the beam. It all goes to the nearest collecting Laser Port; what that one can't take goes to the next. Back past a cavity, it reaches the ports up the light that filled it. With none, drops fall where they are. A Harvest beam with Pickup only cuts a block whose drops the ports up the beam would take, all of them: room, and filters that let them in. Otherwise it waits, and leaves nothing lying. | 200 FE a stack, when it's carried |
| <ItemLink id="quantimium:range_lens" /> | Modifier | Each run of the light reaches twice as far, up to 64 blocks (a pack can change that, `maxRange` in the `lasers` config): light with an action goes 8, 16, 32, 64. | none |
| <ItemLink id="quantimium:interact_lens" /> | Action | Uses one thing a Laser Port up the beam sends, or an empty hand with none, as a player would, each 0.5 s. Set to **use**: with something in hand, a right click on the first thing living in the beam that takes it (feeding, shearing, milking; a Filter after it says which: only grown ones, say; never a villager); else a right click on the block the beam ends on, or on something light passes just before it (a lever, a button), so it opens doors, flips levers, works items on blocks; an empty bucket fills from the fluid the beam ends on. Set to **hit**: a swing at the first thing living in the beam, as hard as what it holds. It works with what the port holds where it lies, trying each kind of thing there in turn (carrots, then wheat, then shears) till one is used: nothing moves unless using it changes it. Then what it became goes back where it belongs: still the same thing (a worn sword) to the sending port, anything new (a filled bucket) up the beam to a collecting one. Through an Area Prism it works on the area: with something in hand, on what's living there first; else on its blocks in turn, each from the side facing the prism (bone meal over a field, say). It does it over and over: behind a pulse mirror, once a signal. Use it in the air, or its button in a mount, to switch. | 200 FE a use |
| <ItemLink id="quantimium:insert_lens" /> | Action | Puts what Laser Ports up the beam send into the inventory the beam ends on (it ramps up and down: each move halves its wait, from 4 s down to its own pace; each look that finds nothing doubles it, back up to 2 s), up to 8 items each 0.5 s (each Speed lens doubles that, to 64, and halves the wait); with an Area Prism, each inventory in its area in turn. A Filter lens says what. | 200 FE a stack |
| <ItemLink id="quantimium:extract_lens" /> | Action | Takes from the inventory the beam ends on, up to 8 items each 0.5 s (each Speed lens doubles that, to 64, and halves the wait), as much as the Laser Ports up the beam have room for, back up to them; with an Area Prism, from each inventory in its area in turn. A Filter lens says what. | 200 FE a stack |
| <ItemLink id="quantimium:precision_lens" /> | Modifier | Harvest cuts with Silk Touch: blocks come away whole. | none |
| <ItemLink id="quantimium:harmonic_lens" /> | Modifier | Harvest cuts with Fortune, and Damage hits with Looting, a level for each, up to 3. | none |
| <ItemLink id="quantimium:unrealising_lens" /> | Modifier | Ore a Harvest beam cuts (anything in `#c:ores`) comes away as <ItemLink id="quantimium:unrealised_matter" />, one to a block, with no history yet. A pack can turn it off in the config, where Matter would duplicate ores. | none |
| <ItemLink id="quantimium:smelting_lens" /> | Modifier | Smelts, as a furnace would, what the actions before it free or move: what a Harvest beam cuts and what Damage kills (picked up or left lying), what Pickup gathers, what Insert puts in and Extract takes out. Sand becomes glass, ore ingots, a pig's drops cooked. Unrealised Matter gets smelting in its history, as a Quantum Crafter with a furnace gives it. What a furnace can't smelt stays as it is, for nothing. With no other action on the light it's an action of its own: it smelts what lies in the beam where it lies, else the block the beam ends on, every 0.5 s. A block it smelts into takes its place (sand becomes glass, and the light goes on through to the next); anything else drops there. | 25 FE for each item it smelts |
| <ItemLink id="quantimium:crushing_lens" /> | Modifier | Crushes what the actions before it free or move, everywhere Smelting smelts: stone to cobblestone, cobblestone and deepslate to gravel, gravel and sandstone to sand, and anything else as the pack's crushers would, their sure outputs only (raw ore to dust, say). Which crushers, and in what order, is `crushingRecipeTypes` in the `lasers` config: Energized Power's pulverizer and crusher, Ender IO's SAG Mill, Mekanism, Create, Thermal and others. Each Crushing or Smelting lens is a step, in the lenses' order (up to 8): Crushing then Smelting makes raw ore dust, then ingots; Crushing, Crushing, Smelting makes cobblestone gravel, then sand, then glass. A step that changes nothing is free, and so is a run of steps that ends as it began (glass crushed to sand and smelted again). Where nothing says which came first (a crushing beam and a smelting beam meeting at a mirror) it crushes first. Where they change no action before them (alone, or before one: Crushing, Smelting, Harvest), they act on the world themselves, as one, in their order, and a Harvest after them waits till they've done: sandstone becomes glass, then it's cut. Unrealised Matter gets the first of those crushers that gives it something new in its history. Alone it crushes what lies in the beam, else the block the beam ends on, in place: stone becomes cobblestone, then gravel, then sand. With Phantom, it goes through what it can't change to the first block on its way it can (Smelting too). | 25 FE for each item it crushes |
| <ItemLink id="quantimium:phantom_lens" /> | Modifier | Light passes through blocks: only laser blocks, and blocks it charges, stop it. A Harvest beam with it cuts the first block on its way it's allowed to (with a Block Filter Lens set to ores, just the ore in the ground; with Pickup, it passes over what no Laser Port up the beam would take); an Area Prism with it reaches what it couldn't see. It stays on the light. | each tick it's lit, a configurable FE (20 by default, `phantomFePerBlock`) for each block it passes through; for an Area Prism, for each block between it and what it works on; and the actions it carries cost twice as much |
| <ItemLink id="quantimium:speed_lens" /> | Modifier | Its actions work twice as fast: a Harvest beam waits half as long between blocks, Placement too, and an Area Prism looks through its area twice as fast. Three count, for eight times. | none |
| <ItemLink id="quantimium:filter_lens" /> | Modifier | The Item Filter Lens: the actions it changes carry, place, put in or take out only the items it lets through. Nine entries, allow or deny, with tags (shift-click an entry). Insert, Extract, Pickup and Placement read it. Use it in the air to set it. | none |
| <ItemLink id="quantimium:block_filter_lens" /> | Modifier | The Block Filter Lens: a Harvest beam cuts only the blocks it lets through, by the block or anything it drops, so "wheat" lets only ripe wheat be cut. Nine entries, allow or deny, and **ripe only**: only crops (and anything else that grows) that are fully grown, never what doesn't grow, so never the farmland. | none |
| <ItemLink id="quantimium:entity_filter_lens" /> | Modifier | The Entity Filter Lens: a Damage or Interact beam hits only the living things it lets through. Nine entries take spawn eggs, allow or deny, for kinds of mob; light up hostile, animals or players (none lit, any), choose to **spare the young**, and a **spare** count (shift for tens): it hits only those beyond that many, so a pen keeps its breeding pair. | none |
| <ItemLink id="quantimium:amplify_lens" /> | Modifier | Harvest cuts as a diamond pickaxe, so obsidian and the like; each one doubles a Damage hit, up to 3. | a cut costs one more share for each; a hit doubles |
| <ItemLink id="quantimium:uv_lens" /> | Modifier | Hides the light: only someone holding a wrench or the Linker sees it. It stays on the light. | none |
| <ItemLink id="quantimium:place_lens" /> | Action | Places a block a Laser Port up the beam sends, in the space where the beam ends, as you would against the block it ends on; one every 0.5 s at most. Seeds count: on farmland they're sown. A bucket of water or lava empties there, and the empty bucket goes back to the port. A Filter lens in its mount says what it places. | 200 FE a block, when it's placed |

<GameScene zoom="2.9" interactive={true}>
  <ImportStructure src="../assets/structures/laser_farm.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

**A wheat field.** Point a beam down at farmland through a Laser Port set to both, holding seeds, and a mount
with Harvest, Pickup, Placement and a Block Filter Lens set to ripe only. Only ripe wheat is cut, it comes
back up to the port, and the spot is sown again. Spread it over a whole field with an Area Prism.
A cut waits until the ports up the beam would take all of its drops, seeds included, so give the
seeds somewhere to go. Above, a collecting port catches the harvest and a sending port below it holds
the seed.

**How far light reaches.** A run of light reaches as far as its source's:
mirrors, relays, prisms and mounts refocus it to that, not to a range of their own. Light with an action reaches
8 blocks a run instead, and the power it carries goes no farther. Each Range
lens doubles a run, up to 64 blocks by default (`maxRange` in the `lasers` config). Where light joins, the farther reach wins.

**Joining light.** Light from two sides joined at a relay or prism does
everything either side did: Harvest light from one emitter and Damage light from another, joined, both
work from there on. A modifier from before the join stays with its own side's actions; one after
changes them all.

**Who pays.** An action is paid for by the sources whose light reached
the lens that gave it, and only for work done: a cut as it's made, a hit when it lands, an item as it's
carried. Harvest light joined with plain light is still paid for by its own emitter alone; a Harvest
lens after the join is paid for by both. Actions only work while their light is lit.

A <ItemLink id="quantimium:laser_port" /> passes light, out of its face, as a relay does,
and holds nine slots. It starts out **collecting**: it catches what a Pickup lens further along brings
back, for pipes and hoppers to take from, or take from it yourself. Put it between the emitter and the
mount: emitter, Laser Port, Lens Mount with Harvest and Pickup, then the blocks to break. What one port
can't take goes on up the beam to the next. A stream from one side of a join goes up its own side, or to
ports after the join. Open it to set a filter on what it catches, nine
entries, as a whitelist or a blacklist, with tags: what it turns away goes on up the beam.

Set to **send**, it works the other way round: what's in its slots goes down
the beam, to a Placement, Insert or Interact lens further along, or once a second a stack at a time to the
nearest collecting port down the beam with room, not past an Isolator. The light reaching it pays to
carry it, and only while it's lit: a switched-off relay before it, or on the way, stops it. A bound
Tesseract in a slot sends from the inventory it links to.

Its mode tab steps through **collect**, **send** and **both**. In both, what
comes back up the beam can go straight back down: a port of seeds on a wheat field catches the harvest
and sows the field again from it.

Use the **Sides** tab to set, face by face, what pipes and hoppers may do there (hand things out, take
them in; things only go in while it sends), and which faces push to, or pull from, what's beside them,
once a second.

An <ItemLink id="quantimium:area_prism" /> spreads the actions of light reaching it over an
area of any size: Harvest cuts each block there in turn, Damage hurts and Pickup gathers whatever's in it,
Placement fills each free space resting on a block. It works only on what the light reaching it
reaches (8 blocks for light with an action, more with Range lenses) and can see, along a clear line from
the prism, and while it's working it shows a beam into what it's working on, following what it's hitting as it moves. Work in an area costs
1.5 times as much. Use the <ItemLink id="quantimium:laser_linker" /> in the air to switch it to area mode, use it on two
corners, then on the prism; the area shows as you pick it, and holding the Linker shows the area of any
prism you look at. Sneak-click the prism with an empty hand to clear its area. Or open
the prism and set its area to a **cube** or a **sphere** round it instead, with a radius. Its actions
work the area **nearest first** (the prism's own height outwards, nearest columns first in each layer),
**top down** (a quarry: nothing falls into what's cleared) or **bottom up**, as set in its screen; after a
reload they carry on where they were.

Work that happens at once (a hit, an item carried, a block placed) is paid for first each tick, so
machines on the same light can't starve it; blocks are charged from what's left.

The <ItemLink id="quantimium:spectroscope" /> splits light into what it carries, as a real
spectroscope's prism does. Use it on any laser block to read the light leaving it: how far a run
reaches and from how many sources, each action with its modifiers, who pays and how many Laser Ports it
can reach, and each beam's length, end and power.

**Don't look into the beam.** Light carrying power blinds anything whose eyes
are in it, for 15 s. The <ItemLink id="quantimium:mirror_lens" /> protects the wearer.

## Logistics

A <ItemLink id="quantimium:logistics_controller" /> is a relay with a back: light in by
its back, where a focus is set, goes on out of its outputs (set as an emitter's are, never the back), with
light of its own. Light on any other side only charges it. Everything its light reaches, through mirrors, splitters and relays, joined with other light or not, is one
**logistics network**, as any light is a laser network. An Isolator lets power through but not the
network. One controller to a network: where light from two meets, both stop, go dark and say why.

<GameScene zoom="2.0" interactive={true}>
  <ImportStructure src="../assets/structures/logistics.snbt" />
  <IsometricCamera yaw="195" pitch="30" />
</GameScene>

*A controller's light through storage, a provider and a requester, each on a chest, to a Logistics Lectern.*
It fills its own store, up to 100,000 FE,
from light ending on it as any block that takes power does, or from cables on any face. From that store it
pays 100 FE for each move its receptacles make (up to a stack), and for
nothing else: it gives no power, so it charges no machines, and lens work past it is paid for by the other
sources whose light reaches the lens, as ever. Out of power, its receptacles wait.

**Lenses past the controller use the network.** Up the beam from a lens,
the controller stands for the whole network, after any Laser Port nearer the lens: what a Pickup or Extract
lens brings back goes into it (to requesters listing it, then storage), so they work as active providers;
what an Insert, Placement or Interact lens uses comes from it (providers, then storage), but only through a
Item Filter Lens, so they work as active requesters: unfiltered, they take nothing from the network (a Laser Port
up the beam still feeds them). As ever, a stream from one side of a join reaches back up its own side:
put the controller before the lenses, or on their side of the join.

A <ItemLink id="quantimium:logistics_receptacle" /> goes on the inventory it serves (a chest, a machine, a drawer), and light
passes straight through it, so one beam along a row of machines joins them all; light onto its outer face joins it too, and stops there. Open it to set what that
inventory is to the network, its priority, and a filter of nine entries, as a whitelist or a blacklist,
with tags (empty, storage and providers take or give everything; a requester asks for nothing as a whitelist,
everything as a blacklist). Its plate lights in its role's colour while a controller's
light reaches it. Each active part moves as an Insert or Extract lens does: up to 16 items a second, four times as many for each Speed lens the light met on its way to it (up to three). Idle, it looks less often, waiting twice as long each time up to 4 seconds (a config option), and half as long again after each move:

- **Storage**: where the network keeps things. What comes in fills
  the highest priority first, as far as its filter lets in; what's taken goes from the lowest first.
- **Provider**: passive, the network may take what its filter lets go
  (a furnace's output, say); active, it pushes it out itself, to requesters listing it first, then into
  storage.
  On a machine that makes things to order (a Quantum Crafter), what it would make is offered as craftable.
  A tab on its screen says what it may make from: anything (stored items and what other machines would make,
  a chain), stored items only, or nothing, offering only what's really in the machine's outputs.
- **Requester**: passive, it takes what active providers push, as its
  filter lets in; active, it pulls what its filter lists from providers first, then storage, what's stored
  before anything is made. A tab on an active requester says whether machines may make what isn't stored for
  it, or it takes only what's stored.
  Only what its filter lists comes, as far as the inventory takes it. With **Keep stocked** on, each
  entry's count (the stack you set it with; scroll over it to change, shift for 16) is how many it keeps
  there: it takes only what it's short of.

A <ItemLink id="quantimium:laser_port" /> on the network, collecting, is a passive provider of
what it's caught. A receptacle on a Laser Port works with its nine slots whatever its faces are set to, in
any role.

Light from a controller is drawn a deeper blue, and always as if at work.

A <ItemLink id="quantimium:logistics_terminal" /> where a controller's light ends shows
everything the network's storage holds and its providers offer, searchable and sorted by count or name,
like an ME terminal; sort and view are tabs on its left. Click an item for a stack on the cursor,
right-click for half, shift-right-click for one, shift-click for a stack into your inventory; while
shift is held the grid holds still, and shift with the wheel takes one more onto the cursor (down) or
puts one back (up). Click the grid holding something to put it away into storage (right-click: one), or
shift-click it in your inventory. What's stored comes out first: if any is, that's all you get, and only when
none is left does a machine make more. Each take or put costs the controller as a move does. A <ItemLink id="quantimium:logistics_lectern" /> is the same terminal on a stand, its screen tilted over the focus the light comes in to: craft one from the other.

A <ItemLink id="quantimium:logistics_materialiser" /> where a controller's light ends
offers the whole network to any mod, as the Reactor's Materialiser Port offers the Reactor: an AE2 storage bus
or a pipe on any face sees everything storage holds and providers offer (a Reactor on the network, with what it
can make), takes from it, and puts things away into storage. Moves happen as part of the other side's transaction,
so one called off puts everything back and looking is free; each move costs the controller as a receptacle's does.
Click it to choose what it offers: stored and craftable items (the default), stored items, or craftable items.
A provider Receptacle on another network's Materialiser brings that network's offers into its own, stored as
stored and craftable as craftable. A receptacle of the same network on its Materialiser is ignored: it would read
the network into itself.

A <ItemLink id="quantimium:reactor_logistics_port" /> on the Reactor's plinth rim, where a
controller's light ends, joins the Reactor to that network both ways, as the ME Superposition Port does for
an ME network. Open it to set it.

- To the network it's storage, at its priority. It offers what the Reactor holds, what its ME network holds
  (passed through), and what could be made from all of it, made when it's taken: a terminal shows those as
  craftable. Its filter says what it offers. Set to store, what the network puts in feeds the horizon.
- Set to share (the default), what the network holds is the Reactor's to make with, as an ME network's is,
  and shows on through an ME Superposition Port.

Light ending on it also powers the Reactor.

**Big networks.** What a network offers, and what its Crafters could make, is worked out
in the background, a little each tick within a time budget (config `performance.workBudgetMicros`, 2 ms a tick
by default), most urgent first: a terminal or Crafter screen someone has open goes first. A crafting table on a
network with a lot in it can preview a thousand crafts, so its list fills in over a few ticks rather than at
once, and the server carries on meanwhile. Taking or crafting something never waits on it.

## Into the mirror

Laser light along a <ItemLink id="quantimium:rift_lens" />'s axis goes through the tear in its middle
into the mirror, and through another rift back out of it. The tear opens while lit light passes. A rift
doesn't refocus the light or take anything for it. Light in the mirror:

- acts only on what's in the mirror. Harvest takes a fully grown <ItemLink id="quantimium:anomalite_crystal" /> apart, on the
  mirror's side, for its shards (fifteen seconds at 60 FE/t), and the tear leaks anomaly and now and
  then a mirror mite. Without Pickup the shards fall on the mirror's side: only a phased
  player sees them or can take them, and only mirrored Pickup light carries them off. Damage hits only the
  Veiled and mirror mites. Placement does nothing.
- draws <ItemLink id="quantimium:veil_thread" /> with Pickup: through a Containment Hall's glass, into the cell where a Veiled
  is held, it draws a thread every three minutes at 120 FE/t, one draw to a hall at a time, and the thread goes
  up the beam to a Laser Port.
- charges nothing of the real world: a machine just stops it.
- is stopped by solid blocks as any light is: blocks are solid in the mirror too.
- can only be seen from in the mirror, or through a <ItemLink id="quantimium:mirror_lens" />.

Light passes a Containment Hall's attuned glass, as it does any glass.

## What stops light

Anything solid in the way stops a beam: it ends on that block, and charges
it only if it takes power. Light passes air, glass, glass panes, and blocks with nothing to bump into.
A beam is checked again every 0.5 s or so, so building across it cuts it
off within a moment, and clearing it lets the light through again.

A run of light only reaches so far: its source's range, or less for light with an
action, or more with Range lenses. A block beyond it gets nothing.

## Networks

Emitters, mirrors, relays, prisms, cavities, photodetectors, Lens Mounts and Laser Ports joined by light make one network. Power goes where it's asked for: every block
the network's light reaches is charged as far as the network's emitters have power, shared between
the emitters whose light reaches it in proportion to what each has. An emitter only ever pays for the
blocks its own light reaches. Light reaches a block by the fewest relays it can.

When a beam's action changes the world along it (a block cut, one
placed, a door opened), only the network that beam belongs to is worked out again; every other network in
the level carries on as it was. Placing or breaking a laser block works everything out again.

Use the Flux Meter, or look with Jade, at an emitter, mirror or relay to see what it's sending or passing on.
Beams glow brighter the more they carry. A switched-on emitter with power keeps
a dim beam lit even when nothing at its end needs power, along with the mirrors, relays and prisms that light
reaches: it's checking for somewhere to send power, and that's what its small ready cost pays for.

Emitters whose light reaches the same block share a network, so that block
draws on all of them by what each has.
