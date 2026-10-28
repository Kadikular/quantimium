# Lenses


[[mechanic:laser.lens.mount]] A [[block:lens_mount]] holds four lenses in a row, and light goes straight
through it: in by its back, where half a focus is set in a recess, and out of its front, where half an
emitter is, refocused, through its lenses in order (its four slots, left to right). Light from any other
side, or at an angle, stops on it. Down each side, four slot panels show what's fitted: dark when empty, the lens's color
when there's one. The beam is still a beam: it carries power to whatever it ends on, as before, and does what its
lenses say along the way. Open the mount with an empty hand; sneak-click or wrench a side to point it
there. Under each lens is a button for that lens's own settings (a filter lens's, say), lit when it has
any. Each lensed beam is tinted by what it does.

**Actions** give light something to do, and light can carry several: Harvest and Damage on one beam
both work. An action stays on the light until the beam ends. [[mechanic:laser.lens.order]] **Modifiers**
change the actions the light already carries when it meets them: those in slots to their left, and those
from mounts before. Never one that comes after: Harvest then Filter cuts only what the filter lets, Filter
then Harvest cuts anything. Pickup works by the same rule, two ways:

| Where Pickup is | What it does |
|---|---|
| After an action (Harvest then Pickup, in one mount or a later one) | Brings back what that action frees, and the action waits while there's nowhere to put it |
| Before any action (Pickup then Harvest), or alone | Gathers whatever lies in the beam: what Harvest cuts, leaves decaying, anything dropped there |

Both on one beam (Pickup, Harvest, Pickup) do both: a tree farm's logs come back as they're cut, and its leaves
as they fall.

**The Laser Ports are the beam's item ends.** A collecting [[block:laser_port]] up the light is where what it frees
goes back to; a sending one is where what it uses comes from. Pickup and Void aren't about any one action: they're
rules for that traffic. Pickup says "carry what's freed back to the Ports"; Void says "what the Ports won't take (or,
set to void all, everything) is destroyed". So where they sit matters less than for other modifiers: once the light
has met a Void lens, every action on it from there on gets it, and every way items travel along the beam follows
it:

| Items on the move | With Void excess | With Void all |
|---|---|---|
| Freed (Harvest's drops, what Damage kills, Pickup's gathering) | What no Port has room for is destroyed | Destroyed |
| Sent down to Insert | Put in where there's room; the rest destroyed | Destroyed, whatever the beam ends on: a trash can |
| Taken out by Extract (only with a list on the Void lens) | Listed things taken, what no Port has room for destroyed | Listed things taken and destroyed |
| What Interact hands back (an empty bucket, a worn tool) | Destroyed if no Port takes it | The same |

A Void lens's list (allow or deny) says what it may destroy; anything else is carried, or waits for room, as if
it weren't there. Place never destroys what it can't place.

