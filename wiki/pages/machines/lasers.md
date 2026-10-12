# Lasers

*Light kept in step carries power across a room.*

Laser emitters send power as light. A beam crosses open air, glass and anything you can walk
through (grass, torches, flowers), and charges the first block it meets that takes power, from any
mod. Mirrors and relays turn the light around corners, and prisms split and join it at any angle; lenses give it
something to do. Items carried by light come later.

!!! info "Badges"
    Badges like [[mechanic:laser.power]] show whether an in-game test backs the rule next to them. See
    [Mechanics coverage](../reference/coverage.md).

## Emitters

[[mechanic:laser.power]] An emitter takes power on any face and sends it out as light from its
outputs. Placed, it faces you, and its light comes out towards you.

[[mechanic:laser.demand]] It sends only what the blocks its beams reach ask for, up to its rate: point
one at a full battery, or at nothing, and it keeps its power, but for a small cost of being ready.

| Emitter | Rate | One run reaches | Holds | Ready cost |
| --- | --- | --- | --- | --- |
| [[block:flash_emitter]] | 200 FE/t | 8 blocks | 20,000 FE | none |
| [[block:laser_emitter_1]] | 2,000 FE/t | 16 blocks | 200,000 FE | 2 FE/t |
| [[block:laser_emitter_2]] | 32,000 FE/t | 32 blocks | 3,200,000 FE | 16 FE/t |
| [[block:laser_emitter_3]] | 512,000 FE/t | 64 blocks | 51,200,000 FE | 128 FE/t |

These are starting values for the first playtest.

[[mechanic:laser.flash]] The **Flash Emitter** can also burn furnace fuel: each tick of burn time
makes {{c:LaserTuning.FLASH_FE_PER_BURN_TICK}} FE, so a coal gives 160,000. It's power for a base that
has no generator yet. It only starts a new fuel once it has room for what that makes.

[[mechanic:laser.solar]] A [[block:solar_emitter]] gathers sunlight through its glass into a small focus
and sends it out of a simple emitter on one side as light: {{c:LaserTuning.SOLAR_FE}} FE/t in full sun,
less as the sky dims (it reads the sky as a daylight detector does: dawn, dusk and rain give less), none
at night or under a roof. It holds nothing. Light that reaches it from any laser (another Solar Emitter,
a Laser Emitter, by mirrors or not) joins its own and leaves by its emitter, but no more than
{{c:LaserTuning.SOLAR_CHAIN_MAX}} FE/t leaves it in all, what it makes included: so a row of eight in full
sun is as much as one row gives. Its light reaches {{c:LaserTuning.SOLAR_RANGE}} blocks a run. Placed, it
shines towards you; sneak-click a side (by hand or with a wrench) to send its light there, but never up,
through its glass.

[[mechanic:laser.outputs]] Sneak-click a face with an empty hand, or use any wrench on it, to add an
output there, or take one away. One emitter can shine from all six faces; it always keeps at least one.

[[mechanic:laser.redstone]] Open an emitter to see what it's sending and to set how it listens to
redstone: not at all, on only with a signal, or off with a signal.

## Mirrors and relays

[[mechanic:laser.mirror]] A [[block:laser_mirror]] is a silver plate on a steel stand that turns light
through a right angle, both ways. It stands on the block it was placed against, for good. Its plate faces
half way between two sides at right angles, neither the one it stands on: click one side (by hand or with
a wrench) and sneak-click another. Light in by either side leaves by the other; light from anywhere else
hits the back of the plate and stops. Sneak-click the side it already takes light from and it's **flat**, facing that side: light goes straight back the way it came, and facing a
Cavity Mirror across a gap it's the far end of the cavity. It never sends light straight on. A redstone
signal turns it off.