| Lens | Kind | What it does | Costs |
| --- | --- | --- | --- |
| [[item:harvest_lens]] | Action | [[mechanic:laser.lens.harvest]] Cuts the block the beam ends on, or one light passes through just before it (a crop on farmland, grass), as an iron pickaxe would (a diamond one with an Amplify lens), and breaks it when the cut's through; the cracks show how far it's got. A block that goes before then takes the cut with it. One block every {{c:LaserTuning.HARVEST_COOLDOWN|s}} at most. It never breaks a block with a block entity (a machine, a chest), Anomalite, or what a wither can't break. With the block gone, the beam reaches on to the next. | {{c:LaserTuning.HARVEST_FE_PER_HARDNESS}} FE for each point of hardness, as it cuts |
| [[item:damage_lens]] | Action | [[mechanic:laser.lens.damage]] Hurts anything living that stands in the beam, players included, every {{c:LaserTuning.DAMAGE_INTERVAL|s}}. It charges up for its hits while there's something to hit, over as many ticks as it takes, and keeps what it's charged. With Pickup, what it kills drops straight back up the beam. | {{c:LaserTuning.DAMAGE_FE_PER_HIT}} FE a hit, charged as it goes |
| [[item:healing_lens]] | Action | [[mechanic:laser.lens.heal]] Heals anything living in the beam, players included, by {{c:LaserTuning.HEAL_PER_SWEEP}} health (a heart) every {{c:LaserTuning.DAMAGE_INTERVAL|s}}. The undead it hurts instead, by {{c:LaserTuning.HEAL_HARM_PER_SWEEP}}, as Instant Health does. Each [[item:amplify_lens]] doubles both, and the cost; each [[item:speed_lens]] heals twice as often, up to four times as often with two. What's at full health is left alone and costs nothing. An [[item:entity_filter_lens]] picks who it works on. | {{c:LaserTuning.HEAL_FE_PER_SWEEP}} FE for each one healed or hurt, charged as it goes |
| [[item:freezing_lens]] | Action | [[mechanic:laser.lens.freeze]] Freezes what the beam ends on. A machine stops ticking while the light holds it, and starts again the moment it doesn't; blocks tagged `quantimium:not_freezable` never stop, and get no ice or snow either. Water it ends on turns to ice, and now and then snow settles on top of what it ends on. Mobs in the beam stop thinking for {{c:LaserTuning.FREEZE_MOB_TICKS|s}}, frozen again once half of that has passed; each point of damage they take thaws them half a second sooner, and mobs tagged `quantimium:not_freezable` (the Ender Dragon) are never frozen. The Wither and the Warden can be. Players get colder a tick at a time while they stand in it, as in powder snow: slower the colder, but never so frozen it hurts. Through an Area Prism, every machine in the area, everything living there, and a few of its blocks at random each sweep. | {{c:LaserTuning.FREEZE_MACHINE_FE}} FE/t for each machine; {{c:LaserTuning.FREEZE_FE_PER_HEALTH}} FE a mob's health point to freeze it (a zombie 500, a Warden 12,500); {{c:LaserTuning.FREEZE_CHILL_FE}} FE/t to chill a player; {{c:LaserTuning.FREEZE_BLOCK_FE}} FE for ice or snow |
| [[item:redstone_lens]] | Action | [[mechanic:laser.lens.redstone]] Gives what the beam ends on a redstone signal: strong, as a lever on it would (it powers through that block), or weak, as dust would (that block only). It comes from the air in front of the block, or from the laser block the beam leaves if there's no gap. A [[item:laser_focus]] it ends on is switched by it as by a wired signal, and so on down the light: each focus it switches passes the lens's light on to the next. Set it (use it in the air) to give its signal always, to send on the signal its own block gets (wireless redstone), or as an AE2 level emitter does: while the Laser Ports up the light, with the inventories they move items to and from, hold fewer than a count of one item, or that many or more (counted once a second). Its strength is 1 to 15. On a Logistics Controller's light it counts what that network holds instead. An Isolator Lens stops its signal unless set to pass redstone. Nothing in the mirror. | none |
| [[item:pickup_lens]] | Modifier, and an action of its own | [[mechanic:laser.lens.pickup]] After an action, what that action frees goes back up the light; before any, or alone, so do items lying within about a block of the beam. It all goes to the nearest collecting Laser Port; what that one can't take goes to the next. Back past a cavity, it reaches the ports up the light that filled it. With none, drops fall where they are. A Harvest beam with Pickup only cuts a block whose drops the ports up the beam would take, all of them: room, and filters that let them in. Otherwise it waits, and leaves nothing lying. | {{c:LaserTuning.PICKUP_FE_PER_STACK}} FE a stack, when it's carried |
| [[item:range_lens]] | Modifier | Each run of the light reaches twice as far, up to 64 blocks (a pack can change that, `maxRange` in the `lasers` config): light with an action goes 8, 16, 32, 64. | none |
| [[item:interact_lens]] | Action | [[mechanic:laser.lens.interact]] Uses one thing a Laser Port up the beam sends, or an empty hand with none, as a player would, each {{c:LaserTuning.PLACE_COOLDOWN|s}}. Set to **use**: with something in hand, a right click on the first thing living in the beam that takes it (feeding, shearing, milking; a Filter after it says which: only grown ones, say; never a villager); else a right click on the block the beam ends on, or on something light passes just before it (a lever, a button), so it opens doors, flips levers, works items on blocks; an empty bucket fills from the fluid the beam ends on. Set to **hit**: a swing at the first thing living in the beam, as hard as what it holds; with nothing living there, it mines the block the beam ends on as a player holding what it holds would (the drops fall there, the tool wears, its enchantments count), for a Harvest lens's FE by hardness. **What it works on** is a second setting: blocks and living things (the default), blocks only, or living things only. A tool bound to an [[block:entangled_dock]] is charged from the dock as it's used. It works with what the port holds where it lies, trying each kind of thing there in turn (carrots, then wheat, then shears) till one is used: nothing moves unless using it changes it. Then what it became goes back where it belongs: still the same thing (a worn sword) to the sending port, anything new (a filled bucket) up the beam to a collecting one. Through an Area Prism it works on the area: with something in hand, on what's living there first; else on its blocks in turn, each from the side facing the prism (bone meal over a field, say). It does it over and over: behind a pulse mirror, once a signal. Use it in the air, or its button in a mount, to open its settings: use or hit, and what it works on. | {{c:LaserTuning.PLACE_FE}} FE a use |
| [[item:insert_lens]] | Action | [[mechanic:laser.lens.insert]] Puts what Laser Ports up the beam send into the inventory the beam ends on (it ramps up and down: each move halves its wait, from 4 s down to its own pace; each look that finds nothing doubles it, back up to 2 s), up to {{c:LaserTuning.MOVE_ITEMS}} items each {{c:LaserTuning.PLACE_COOLDOWN|s}} (each Speed lens doubles that, to 64, and halves the wait); with an Area Prism, each inventory in its area in turn. A Filter lens says what. | {{c:LaserTuning.PICKUP_FE_PER_STACK}} FE a stack |
| [[item:extract_lens]] | Action | [[mechanic:laser.lens.extract]] Takes from the inventory the beam ends on, up to {{c:LaserTuning.MOVE_ITEMS}} items each {{c:LaserTuning.PLACE_COOLDOWN|s}} (each Speed lens doubles that, to 64, and halves the wait), as much as the Laser Ports up the beam have room for, back up to them; with an Area Prism, from each inventory in its area in turn. A Filter lens says what. | {{c:LaserTuning.PICKUP_FE_PER_STACK}} FE a stack |
| [[item:precision_lens]] | Modifier | [[mechanic:laser.lens.precision]] Harvest cuts with Silk Touch: blocks come away whole. | none |
| [[item:acceleration_lens]] | Action | [[mechanic:laser.lens.accelerate]] Speeds up the machine the beam ends on, giving it extra ticks of its own: 2x, doubled by each [[item:speed_lens]] to 4x, 8x and 16x (the most is configurable). Through an Area Prism, every machine in the area. Each machine's extra ticks may take at most 100 µs a tick, and all of them together 5,000 µs (both configurable): one that would take longer is given fewer. Jade shows how fast it's going, and when the tick budget held it back ("Accelerated 5x of 8x (tick budget)"). Machines that don't tick, Quantimium's own machines, and blocks tagged `quantimium:not_accelerated` aren't sped up. | 500 FE for each extra tick (configurable), so 500 FE/t at 2x |
| [[item:void_lens]] | Modifier | [[mechanic:laser.lens.void]] Destroys what the beam's Laser Ports won't take (see above): like Pickup, it stays on the light, so actions added after it get it too. Use it in the air (or its button in a mount) to set it. **Void excess** (the default): with Pickup, what the Laser Ports or network up the beam have room for goes to them and the rest is destroyed, so Harvest never waits for room; with no ports, or without Pickup, everything they free is destroyed. **Void all**: everything they free or Pickup gathers is destroyed, ports or not (Harvest's drops, what Damage kills, items lying in the beam). Its screen has nine entries and allow or deny, as the Item Filter Lens's: listed, it destroys only those (or all but those), and keeps the rest as if it weren't there; seeds listed with void excess, a wheat farm keeps its wheat and burns the seeds it has no room for. | none |
| [[item:harmonic_lens]] | Modifier | [[mechanic:laser.lens.harmonic]] Harvest cuts with Fortune, and Damage hits with Looting, a level for each, up to {{c:LaserTuning.HARMONIC_LENSES}}. | none |
| [[item:unrealising_lens]] | Modifier | [[mechanic:laser.lens.unrealise]] Ore a Harvest beam cuts (anything in `#c:ores`) comes away as [[item:unrealised_matter]], one to a block, with no history yet. A pack can turn it off in the config, where Matter would duplicate ores. | none |
| [[item:smelting_lens]] | Modifier | [[mechanic:laser.lens.smelting]] Smelts, as a furnace would, what the actions before it free or move: what a Harvest beam cuts and what Damage kills (picked up or left lying), what Pickup gathers, what Insert puts in and Extract takes out. Sand becomes glass, ore ingots, a pig's drops cooked. Unrealized Matter gets smelting in its history, as a Quantum Crafter with a furnace gives it. What a furnace can't smelt stays as it is, for nothing. With no other action on the light it's an action of its own: it smelts what lies in the beam where it lies, else the block the beam ends on, every {{c:LaserTuning.PLACE_COOLDOWN|s}}. A block it smelts into takes its place (sand becomes glass, and the light goes on through to the next); anything else drops there. | {{c:LaserTuning.SMELT_FE_PER_ITEM}} FE for each item it smelts |
| [[item:crushing_lens]] | Modifier | [[mechanic:laser.lens.crushing]] Crushes what the actions before it free or move, everywhere Smelting smelts: stone to cobblestone, cobblestone and deepslate to gravel, gravel and sandstone to sand, and anything else as the pack's crushers would, their sure outputs only (raw ore to dust, say). Which crushers, and in what order, is `crushingRecipeTypes` in the `lasers` config: Energized Power's pulverizer and crusher, Ender IO's SAG Mill, Mekanism, Create, Thermal and others. Each Crushing or Smelting lens is a step, in the lenses' order (up to {{c:LaserTuning.PROCESS_STEPS}}): Crushing then Smelting makes raw ore dust, then ingots; Crushing, Crushing, Smelting makes cobblestone gravel, then sand, then glass. A step that changes nothing is free, and so is a run of steps that ends as it began (glass crushed to sand and smelted again). Where nothing says which came first (a crushing beam and a smelting beam meeting at a mirror) it crushes first. Where they change no action before them (alone, or before one: Crushing, Smelting, Harvest), they act on the world themselves, as one, in their order, and a Harvest after them waits till they've done: sandstone becomes glass, then it's cut. Unrealized Matter gets the first of those crushers that gives it something new in its history. Alone it crushes what lies in the beam, else the block the beam ends on, in place: stone becomes cobblestone, then gravel, then sand. With Phantom, it goes through what it can't change to the first block on its way it can (Smelting too). | {{c:LaserTuning.CRUSH_FE_PER_ITEM}} FE for each item it crushes |
| [[item:phantom_lens]] | Modifier | [[mechanic:laser.lens.phantom]] Light passes through blocks: only laser blocks, and blocks it charges, stop it. A Harvest beam with it cuts the first block on its way it's allowed to (with a Block Filter Lens set to ores, just the ore in the ground; with Pickup, it passes over what no Laser Port up the beam would take); an Area Prism with it reaches what it couldn't see. It stays on the light. | each tick it's lit, a configurable FE (20 by default, `phantomFePerBlock`) for each block it passes through; for an Area Prism, for each block between it and what it works on; and the actions it carries cost twice as much |
| [[item:speed_lens]] | Modifier | [[mechanic:laser.lens.speed]] Its actions work twice as fast: a Harvest beam waits half as long between blocks, Placement too, and an Area Prism looks through its area twice as fast. Three count, for eight times. | none |
| [[item:filter_lens]] | Modifier | [[mechanic:laser.lens.filter]] The Item Filter Lens: the actions it changes carry, place, put in or take out only the items it lets through. Nine entries, allow or deny, with tags (shift-click an entry). Insert, Extract, Pickup and Placement read it. Use it in the air to set it. | none |
| [[item:block_filter_lens]] | Modifier | The Block Filter Lens: a Harvest beam cuts only the blocks it lets through, by the block or anything it drops, so "wheat" lets only ripe wheat be cut. Nine entries, allow or deny, and **ripe only**: only crops (and anything else that grows) that are fully grown, never what doesn't grow, so never the farmland. | none |
| [[item:entity_filter_lens]] | Modifier | The Entity Filter Lens: a Damage, Healing, Freezing or Interact beam works only on the living things it lets through. Nine entries take spawn eggs, allow or deny, for kinds of mob. Sneak-use the lens on a creature or player to list **just that one** (shown as their head or spawn egg, named), or sneak-use it in the air to list yourself: an Area Prism with Healing and a lens listing you heals only you. Click an entry out of its slot to take it off the list. light up hostile, animals or players (none lit, any), choose to **spare the young**, and a **spare** count (shift for tens): it hits only those beyond that many, so a pen keeps its breeding pair. | none |
| [[item:amplify_lens]] | Modifier | [[mechanic:laser.lens.amplify]] Harvest cuts as a diamond pickaxe, so obsidian and the like; each one doubles a Damage hit, up to {{c:LaserTuning.AMPLIFY_LENSES}}. | a cut costs one more share for each; a hit doubles |
| [[item:uv_lens]] | Modifier | Hides the light: only someone holding a wrench or the Linker sees it. It stays on the light. | none |
| [[item:place_lens]] | Action | [[mechanic:laser.lens.place]] Places a block a Laser Port up the beam sends, in the space where the beam ends, as you would against the block it ends on; one every {{c:LaserTuning.PLACE_COOLDOWN|s}} at most. Seeds count: on farmland they're sown. A bucket of water or lava empties there, and the empty bucket goes back to the port. A Filter lens in its mount says what it places. | {{c:LaserTuning.PLACE_FE}} FE a block, when it's placed |

[[structure:laser_farm]]

**A wheat field.** Point a beam down at farmland through a Laser Port set to both, holding seeds, and a mount
with Harvest, Pickup, Placement and a Block Filter Lens set to ripe only. Only ripe wheat is cut, it comes
back up to the port, and the spot is sown again. Spread it over a whole field with an Area Prism.
A cut waits until the ports up the beam would take all of its drops, seeds included, so give the
seeds somewhere to go. Above, a collecting port catches the harvest and a sending port below it holds
the seed.

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

[[mechanic:laser.port.collect]] A [[block:laser_port]] passes light, in straight by its back and out of its face, as a relay does,
and holds nine slots. It starts out **collecting**: it catches what a Pickup lens further along brings
back, for pipes and hoppers to take from, or take from it yourself. Put it between the emitter and the
mount: emitter, Laser Port, Lens Mount with Harvest and Pickup, then the blocks to break. What one port
can't take goes on up the beam to the next. A stream from one side of a join goes up its own side, or to
ports after the join. [[mechanic:laser.port.filter]] Open it to set a filter on what it catches, nine
entries, as a whitelist or a blacklist, with tags: what it turns away goes on up the beam.

[[mechanic:laser.port.send]] Set to **send**, it works the other way round: what's in its slots goes down
the beam, to a Placement, Insert or Interact lens further along, or once a second a stack at a time to the
nearest collecting port down the beam with room, not past an Isolator. The light reaching it pays to
carry it, and only while it's lit: a switched-off relay before it, or on the way, stops it. A bound
Tesseract in a slot sends from the inventory it links to.

[[mechanic:laser.port.mode]] Its mode tab steps through **collect**, **send** and **both**. In both, what
comes back up the beam can go straight back down: a port of seeds on a wheat field catches the harvest
and sows the field again from it.

Use the **Sides** tab to set, face by face, what pipes and hoppers may do there (hand things out, take
them in; things only go in while it sends), and which faces push to, or pull from, what's beside them,
once a second.

[[mechanic:laser.area_prism]] An [[block:area_prism]] spreads the actions of light reaching it over an
area of any size: Harvest cuts each block there in turn, Damage hurts and Pickup gathers whatever's in it,
Placement fills each free space resting on a block. It works only on what the light reaching it
reaches (8 blocks for light with an action, more with Range lenses) and can see, along a clear line from
the prism, and while it's working it shows a beam into what it's working on, following what it's hitting as it moves. Work in an area costs
{{c:LaserTuning.AREA_COST}} times as much. Use the [[item:laser_linker]] in the air to switch it to area mode, use it on two
corners, then on the prism; the area shows as you pick it, and holding the Linker shows the area of any
prism you look at. Sneak-click the prism with an empty hand to clear its area. [[mechanic:laser.area_prism.shape]] Or open
the prism and set its area to a **cube** or a **sphere** around it instead, with a radius. Its actions
work the area **nearest first** (the prism's own height outwards, nearest columns first in each layer),
**top down** (a quarry: nothing falls into what's cleared) or **bottom up**, as set in its screen; after a
reload they carry on where they were.

Work that happens at once (a hit, an item carried, a block placed) is paid for first each tick, so
machines on the same light can't starve it; blocks are charged from what's left.

[[mechanic:laser.spectroscope]] The [[item:spectroscope]] splits light into what it carries, as a real
spectroscope's prism does. Use it on any laser block to read the light leaving it: how far a run
reaches and from how many sources, each action with its modifiers, who pays and how many Laser Ports it
can reach, and each beam's length, end and power.

[[mechanic:laser.blind]] **Don't look into the beam.** Light carrying power blinds anything whose eyes
are in it, for {{c:LaserTuning.BLIND_TICKS|s}}. The [[item:mirror_lens]] protects the wearer.