[[mechanic:laser.splitter]] A [[block:laser_splitter]] is a Laser Mirror with a half-silvered plate, set
the same way (never flat): click its **input side**, sneak-click its **output (turn) side**, at right
angles, as you sneak-click a Solar Emitter's output; sneak-clicking its input side swaps the two. Its plate has a light-blue mark in the middle, and a
notch on its rim by the output side; the Flux Meter or Jade names both sides.
Light in by its input side splits: half goes straight on, half turns out of its turn side. Light from
behind its turn side passes straight through the plate and out of the turn side too; with both coming
in, they leave by the turn side only, joined: a combiner. Light from any other side stops on it, and so
does light into a side facing what it stands on. It closes no cavity.

[[mechanic:laser.relay]] A [[block:laser_relay]] is a focus on a post, on a base on the block it was
placed against, with an emitter on any of its other sides. Light reaching its focus, from any side but
its base, leaves by each emitter, shared by what each beam's blocks ask for: a prism's job for the six
straight directions, with no linking. Placed, its one emitter faces you. Sneak-click (by hand or with a
wrench) a side of its focus to put an emitter there, or an emitter to take it away. Click an emitter to
open it: it holds one lens, which only the light leaving by that emitter goes through (a Harvest lens on
one emitter cuts only there), and its tip takes the lens's colour. Taking an emitter away drops its lens.

Both refocus the light to its source's range: a run from a mirror or relay reaches as far as a run from
the emitter that sent it, so a Flash Emitter's light goes 8 blocks a run however many it passes. Each
one a beam passes takes {{c:LaserTuning.HOP_TAX|pct}} of what passes, and that becomes flux where it
stands.

[[mechanic:laser.relay.redstone]] Each of a relay's emitters listens to the redstone reaching the block its
own way, set in its screen: it ignores it, lets light out only with a signal, only without one, or in
**pulse** mode, where each signal it gets lets light out until one action down its beam is done (a block
cut, a hit, an item carried) or two seconds pass. One signal can open one emitter and close another. A
pulse emitter before a Harvest lens breaks one block per pulse. An emitter switched off sends nothing; with
all of them off, the relay passes no light at all, and what's beyond gets only the light that reaches it
some other way. An emitter switched off by redstone gives out none.

[[mechanic:laser.isolator]] An [[block:isolator]] passes light on as a relay does, but lets only power
through: light leaving it carries nothing it did (actions, Pickup, UV), and nothing comes back up through
it, so Pickup's items stop short of it. It's a plain block: sneak-click or wrench a face to add an output
there or take one away (one always stays), and use an empty hand to step through how it listens to
redstone.

## Prisms

[[mechanic:laser.prism.split]] A [[block:laser_prism]] sends the light that reaches it to each block
it's linked to, along a straight line at any angle, sharing it by what each asks for.

[[mechanic:laser.prism.join]] Light can reach it from any side, from any number of beams, so a prism
also joins several emitters into one network: a block it's linked to can take more than any one of
them could send. It refocuses and taxes light as a relay does.

[[mechanic:laser.prism.links]] Link it with the [[item:laser_linker]]: use the Linker on the prism to
pick it, then on each block its light should reach, once to link it and again to unlink it. A prism
links up to {{c:LaserTuning.PRISM_LINKS}} blocks, within {{c:LaserTuning.PRISM_RANGE}} blocks. Sneak-use
the Linker in the air to let the prism go, and sneak-click the prism with an empty hand to let go of
all its links. A link to a block that's been removed, even one light passes through such as grass, is let go of by itself.
[[mechanic:laser.prism.keep]] Use an empty hand on a prism to make it keep those links instead: it goes on aiming at the
spot, so a Harvest lens's light breaks whatever comes back there.

Holding the Linker, or a wrench, shows every beam faintly, even ones carrying nothing, so you can see
where light would go.

## Optical cavities

[[mechanic:laser.cavity]] A [[block:cavity_mirror]] facing a flat [[block:laser_mirror]], a [[block:laser_relay]], or another Cavity
Mirror, across a clear gap of up to {{c:LaserTuning.CAVITY_MAX_GAP}} blocks holds light between them,
as a real laser's cavity does: a battery for a laser network. Only one end needs to be a Cavity Mirror.
Each Cavity Mirror holds {{c:LaserTuning.CAVITY_FE_PER_BLOCK}} FE for each block of gap, and takes or
gives up to {{c:LaserTuning.CAVITY_RATE}} FE/t; a Laser Mirror or Relay at the other end holds nothing.

With nothing facing it, a Cavity Mirror is just a relay: light that reaches it leaves by its front,
towards where its partner would stand. Once paired, what it holds leaves by its back instead.

- Light that reaches a cavity mirror from outside charges it, but only with what the network's
  emitters have left over once its blocks are served. One cavity never fills another.
- Light leaves by the mirror's **back**, its output coupler, for the blocks that ask for it. A network
  serves its blocks from its emitters first, then from its cavities. Use a wrench on a mirror to turn
  its back output off, or on again: a mirror with its output off only stores. Sneak-click a face with
  an empty hand to point the mirror there.
- With two Cavity Mirrors, the light bounces between them, so what either holds evens out between
  them: both glow, and both can give it out.
- Light reaching the Laser Relay at the far end is passed into the cavity, so that end can feed it.
- [[mechanic:laser.cavity.leak]] A cavity can leak: a pack can set it to lose a share of what it holds
  each tick, up to a cap, as flux where it stands. Out of the box it doesn't leak. Break a charged
  mirror, or block its gap, and the light it held all goes into the field at once.

The held light glows in one beam across the gap, brighter the more there is.

[[mechanic:laser.light.cavity]] **A cavity keeps what its light does.** Fill it with Harvest light and
the light it gives out harvests too; it reaches as far as the light that filled it. The cavity pays for
what its light does, from what it holds. Light from different sides mixes in it: it does all of it,
until it's empty and the next light to fill it decides.

## Photodetectors

[[mechanic:laser.photodetector]] A [[block:photodetector]] sits in a beam: light passes through it and
out of its face, and it takes nothing for it. It doesn't refocus the light, so the light goes on only as
far as it had left. It lights up while light passes, and gives a redstone
signal, and a comparator reading, for how much: 1 for a trickle, two more for each tenfold, up to 15.

## Lenses

[[mechanic:laser.lens.mount]] A [[block:lens_mount]] is a relay that holds lenses: light that reaches
it leaves by its face, refocused, through its four lens slots, left to right. The beam is still a beam:
it carries power to whatever it ends on, as before, and does what its lenses say along the way. Open
the mount with an empty hand; sneak-click or wrench a face to point it there. Under each lens is a
button for that lens's own settings (a Filter lens's, say), lit when it has any. Each lensed beam is
tinted by what it does.

**Actions** give light something to do, and light can carry several: Harvest and Damage on one beam
both work. An action stays on the light until the beam ends. [[mechanic:laser.lens.order]] **Modifiers**
change the actions the light already carries when it meets them: those in slots to their left, and those
from mounts before. Never one that comes after: Harvest then Filter cuts only what the filter lets, Filter
then Harvest cuts anything. Pickup is the exception: it stays on the light, and actions added after it get
it too, so Pickup then Harvest brings back what it cuts as Harvest then Pickup does (and waits when there's
no room).

| Lens | Kind | What it does | Costs |
| --- | --- | --- | --- |
| [[item:harvest_lens]] | Action | [[mechanic:laser.lens.harvest]] Cuts the block the beam ends on, or one light passes through just before it (a crop on farmland, grass), as an iron pickaxe would (a diamond one with an Amplify lens), and breaks it when the cut's through; the cracks show how far it's got. A block that goes before then takes the cut with it. One block every {{c:LaserTuning.HARVEST_COOLDOWN|s}} at most. It never breaks a block with a block entity (a machine, a chest), Anomalite, or what a wither can't break. With the block gone, the beam reaches on to the next. | {{c:LaserTuning.HARVEST_FE_PER_HARDNESS}} FE for each point of hardness, as it cuts |
| [[item:damage_lens]] | Action | [[mechanic:laser.lens.damage]] Hurts anything living that stands in the beam, players included, every {{c:LaserTuning.DAMAGE_INTERVAL|s}}. It charges up for its hits while there's something to hit, over as many ticks as it takes, and keeps what it's charged. With Pickup, what it kills drops straight back up the beam. | {{c:LaserTuning.DAMAGE_FE_PER_HIT}} FE a hit, charged as it goes |
| [[item:pickup_lens]] | Modifier, and an action of its own | [[mechanic:laser.lens.pickup]] What the light's actions free, and items lying within about a block of the beam, go back up it to the nearest Collector; what that one can't take goes to the next. Back past a cavity, it reaches the Collectors up the light that filled it. With no Collector, drops fall where they are. A Harvest beam with Pickup only cuts a block whose drops the Collectors up the beam would take, all of them: room, and filters that let them in. Otherwise it waits, and leaves nothing lying. | {{c:LaserTuning.PICKUP_FE_PER_STACK}} FE a stack, when it's carried |
| [[item:range_lens]] | Modifier | Each run of the light reaches twice as far, up to 64 blocks (a pack can change that, `maxRange` in the `lasers` config): light with an action goes 8, 16, 32, 64. | none |
| [[item:interact_lens]] | Action | [[mechanic:laser.lens.interact]] Uses one thing a Sender up the beam sends, or an empty hand with none, as a player would, each {{c:LaserTuning.PLACE_COOLDOWN|s}}. Set to **use**: with something in hand, a right click on the first thing living in the beam that takes it (feeding, shearing, milking; a Filter after it says which: only grown ones, say; never a villager); else a right click on the block the beam ends on, or on something light passes just before it (a lever, a button), so it opens doors, flips levers, works items on blocks; an empty bucket fills from the fluid the beam ends on. Set to **hit**: a swing at the first thing living in the beam, as hard as what it holds. It works with what the Sender holds where it lies, trying each kind of thing there in turn (carrots, then wheat, then shears) till one is used: nothing moves unless using it changes it. Then what it became goes back where it belongs: still the same thing (a worn sword) to the Sender, anything new (a filled bucket) up the beam to a Collector. Through an Area Prism it works on the area: with something in hand, on what's living there first; else on its blocks in turn, each from the side facing the prism (bone meal over a field, say). It does it over and over: behind a pulse mirror, once a signal. Use it in the air, or its button in a mount, to switch. | {{c:LaserTuning.PLACE_FE}} FE a use |
| [[item:insert_lens]] | Action | [[mechanic:laser.lens.insert]] Puts what Senders up the beam send into the inventory the beam ends on, up to {{c:LaserTuning.MOVE_ITEMS}} items each {{c:LaserTuning.PLACE_COOLDOWN|s}} (each Speed lens doubles that, to 64, and halves the wait); with an Area Prism, each inventory in its area in turn. A Filter lens says what. | {{c:LaserTuning.PICKUP_FE_PER_STACK}} FE a stack |
| [[item:extract_lens]] | Action | [[mechanic:laser.lens.extract]] Takes from the inventory the beam ends on, up to {{c:LaserTuning.MOVE_ITEMS}} items each {{c:LaserTuning.PLACE_COOLDOWN|s}} (each Speed lens doubles that, to 64, and halves the wait), as much as the Collectors up the beam have room for, back up to them; with an Area Prism, from each inventory in its area in turn. A Filter lens says what. | {{c:LaserTuning.PICKUP_FE_PER_STACK}} FE a stack |
| [[item:precision_lens]] | Modifier | [[mechanic:laser.lens.precision]] Harvest cuts with Silk Touch: blocks come away whole. | none |
| [[item:harmonic_lens]] | Modifier | [[mechanic:laser.lens.harmonic]] Harvest cuts with Fortune, and Damage hits with Looting, a level for each, up to {{c:LaserTuning.HARMONIC_LENSES}}. | none |
| [[item:unrealising_lens]] | Modifier | [[mechanic:laser.lens.unrealise]] Ore a Harvest beam cuts (anything in `#c:ores`) comes away as [[item:unrealised_matter]], one to a block, with no history yet. A pack can turn it off in the config, where Matter would duplicate ores. | none |
| [[item:smelting_lens]] | Modifier | [[mechanic:laser.lens.smelting]] Smelts, as a furnace would, what the actions before it free or move: what a Harvest beam cuts and what Damage kills (picked up or left lying), what Pickup gathers, what Insert puts in and Extract takes out. Sand becomes glass, ore ingots, a pig's drops cooked. Unrealised Matter gets smelting in its history, as a Quantum Crafter with a furnace gives it. What a furnace can't smelt stays as it is, for nothing. With no other action on the light it's an action of its own: it smelts what lies in the beam where it lies, else the block the beam ends on, every {{c:LaserTuning.PLACE_COOLDOWN|s}}. A block it smelts into takes its place (sand becomes glass, and the light goes on through to the next); anything else drops there. | {{c:LaserTuning.SMELT_FE_PER_ITEM}} FE for each item it smelts |
| [[item:crushing_lens]] | Modifier | [[mechanic:laser.lens.crushing]] Crushes what the actions before it free or move, everywhere Smelting smelts: stone to cobblestone, cobblestone and deepslate to gravel, gravel and sandstone to sand, and anything else as the pack's crushers would, their sure outputs only (raw ore to dust, say). Which crushers, and in what order, is `crushingRecipeTypes` in the `lasers` config: Energized Power's pulverizer and crusher, Ender IO's SAG Mill, Mekanism, Create, Thermal and others. Each Crushing or Smelting lens is a step, in the lenses' order (up to {{c:LaserTuning.PROCESS_STEPS}}): Crushing then Smelting makes raw ore dust, then ingots; Crushing, Crushing, Smelting makes cobblestone gravel, then sand, then glass. A step that changes nothing is free, and so is a run of steps that ends as it began (glass crushed to sand and smelted again). Where nothing says which came first (a crushing beam and a smelting beam meeting at a mirror) it crushes first. Where they change no action before them (alone, or before one: Crushing, Smelting, Harvest), they act on the world themselves, as one, in their order, and a Harvest after them waits till they've done: sandstone becomes glass, then it's cut. Unrealised Matter gets the first of those crushers that gives it something new in its history. Alone it crushes what lies in the beam, else the block the beam ends on, in place: stone becomes cobblestone, then gravel, then sand. With Phantom, it goes through what it can't change to the first block on its way it can (Smelting too). | {{c:LaserTuning.CRUSH_FE_PER_ITEM}} FE for each item it crushes |
| [[item:phantom_lens]] | Modifier | [[mechanic:laser.lens.phantom]] Light passes through blocks: only laser blocks, and blocks it charges, stop it. A Harvest beam with it cuts the first block on its way it's allowed to (with a Filter set to ores, just the ore in the ground; with Pickup, it passes over what no Collector up the beam would take); an Area Prism with it reaches what it couldn't see. It stays on the light. | each tick it's lit, a configurable FE (15 by default, `phantomFePerBlock`) for each block it passes through; for an Area Prism, for each block between it and what it works on |
| [[item:speed_lens]] | Modifier | [[mechanic:laser.lens.speed]] Its actions work twice as fast: a Harvest beam waits half as long between blocks, Placement too, and an Area Prism looks through its area twice as fast. Three count, for eight times. | none |
| [[item:filter_lens]] | Modifier | [[mechanic:laser.lens.filter]] The actions it changes act only on what it lists: nine entries, a whitelist or a blacklist, with tags, and which living things a hit may land on (any, hostile, passive, players, all but players). A block counts by what it drops, so "wheat" lets only ripe wheat be cut. It decides what each action works on (which blocks Harvest cuts, what Damage hits, which items Pickup gathers from the ground, what Placement places), not what happens to the drops: a cut block's drops, and a kill's, all come back with Pickup. **Fully grown** lets a Harvest beam cut only ripe crops (and nothing that doesn't grow, so never the farmland), and a Damage beam spare the young. A **keep** count spares that many of what a Damage beam would hit: it hits only those beyond it, so a pen keeps its breeding pair. Use it in the air to set it. | none |
| [[item:amplify_lens]] | Modifier | [[mechanic:laser.lens.amplify]] Harvest cuts as a diamond pickaxe, so obsidian and the like; each one doubles a Damage hit, up to {{c:LaserTuning.AMPLIFY_LENSES}}. | a cut costs one more share for each; a hit doubles |
| [[item:uv_lens]] | Modifier | Hides the light: only someone holding a wrench or the Linker sees it. It stays on the light. | none |
| [[item:place_lens]] | Action | [[mechanic:laser.lens.place]] Places a block a Sender up the beam sends, in the space where the beam ends, as you would against the block it ends on; one every {{c:LaserTuning.PLACE_COOLDOWN|s}} at most. Seeds count: on farmland they're sown. A bucket of water or lava empties there, and the empty bucket goes back to the Sender. A Filter lens in its mount says what it places. | {{c:LaserTuning.PLACE_FE}} FE a block, when it's placed |

**A wheat field.** Point a beam down at farmland through a Collector, a Sender of seeds, and a mount
with Harvest, Pickup, Placement and a Filter lens set to fully grown. Only ripe wheat is cut, it comes
back up to the Collector, and the spot is sown again. Spread it over a whole field with an Area Prism.
A cut waits until the Collectors up the beam would take all of its drops, seeds included, so give the
seeds somewhere to go. Use Fully grown rather than a "wheat" entry here: an entry also stops Pickup
gathering items from the ground.

[[mechanic:laser.light.range]] **How far light reaches.** A run of light reaches as far as its source's:
mirrors, relays, prisms and mounts refocus it to that, not to a range of their own. Light with an action reaches
{{c:LaserTuning.ACTION_REACH}} blocks a run instead, and the power it carries goes no farther. Each Range
lens doubles a run, up to 64 blocks by default (`maxRange` in the `lasers` config). Where light joins, the farther reach wins.

[[mechanic:laser.light.join]] **Joining light.** Light from two sides joined at a relay or prism does
everything either side did: Harvest light from one emitter and Damage light from another, joined, both
work from there on. A modifier from before the join stays with its own side's actions; one after
changes them all.

[[mechanic:laser.light.payers]] **Who pays.** An action is paid for by the sources whose light reached
the lens that gave it, and only for work done: a cut as it's made, a hit when it lands, an item as it's
carried. Harvest light joined with plain light is still paid for by its own emitter alone; a Harvest
lens after the join is paid for by both. Actions only work while their light is lit.

[[mechanic:laser.collector]] A [[block:collector]] passes light, out of its face, as a relay does, and
catches what a Pickup lens further along brings back: nine slots, for pipes and hoppers to take from,
or take from it yourself. Put it between the emitter and the mount: emitter, Collector, Lens Mount with
Harvest and Pickup, then the blocks to break. What one Collector can't take goes on up the beam to the
next. A stream from one side of a join goes up its own side, or to Collectors after the join.
[[mechanic:laser.collector.filter]] Open it to set a filter, nine entries, as a whitelist or a blacklist,
with tags: what it turns away goes on up the beam.

[[mechanic:laser.sender]] A [[block:sender]] is a Collector the other way round: light passes through
it, and what's in its nine slots goes down the beam, to a Placement lens further along, or once a second
a stack at a time to the nearest Collector down the beam with room, not past an Isolator. The light
reaching it pays to carry it, and only while it's lit: a switched-off relay before it, or on the way,
stops it. A bound Tesseract in a slot sends from the inventory it links to.

[[mechanic:laser.collector.mode]] Each can be set, on its screen, to **collect**, **send** or **both**: a
Collector starts collecting and a Sender sending, but either does either. In both, what comes back up
the beam can go straight back down: a Collector of seeds on a wheat field catches the harvest and sows
the field again from it, with no Sender.

Open a Collector or Sender and use the **Faces** tab to set, face by face, what pipes and hoppers may
do there (a Collector's faces hand things out, a Sender's take them in), and which faces push to, or
pull from, what's beside them, once a second.

[[mechanic:laser.area_prism]] An [[block:area_prism]] spreads the actions of light reaching it over an
area of any size: Harvest cuts each block there in turn, Damage hurts and Pickup gathers whatever's in it,
Placement fills each free space resting on a block. It works only on what the light reaching it
reaches (8 blocks for light with an action, more with Range lenses) and can see, along a clear line from
the prism, and while it's working it shows a beam into what it's working on, following what it's hitting as it moves. Work in an area costs
{{c:LaserTuning.AREA_COST}} times as much. Use the [[item:laser_linker]] in the air to switch it to area mode, use it on two
corners, then on the prism; the area shows as you pick it, and holding the Linker shows the area of any
prism you look at. Sneak-click the prism with an empty hand to clear its area. [[mechanic:laser.area_prism.shape]] Or open
the prism and set its area to a **cube** or a **sphere** round it instead, with a radius.

Work that happens at once (a hit, an item carried, a block placed) is paid for first each tick, so
machines on the same light can't starve it; blocks are charged from what's left.

[[mechanic:laser.spectroscope]] The [[item:spectroscope]] splits light into what it carries, as a real
spectroscope's prism does. Use it on any laser block to read the light leaving it: how far a run
reaches and from how many sources, each action with its modifiers, who pays and how many Collectors it
can reach, and each beam's length, end and power.

[[mechanic:laser.blind]] **Don't look into the beam.** Light carrying power blinds anything whose eyes
are in it, for {{c:LaserTuning.BLIND_TICKS|s}}. The [[item:mirror_lens]] protects the wearer.

## Into the mirror

[[mechanic:laser.rift]] Laser light along a [[block:rift_lens]]'s axis goes through the tear in its middle
into the mirror, and through another rift back out of it. The tear opens while lit light passes. A rift
doesn't refocus the light or take anything for it. Light in the mirror:

- acts only on what's in the mirror. Harvest takes a fully grown [[block:anomalite_crystal]] apart, on the
  mirror's side, for its shards, as a [[block:harvest_laser]] does, and the tear leaks anomaly and now and
  then a mirror mite the same way. Without Pickup the shards fall on the mirror's side: only a phased
  player sees them or can take them, and only mirrored Pickup light carries them off. Damage hits only the
  Veiled and mirror mites. Placement does nothing.
- draws [[item:veil_thread]] with Pickup: through a Containment Hall's glass, into the cell where a Veiled
  is held, it draws a thread at a Harvest Laser's pace, one draw to a hall at a time, and the thread goes
  up the beam to a Collector.
- charges nothing of the real world: a machine just stops it.
- is stopped by solid blocks as any light is: blocks are solid in the mirror too.
- can only be seen from in the mirror, or through a [[item:mirror_lens]].

Light passes a Containment Hall's attuned glass, as it does any glass.

## What stops light

[[mechanic:laser.blocked]] Anything solid in the way stops a beam: it ends on that block, and charges
it only if it takes power. Light passes air, glass, glass panes, and blocks with nothing to bump into.
A beam is checked again every {{c:LaserNetworks.RECHECK_TICKS|s}} or so, so building across it cuts it
off within a moment, and clearing it lets the light through again.

[[mechanic:laser.range]] A run of light only reaches so far: its source's range, or less for light with an
action, or more with Range lenses. A block beyond it gets nothing.

## Networks

Emitters, mirrors, relays, prisms, cavities, photodetectors, Lens Mounts and Collectors joined by light make one network. Power goes where it's asked for: every block
the network's light reaches is charged as far as the network's emitters have power, shared between
the emitters whose light reaches it in proportion to what each has. An emitter only ever pays for the
blocks its own light reaches. Light reaches a block by the fewest relays it can.

Use the Flux Meter, or look with Jade, at an emitter, mirror or relay to see what it's sending or passing on.
[[mechanic:laser.live]] Beams glow brighter the more they carry. A switched-on emitter with power keeps
a dim beam lit even when nothing at its end needs power, along with the mirrors, relays and prisms that light
reaches: it's checking for somewhere to send power, and that's what its small ready cost pays for.

[[mechanic:laser.share]] Emitters whose light reaches the same block share a network, so that block
draws on all of them by what each has.
