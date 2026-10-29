# Changelog

## 0.8.0-alpha: Fluids and channels

### Fluids

- **Laser Tank**: the Laser Port for fluids. Four tanks of 16 buckets, one fluid each; collects, sends or both, and
  takes light only by its back. A bucket fills or empties it; pipes drain it on any face and fill it while it sends.
  Once a second a sending tank pours up to four buckets to the next collecting tank down the laser.
- **Insert and Extract move fluids**: with no items to move, they fill or drain the fluid handler the laser ends on,
  from or to the Laser Tanks up the laser, a bucket at a time (doubled for each Speed Lens).
- **Extract pumps**: with nothing to take from, it takes the first fluid source on its way (the laser runs on through
  water) up to the collecting Laser Tanks, a bucket at a time.
- **The Item Filter Lens is now the Filter Lens** and lists fluids: right-click an entry with a bucket to list its fluid
  (drawn as that fluid), or drag a fluid in from JEI; a plain click lists the bucket itself. Fluid entries apply only
  to fluids, so a list of items lets every fluid through. The **Void Lens** destroys fluids the same way.
- The Laser Tank has a filter and a Sides tab like the Laser Port's (faces push to and pull from what's beside them),
  its tanks fill and empty with a bucket clicked on them, and screens draw fluids with their own texture.
- **Interact pours from Laser Tanks**: when nothing a Laser Port sends is used, it uses a bucket of a sending tank's
  fluid (filling a cauldron, or pouring it out), draining the tank only if the bucket was emptied.
- **Fluids on logistics networks**: a Logistics Receptacle on any tank puts it on the network as storage, a provider or
  a requester, moving fluids as it moves items (2,000 mB a second, quickened by Speed Lenses). Its filter lists
  fluids too, and keeping stock counts them in buckets. A collecting Laser Tank is a passive provider.
- **The terminal lists fluids** with their amounts, sorted as a bucket to an item: click one for a bucket of it (the
  bucket taken from the network, or made), click it holding a container to fill that, or right-click the grid with a
  filled one to pour it into storage. An Extract Lens past the controller pours into the network; Insert and Interact
  take its fluids only through a Filter Lens listing a fluid.
- **Recipes with fluids are read**: the fluids a mod's recipe takes and gives are found by their fields' types (or
  named in an adapter's `fluid_input_fields` and `fluid_output_fields`).
- **The Quantum Crafter has tanks**: three in, beside the catalyst, and two out, shown among its outputs as a terminal
  shows fluids. A recipe taking a fluid draws it from them, or from a tank a Tesseract in its grid is bound to; fluids
  a recipe makes fill its output tanks, and a bucket clicked on one fills from it. Recipes making only fluids are
  offered there too. Pipes fill the inputs and drain the outputs. A recipe taking a fluid is no longer offered as if
  it took items alone.
- New Crafter catalysts: Energized Power's Fluid Transposer, Fluid Freezer, Stone Solidifier and Stone Liquefier, and
  Productive Bees' Bottler. A bucket used on the Crafter fills or empties its tanks.
- **The Logistics Materializer offers the network's fluids** through a fluid handler, so a Crafter's Tesseract bound to
  it makes from the network's fluids. Fluids are no longer shown to pipes as items.
- **The Reactor holds fluids**, a bucket weighing as an item: in through an Input port, out through the Materializer
  Port (made if need be), and to a logistics network through its Logistics Port, drawing on the network's fluids too.
  Its recipes take and make fluids, and buckets fill and empty with no catalyst.
- **AE2**: the ME Superposition Crafter offers recipes with fluids, taking and giving AE2 fluids. The ME Superposition
  Port shows the Reactor's fluids as AE2 fluids, takes them in and out, and lets the Reactor make from the network's
  fluids; its patterns ask for and give fluids as fluids.
- Fluid gauges are marked like a measuring jug.
- **The Crafter's faces set fluids apart from items** in its Sides tab, with auto input and output. A face gives
  what its output tanks hold and what a recipe making only fluids shows, made as a pipe, bus or auto output takes it.
- **A Crafter's fluids on a logistics network**: what its output tanks hold is stock, and what a recipe making only
  fluids shows is craftable, made as a terminal or requester takes it.
- **Make to storage**: middle-click something that can be made in a Logistics Terminal, pick how many (up to what can
  be made), and it's made and kept in storage, items or fluids, if storage has room.
- The terminal counts fluids that can be made, as it counts items.
- **The Reactor's fluids it could make are on its network**: listed as craftable, and made as a terminal or a requester
  that requests what can be made takes them.
- A Reactor making something uses a recipe whose ingredients it has on hand first: lava from the cobblestone held, not
  from andesite it would first have to make.
- The Horizon Core shows fluids with their texture, in mB and buckets, sorted as a bucket to an item.
- A Reactor no longer counts empty buckets as water it could make: filling and emptying buckets made it show far more
  of every fluid than it had.

### Lasers

- **Channels**: 0 to 15, and All. Each lens in a Lens Mount has a channel (the number above it): an action works on its
  channel, and a modifier changes only the actions on its own (on All, every one). Laser Ports, Tanks, Receptacles and
  Logistics Controllers have one too, so several networks share a laser path. An Isolator stops only its channel.
  Everything starts on 0, so nothing changes until a channel is set. A Logistics Controller starts on All: a network
  for each channel its receptacles are on. A Terminal shows its channel's network; a Materializer offers every channel's.
- **The Logistics Controller has a screen**: its receptacles, its power, and its channel.
- **One-way Laser Splitters**: sneak with a wrench, or sneak-click the base with an empty hand, to make a splitter a
  one-way mirror (its plate's back steel): light on the front only turns, light from behind only goes through, so two
  lasers combine without spilling.
- **The Spectroscope has a screen**: the laser split into its spectrum, a line for each action (shaped by its
  modifiers) and a band for logistics, a channel picker, and a table of its actions and the ports they use. Aim it at a
  laser in the air to read it.

### Quantum Crafter

- **Pins**: middle-click an output, or drag one from JEI onto the grid, to pin it. Pinned outputs come first, outlined
  in gold, and stay shown, with a 0, when none can be made. **Locked**, the Crafter makes only what's pinned (the rest
  shown dimmed), and auto output makes each in turn. A Crafter locked to a recipe before keeps it, pinned.
- Four output tanks for what recipes make besides items; when they're too full for anything to be made, it says
  *Output blocked*.

## 0.7.0-alpha: The Laser Focus, the Redstone Lens and true mirrors

### Lasers

- **Redstone Lens** (action): gives what the beam ends on a redstone signal of 1 to 15, strong or weak. Set it to give it always, to
  send on the signal its own block gets, or as a level emitter: while the Laser Ports up the light hold fewer than a
  count of an item, or that many or more (on a logistics beam, counted in its network). A Laser Focus it reaches is switched as by a wired signal, and the light it
  then passes on carries the signal further.
- **Isolator Lens**: two new switches. It can stop power (nothing past it is charged, though what the light does
  is still paid for), and pass a Redstone Lens's signal, which it stops by default.
- A Clear Laser Focus no longer passes light straight out through an Emitter on its far face.
- A cut or other work paid for by several sources through a Focus Emitter no longer stalls just short of done (stuck
  at 99%).
- A sneaking click with an empty hand takes an Emitter's lens out before the Emitter. In creative, what comes off a
  Laser Focus goes into your inventory.
- Isolator Lens: the redstone switch is a redstone torch, lit while signals pass, and its filter is labelled.
- Isolator Lens: Pickup's items go back up through it only if it passes logistics, and one in a Focus Emitter now stops
  them too (it didn't).
- A Laser Port takes light only straight in by its back, and a Lens Mount no longer takes light at an angle.
- A Laser Focus block with no focus stops light at any attachment on the face it comes in or goes out by.
- Beams notice a block placed or broken on them the next tick, instead of within half a second, and cost far less to keep
  watch over: only a change light can't pass, or one where a beam ends, is looked at.
- Light lights up, at its full brightness, and goes dark as soon as it should: from an Emitter put down or switched
  on, and through each Laser Focus it reaches or stops reaching, not up to a second later.
- A Laser Focus that light meets on a Stand, Cover or Emitter shows no light leaving it.
- **The Laser Focus replaces the Laser Relay.** Put a Laser Focus down, then put attachments on its faces: Focus
  Stands (as many as you like, or none and it floats), Focus Emitters (each with its lens and redstone setting) and
  Focus Covers. A **Clear Laser Focus** also lets light pass straight through. Attachments can go on a block with no
  focus and do nothing until one goes in; a sneaking click (by hand or with a wrench) takes off the focus or an attachment and leaves the rest.
  The focus loses nothing: each Emitter takes the loss, 0.5% by default (`relayLoss`), and mirrors 0.25%
  (`mirrorLoss`). Relays in your world become a Laser Focus with a Stand and their Emitters, lenses and redstone
  settings kept.
- **Logistics Receptacles are Laser Focus attachments**: several on one focus, each serving its own inventory. On a
  Clear Laser Focus light passes through as before. Receptacle blocks become a Clear Laser Focus with a Receptacle,
  settings kept.
- **Cavity Mirrors are Laser Focus attachments**: several on one focus, each with its own cavity. What they hold
  leaves by the focus's Emitters. Cavity Mirror blocks become a Laser Focus with a Stand, the plate and their
  Emitters, holding what they held. An unpaired one no longer acts as a mirror, and an
  Emitter facing back no longer closes a cavity: only a mirror does.
- Straight light no longer gets into a Laser Focus by a face with an Emitter (diagonal light still does).
- **A Prism links to the face you click**: its light runs at any angle to the middle of that face and goes in by it,
  so you choose which side of a machine it charges, or a Laser Port's or Lens Mount's back. From a Prism that can't see
  that face, the light meets the side it comes to first, as any light would. A Laser Focus, Mirror or Prism is still lit at its middle (its focus). Links made before take the
  face facing the prism.
- **Mirrors reflect like real ones.** A beam bounces off a Laser Mirror and goes on as the same beam, with its own
  light: beams crossing one mirror never mix. A plate faces one of 42 ways: flat to a face, or turned 22.5° or 45°
  towards a neighbouring one. Click a face's middle for flat, the ring round it for 22.5°, the outer ring for 45°
  towards the side clicked; sneak-click to move the stand. Looking at a face outlines its parts, and a ghost shows where
  the plate would turn. A plate never faces towards its stand. Light now travels diagonally too (26 ways in all); what leaves a mirror
  is always one of them. Mirrors already placed keep the way they faced. Each bounce takes 0.25%.
- **The Laser Splitter** reflects off both sides and lets light through: one beam becomes two, each its own. It no
  longer joins beams into one. It can face straight on too. Light never leaves a mirror or splitter through its stand.
- **Cavities round corners.** A cavity's light can run round any number of plain Laser Mirrors, back off a flat one
  or into another Cavity Mirror: up to 16 blocks one way, each mirror counting as one. A splitter, or a mirror
  switched off, breaks it.
- Mirrors no longer show where light would go while holding a wrench or the Linker.
- An Interact Lens with nothing to do waits longer between tries, as Insert and Extract do (up to `laserIdleSeconds`),
  instead of trying everything it's sent every tick. Busy, it keeps its pace.

### Reactor

- A storage bus reading a busy Reactor's Materialiser Port costs far less: an answer stands for a second while what it
  uses is still there, however else the Reactor changes, and planning is held to about 1 ms a tick (a bus listing 1,450
  slots every tick went from about 240 ms to under 2 ms). Things never asked about before can take a few seconds to show.
- Plans look items up faster, and the ME Superposition Port plans its trees without copying the whole stock for each,
  in at most about 1 ms a tick.

## 0.6.1-alpha: Healing, freezing, and a lens pass

Same Minecraft 26.1.2 and NeoForge 26.1.2.109. Worlds from 0.6.0 carry on.

### Lasers

- **Healing Lens**: heals anything living in the beam by a heart every half second, and hurts the undead instead, as
  Instant Health does. Amplify doubles both, and the cost (500 FE for each one healed or hurt); a Speed lens heals
  twice as often, two four times. What's at full health costs nothing; an Entity Filter picks who it works on.
- **Freezing Lens**: freezes what the beam ends on. Machines stop ticking while the light holds them (tag blocks
  `quantimium:not_freezable` to keep it off them); water turns to ice and snow now and then settles on top. Mobs stop
  thinking for 10 seconds, frozen again while the light stays on them; each point of damage thaws them half a second
  sooner. Freezing one costs 25 FE for each point of its most health (a zombie 500, a Warden 12,500), charged up as
  Damage's hits are. The Ender Dragon is never frozen (entity tag `quantimium:not_freezable`); the Wither and the
  Warden can be. Players get colder a tick at a time, as in powder snow: slower the colder, never hurt. Snow
  settles slowly. Through an Area Prism, the whole area.
- **Fixed:** an emitter set to run with a redstone signal lit its beam when given one, but the lenses down it
  (Acceleration, Freezing and the rest) didn't start working.
- **Fixed:** Harvest with Pickup could cut a crop when there was room for some of its seeds but not all, dropping the
  rest: it now drops exactly what it found room for.
- **The Void Lens has a list**: its screen is the Item Filter Lens's (nine entries, allow or deny) with a tab for void
  all or excess. Listed, it destroys only those (or all but those): seeds listed with void excess, a wheat farm keeps
  every wheat and destroys the seeds it has no room for.
- **Light goes through water** (and waterlogged blocks it would go through dry); lava still stops it. A Freezing beam
  freezes the first water on its way; an Interact beam's bucket fills from the first source on its way.
- **Fixed:** a Quantum Crafter reading a chest through a Tesseract kept showing what it could make before the chest
  was emptied, refilled or broken, till its Tesseract or catalyst was replaced: it remembered its answer for its own
  grid without counting what the Tesseract reaches.
- **Fixed:** Harvest with Pickup that found no room once never asked again (after a world load, a farm full at the
  time stayed idle till the network was rebuilt). Acceleration and Freezing through an Area Prism never found the
  machines in their area; both work now.
- **The Void Lens stays on the light**, as Pickup does: actions added after it (in a later mount, or later in its own)
  get it too. It covers every way items travel along the beam: Insert with void excess puts in what fits and destroys
  the rest, with void all it's a trash can; Extract with a listed Void takes what's listed and destroys what no Port
  holds; what Interact hands back that no Port takes is destroyed. The guide now explains Laser Ports as the beam's
  item ends, and Pickup and Void as rules for that traffic.
- **The Spectroscope says what a Harvest beam is doing**: cutting (and how far), nothing to cut, or no room for what
  it would cut (and what).
- **The Entity Filter Lens can list one creature, or you**: sneak-use it on a creature or player to list just that one
  (their head or spawn egg, with their name), or in the air to list yourself. With Healing through an Area Prism,
  it heals only you; set to deny, it spares only you. Clicked out of its slot, it's off the list.
- **Fixed:** Harvest with Pickup could cut a crop whose wheat and seeds each fit a Laser Port's last empty slot but
  not both, and lose the seeds. A drop's things are now checked together.
- **Harvest with Pickup rests when full**: half a second, then twice as long each time it's still full, up to 4
  seconds (as Insert and Extract do), and back to work at once when it can cut again.
- **Harvest sets what it cuts down where the block was**, still, rather than throwing it about: a Pickup sweeping the
  beam always reaches it.
- **Lighter lens scanning.** An Area Prism remembers for a second which blocks it couldn't see, instead of tracing to
  them again each pass (a tree farm's hidden logs, a quarry's far side); Harvest with Pickup rolls a block's drops
  once to check for room, and remembers each answer for half a second, asking afresh only the tick it breaks a
  block. An idle area scan costs about an eighth of what it did. A Materialiser reading through a Tesseract looks
  once a second, not every tick (a linked network is a whole network to look through).
- **`/quantimium lasers profile [seconds]`** (operators): times this dimension's lasers for a few seconds, then lists
  what they spent each tick on (checking beams, lens actions, logistics, moving power) and the ten costliest lens
  actions with where they are, to find the one slowing a base.

## 0.6.0-alpha: Warm fields and new lenses

Same Minecraft 26.1.2 and NeoForge 26.1.2.109. Worlds from 0.5.x carry on. A Materialiser holding Trace needs
Anomalite Dust instead (the Trace stays in its slot until taken out).

### The field

- **Flux keeps a warmth.** Each chunk has a slow memory of its field that warms over about an in-game day of work and
  cools over about two once it stops; the chunk reads as the higher of the two, and anomaly follows. A base crafting
  here and there warms up and stays warm; switched off, it holds its level for a day before fading; one huge batch
  barely warms it. Configurable (`warmthRiseMinutes`, `warmthFallMinutes`).
- **Machines no longer leak FE in anomaly.** The passive drain is gone (and its `passiveDrain…` config): what feeds on
  a hot base's machines is the Anomalite crystals that grow on them.

### Unrealised Matter

- **The Materialiser is paid in Anomalite Dust**, not Trace: renewable from a crystal farm (crush Anomalite with a
  Crushing Lens: a shard crushes into one dust). A common ore at Medium costs 0.75 dust a Matter, as Trace did.
- **Its screen works like a Logistics Terminal**: a grid of every form, each showing what waits in its buffer and
  how many more it could make; click for a stack, right-click for half, shift-right-click for one, shift and the
  wheel for one at a time, shift-click into the inventory. Its output slots are gone: what a Matter makes over a
  take waits in a buffer, taken first. Middle-click a form to export it (gold): what pipes and hoppers take.
- **A Materialiser behind a provider Logistics Receptacle** offers every form to the network as craftable, made
  when taken.

### Lasers

- **Acceleration Lens**: speeds up the machine the beam ends on (every machine in an Area Prism's area): 2x, doubled by
  each Speed lens up to 16x, 500 FE for each extra tick. Each machine's extra ticks keep to 100 µs a tick and all of them
  to 5,000 µs; Jade says how fast a machine is going and when the budget held it back. All configurable, with a
  `quantimium:not_accelerated` block tag.
- **Void Lens**: destroys what the actions before it free. Void excess (with Pickup, only what the Laser Ports or
  network have no room for) or void all (everything freed or picked up), set on its settings screen.
- **Interact Lens**: set to hit, it mines the block the beam ends on with its tool (wearing it) when nothing living is
  there. A second setting picks blocks, living things or both; both are set on a settings screen (use it in the air,
  or its button in a mount). A tool bound to an Entangled Dock is charged from it as the lens uses it.
- **Laser Ports export faster while busy**, and pass what they collect straight into the inventories they export to.

### Partner mods

- **FTB Industrial Contraptions**: its Macerator, Compressor, Extruder, Roller, Canning Machine, Centrifuge, Alloy
  Smelter, Reprocessor and furnaces (and their Advanced versions) are catalysts for the Quantum Crafter, the ME
  Superposition Crafter and the Reactor.

### Fixes

- Craft placeholders in the terminal's grid no longer vanish from the screen while still being used.
- Set Filter and Isolator lenses stack, and the terminal can take them.
- An Isolator Lens not set to pass actions stops Laser Ports linking past it.
- A source sending all its power down one beam still shows its other beams dimly.
- Pickup before an action (or alone) gathers everything lying in the beam; after one, it carries what that action
  frees. Actions after a Pickup no longer get it.
- The terminal shows an item's full tooltip.
- The stash and logistics keys say nothing to a player with no doubles.
- Unrealised Matter processed on a logistics network (pulverised, say) is offered to the network's other Crafters
  (smelted by a furnace Crafter), as it is when it's in a Crafter's own grid.
- Side configuration names a neighbour as picking it would: Sophisticated Storage's chests no longer show as
  `%s%sGold Chest`.
- Drops other mods hand out in the mirror (Ultimine, Apotheosis) can be picked up there.

## 0.5.7-alpha: Crafting at the terminal

Same Minecraft 26.1.2 and NeoForge 26.1.2.109. Worlds from 0.5.x carry on; a Crafter's Receptacle set to craft
"from anything" now crafts from stored items.

### Logistics

- **Crafters on a network make things from what's really stored, one step.** Chains of crafts through the network
  (planks from logs for the sticks for a pickaxe) are gone: in big packs they listed things that couldn't be made,
  were refused until clicked again, and chose odd ways round. Chains are a Reactor's to plan. The provider tab on a
  Crafter's Receptacle now just turns offering its crafts on or off.
- **Craftable counts are real**: Crafters making the same thing from the network's items count once, not once each
  (a row of crafting tables showed each other's planks many times over).
- A simulated take from a Crafter (another mod checking what it could get) no longer makes the craft, and no longer
  stops the real take that follows.
- The terminal takes what's left over from a craft before crafting more.
- **The Logistics Terminal has a crafting grid**, shown by a tab: under a shorter item grid, kept in the terminal, and
  refilled from the network after each craft. JEI's + fills it from the network or your inventory; what only a
  machine can make goes in as "Craft" and is made when you take the result (or drag it there from the item grid).
  Missing items show red, what will be made blue. An item that runs out becomes a "Craft" if it can be made.
- **Crafters linked by hand still chain**: a furnace Crafter linked straight to a pulveriser Crafter smelts its
  dust, behind a provider too.
- The terminal remembers its crafting grid, sort and view between games (config/quantimium-client.toml).
- **Searching the terminal works as JEI's search does**: `@mod`, `#tooltip`, `$tag`, `-` to leave out, `|` for either.
- The terminal is a row taller (six rows of items).
- `/quantimium trace <item> [count]` (operators): a take at the nearest Logistics Terminal, written down step by step
  to `logs/`, for working out why something isn't made.

### Performance

- Working out what a network offers is many times cheaper without chains: about 30 ms instead of a second for
  18 Crafters over 1,200 kinds of item.

## 0.5.6-alpha: Crafts that come first time

Same Minecraft 26.1.2 and NeoForge 26.1.2.109. Worlds from 0.5.x carry on.

### Fixes

- **Big networks no longer lag the server working out their offers**: 0.5.5 shipped an experiment by mistake that,
  on a network of thousands of kinds, compared every item against every other.
- **Crafts refused for no reason, then given on the third click**: a craft short of something now makes what it
  can and tries again at once when its sub-crafts made progress; what's left over from a craft is found where it was
  put; whether a craft can be made is asked of one run, not the whole batch.
- A Crafter set to make from stored items only shows only what it can make from them.
- A take refused though listed (the list was behind) makes the network work its offers out again at once.

### Performance

- Working out what a big network offers takes about half the work: each craftable keeps only what its own craft
  takes, and what's made of what is looked up once per working out.

### Logistics Receptacles

- The power tab reads "Share network power: on/off".

## 0.5.5-alpha: Lighter rebuilds

Same Minecraft 26.1.2 and NeoForge 26.1.2.109. Worlds from 0.5.x carry on.

- **A busy network works its offers out at about half the cost**, and so keeps its lists fresher in the same budget:
  - stock moving in things nothing on the network is made from (a quarry's stone, with Crafters using iron)
    no longer sets it off; something new, or after five seconds any change, still does;
  - a Crafter matches only the recipes naming something new (or more of something it had too little of for a
    recipe), and resizes the crafts it already knows; every recipe is matched only at first, and once a minute;
  - what counts as "too little" comes from the catalyst's recipes (nine for a crafting table).
- A Crafter's own preview is worked out the same way, rather than matching every recipe each time, and leaves out
  loops from the start: opening a Crafter no longer flashes, for a moment, a craft out of what another machine
  would make of its own output (iron from the dust a pulveriser makes of iron).
- Crafters a network feeds look in on it at most once a second between them, not every few ticks each.
- A craft is left out as a loop only if everything it would use is made of what it makes: iron dust from ingots is
  offered when the ingots come from blocks as well as from smelted dust.

### Logistics Receptacles

- **Power the machine it's on**: a tab on a Receptacle's screen, shown when the machine takes power. The light reaching
  the Receptacle (an emitter's, through the controller) charges that machine too.
- A Receptacle is lit only while its network's controller has the power to move things.
- A Reactor on a network: its port's offers catch up on what changed instead of being worked out again after every
  take (a terminal take from a Reactor holding thousands of kinds: about a third of the time).

## 0.5.4-alpha: Stress tested

Same Minecraft 26.1.2 and NeoForge 26.1.2.109. Worlds from 0.5.x carry on.

### Crafting on a network

- **What the terminal lists, a take makes, first time**: a network hands its Crafters the crafts it worked out
  for them, so a Crafter's list never lags behind the terminal's.
- Crafts no longer chase each other round a loop (nuggets, ingots, blocks) across Crafters: a pull that did could
  freeze a server for minutes.
- A craft that comes up short isn't tried again by the next click that tick.

### Performance

- A screen left open no longer keeps its network working flat out; only a move a player makes, or a screen just
  opened, is urgent, and the urgent budget is 4 ms (`performance.urgentWorkBudgetMicros`; an existing config keeps
  its value, so change it there).
- A busy network works its offers out at about a third of the cost: Crafters resize the crafts they found rather
  than matching every recipe again (new things, and every five seconds, they match them all).
- Requesters pulling and providers pushing no longer ask every Crafter preview whether it could be made.
- Takes and Crafters drawing on a network no longer look over all of it for each ingredient.
- A Reactor holding thousands of kinds plans without copying its ledger: pulls through its ports are several times
  quicker.

### AE2

- A storage bus on a Logistics Materialiser reads the network's offers directly. As it came, AE2 checked each listed
  thing by taking it and putting it back, which for what's made meant making it, every time it looked.

## 0.5.3-alpha: Quick takes

Same Minecraft 26.1.2 and NeoForge 26.1.2.109. Worlds from 0.5.x carry on.

- **Taking from a Logistics Terminal is quick again**: it looks only where the item is, instead of asking every
  Crafter on the network whether it could make each thing it shows. Lenses taking stored items skip Crafter
  previews too.

## 0.5.2-alpha: Smooth on a busy server

Same Minecraft 26.1.2 and NeoForge 26.1.2.109. Worlds from 0.5.x carry on.

### Background work

- **A work queue**: what a logistics network offers and what Crafters could make is worked out a little each
  tick, a millisecond at a time, within a budget (config `performance.workBudgetMicros`, 2 ms). Work a player
  is waiting on (a terminal or Crafter screen open, something just taken or put in) goes first and gets more
  (`performance.urgentWorkBudgetMicros`, 10 ms). A crafting table on a big network no longer stalls the server.
- **What's stored shows at once**; what can be made from it catches up within a few ticks. Taking or crafting
  never waits on the queue.
- Mirror flora with no one in the mirror nearby grows every half minute, in the background.

### Crafting

- Chains up to eight steps deep are listed (AE2 storage components from raw stuff, through an Inscriber).
- A Crafter shows everything it can make (previews aren't slots), as its own chain would make it, and leaves
  loops out as the network does.
- A Crafter's grid isn't its network's stock, and a craft takes from its own grid before any Tesseract.

### Screens

- JEI's R and U work over the Logistics Terminal's and Crafter's grids; a click away lets go of a search box;
  slim scroll bars beside both grids.
- Crafter cells show what's there (top right) and, in violet, what can be made besides; a click takes a stack.

### Speed

- Recipe matching and sizing look only at the offers holding what a recipe names; AE2 storage buses on
  Logistics Materialisers and Reactor ports stay quick; a network's offers cost far less to keep up.

## 0.5.1-alpha: Crafting through the network

Same Minecraft 26.1.2 and NeoForge 26.1.2.109. Worlds from 0.5.0 carry on.

### Crafting through logistics

- **Crafters chain through a logistics network**: a Tesseract bound to a Logistics Materialiser draws on the
  network, a provider Receptacle on the Crafter offers what it can make. Chains run up to eight steps across
  any number of Crafters (AE2 storage components from raw stuff, through an Inscriber, are six), and one
  Crafter makes several steps itself (a furnace smelting stone for the bricks it then cracks). Every take is
  exact: called off, it puts everything back.
- **Terminals list what's stored and what can be made apart**, worked out once a second in a fixed order, so
  it reads the same whatever's opened first. Counts are what a Crafter **can make**, past one stack, and a
  loop (ingots to blocks and back) isn't counted twice.
- **Stored first**: a take gives what's stored before anything is made. A Crafter's provider can craft from
  anything, from stored items only, or not at all; an active requester can have things made for it or not.
- **Logistics Materialiser modes**: stored, craftable, or both. A provider Receptacle on another network's
  Materialiser brings that network's offers into its own.

### Quantum Crafter

- **A terminal-style screen**: one searchable, scrolling grid of what's in its outputs and everything it can
  make, sorted by count or name. A cell shows what's there and, in violet, what can be made besides. Click for
  a stack, right-click half, shift-right-click or shift-scroll one; the grid holds still while shift is held.
- **Previews aren't slots any more**: it shows every craft it can make, not 54; its output slots hold only
  what it has made. A Crafter reading a network or drawer counts every stack, not one.

### Lasers

- **Entangled Mirror**: put in a Tesseract bound to a laser block (a Relay, say) and the light reaching that
  block comes out of the mirror, any distance away in the same dimension, less 5%. Made in pairs in the Foundry.
- The **Laser Splitter** and **Cavity Mirror** have a see-through focus in their plate; the Splitter's column
  stays out of the beam.
- **Area Prism** scan orders (nearest first, top down, bottom up), and it carries on where it was after a reload.

### Fixes and speed

- AE2 storage buses on a Logistics Materialiser or a Reactor Materialiser Port no longer slow the server: a
  sweep of 400 items went from ~80 ms to ~2 ms. A network's offers cost far less to keep up.
- Solar chains at their limit no longer lose most of their power; crystals feed on fill-only machines;
  Receptacles take light on their outer face; crying obsidian from the Foundry; the Entangled Dock no longer
  needs the Foundry; screens beside a Logistics Materialiser work.

## 0.5.0-alpha: Quantimium Lasers

**Power, work and items by light**, and a new look for every screen. Same Minecraft 26.1.2 and
NeoForge 26.1.2.109. Worlds from 0.4.x carry on.

### Light

- **Laser Emitters** (Flash, I, II, III) and the **Solar Emitter** turn FE into light, and anything
  that takes FE takes it back where a beam ends. Beams join, split and bend through **Laser Relays**,
  **Mirrors**, **Splitters** and **Prisms**, linked with the **Laser Linker**; **Cavity Mirrors** store
  light between them, and **Photodetectors** read it.
- **Lens Mounts** and **Relay emitters** hold lenses that give light something to do: **Harvest**,
  **Damage**, **Pickup**, **Place**, **Insert**, **Extract** and **Interact**, changed by **Range**,
  **Speed**, **Amplify**, **Precision**, **Harmonic**, **Unrealising**, **Smelting**, **Crushing**,
  **Phantom** and **UV**. Lens order matters, and **Laser Ports** catch what comes back and send
  what goes out.
- **Filter lenses** come in three: **Item** (what's carried, placed or moved), **Block** (what Harvest
  cuts, ripe only if you like) and **Entity** (what's hit, by kind or spawn egg, sparing the young or a
  number of them). Each sets only its own part, so they combine on one beam.
- **Isolators** let only power through. The **Isolator Lens** does the same in a mount and can be set
  to let a logistics network, actions, modifiers or chosen lenses pass.
- **The Area Prism** works a whole area: picked with the Linker, or a cube or sphere round it.
- **Rifts** take light into the mirror, where Harvest cuts grown Anomalite crystals and Pickup draws
  Veil Thread.

### Logistics

- **A Logistics Controller's light is a network.** **Logistics Receptacles** on any inventory make it
  storage, a provider or a requester, active or passive, with a priority and a filter; a requester can
  **keep counts stocked**. Active receptacles move at a lens's pace, quicker with Speed lenses.
- **Logistics Terminal** and **Logistics Lectern**: everything the network holds, to take or put
  away, as an ME terminal; the **Pod Logistics Module** opens it from anywhere (V).
- **The Reactor joins in.** The **Reactor Logistics Port** makes the Reactor network storage, sharing
  what it holds and can make; the **Logistics Materialiser** offers the network to pipes.

### The Reactor

- **Catalyst Bays sink into the floor**, four catalysts each with a filter, and the **Lit Reactor
  Plinth** shows the core working.
- **The ME Superposition Port** puts the Reactor on an ME network: what it holds as storage, what it
  can make as patterns (whole trees, steps or both), with a priority and filters, set up like a
  storage bus.
- **Safer and quicker**: no duplication or loss at the edges, partial recounts, and an indestructible
  Singularity.

### Screens

- **One layout everywhere.** Pages (filter, priority, sides, slots) are tabs on the right, lit while
  open; a block's own settings are tabs on the left. Icons replace letters, tooltips are a line, and
  nothing overlaps.
- **Sides** and **Simulator slot binding** open in the machine's own frame and save as you change them.
  **Priority** has its own page: type it, step it or scroll it.

### Progression

- **Anomalite from Medium anomaly**: crystals seed slowly at Medium, two to a chunk, and faster at High
  (up to 8), Critical (16) and Singularity (no limit). A **Quantum Exciter** clicked aims for High, so a
  modest base can get there.
- **Anomaly lingers**: it fades at half flux's rate. Craft surcharges are halved (Medium +10%, High +25%).
- **The Decoherence Lance no longer needs the Foundry**: its Anomalite Emitter has a crafting-table recipe.
- Cheaper Anomalite recipes: Budding Anomalite (no crystal), Attuned Glass (a shard tunes four), the
  Simulator, Laser Emitter I. Laser Mirrors from silver where a pack has it.
- The Exciter and field-control blocks no longer leak FE to anomaly. Solar Emitters run full at midday.
- Swinging at a rift says what closes it, and only now and then throws you back.

### Changed

- **The Harvest Laser is gone**: laser light through a Rift Lens with Harvest and Pickup lenses does its
  work, cutting crystals and drawing Veil Thread. One already placed becomes a Laser Emitter I.
- The old **Collector** and **Sender** are the **Laser Port**; the old **Filter Lens** is the **Item
  Filter Lens** and keeps its settings.
- New `lasers` options: `maxRange` (64), `phantomFePerBlock` (20, and Phantom light's actions cost
  twice as much), `laserIdleSeconds` (4), `unrealisingLens`, `crushingRecipeTypes` and the cavity leak.

## 0.4.0-alpha: the Quantimium Reactor

**The Quantimium Reactor**, a first version, and the machines of All the Mods 11. Same Minecraft
26.1.2 and NeoForge 26.1.2.109. Worlds from 0.3.x carry on.

### The Quantimium Reactor

- **A captive black hole.** Collapse an unbound Tesseract in an empty Fold Chamber (9 or more a side)
  into a **Singularity**, and seat it in a **Horizon Core** on an 11-wide disc of **Reactor Plinth**.
  **Ring Emitters** in facing pairs drive its rings: each ring quadruples what it holds, up to sixteen
  million items. It holds exactly what goes in, data and all, and nothing is ever at risk: a full
  horizon refuses more, an unpowered one waits. The Singularity *is* the horizon: take it out and
  everything goes with it.
- **One list of everything it holds or could make**, like an ME terminal: through up to eight
  **Catalyst Bays** (a machine, a crafting table or a Folded Tesseract each), counted off the server
  thread, sortable and searchable. Recipe loops are never counted back into themselves, and things made
  from the same source share it.
- **Take anything on the list** and it plans the whole tree through its catalysts, all or nothing:
  held items and leftovers first, the recipe that uses least, presses and other tools made once and
  kept, cutting knives worn rather than used up.
- **Ports** on the disc's rim: Input, Output, Energy, and the **Materialiser Port**, which offers the
  whole list to pipes and to an AE2 storage bus as if it were all there.
- **Unrealised Matter in the horizon** is observed into whatever it could become, with no catalyst.

### More machines

- **The machines of All the Mods 11 are catalysts** for the Quantum Crafter, the ME Superposition
  Crafter and the Reactor: **EnderIO** (Alloy Smelter, SAG Mill, Slice'n'Splice), **Energized Power**
  (Crusher, Pulverizer, Sawmill, Compressor, Alloy Furnace, Induction Smelter, Metal Press, Charger,
  Assembling Machine, Powered Furnace), **Powah** (Energizing Orb), **Productive Bees** (Centrifuges),
  **Mystical Agriculture** (Seed Reprocessor, Infusion and Awakening Altars) and **Iron Furnaces**.
  Only guaranteed outputs are made. The Crystal Growth Chamber is left out on purpose: with instant
  crafting it would be endless amethyst.
- **Productive Bees hives in the Simulator**, expansion boxes and all, with a Simulator Upgrade (or
  Productivity III or IV) in the hive so its bees stay home.
- **Recipe adapters** can read more: arrays and counted ingredients, several outputs with chances
  left out, a recipe's own energy, and the machines that run each recipe type.

### The mirror and Matter

- **Mirror mites leave Quantimium Traces**: a quarter of those killed by a player drop one, in the
  mirror, for whoever killed them.
- **Doors, trapdoors, fence gates, buttons and levers work from the mirror**, on the real world
  (`#quantimium:mirror_usable`).
- **Unrealised Matter** slowly morphs between a gem, lapis, coal and a raw ore nugget, and JEI and ME
  terminals now find it by name.
- Matter never becomes **Allthemodium, Vibranium or Unobtainium**: they're All the Mods' endgame
  (`#quantimium:unrealised/excluded`).
- **A Sophon** takes a Semi-Stable Tesseract, 4 Anomaly Fragments and a Rift Residue at the Unfolding
  Array, instead of a Totem of Undying and 16 Unrealised Matter.

### The guide and the wiki

- **Multiblocks in 3D** in the in-game guide: turn and zoom the Reactor, Foundry, Fold Chamber,
  Containment Hall, Superposition Pod, Unfolding Array and Harvester. The guide key over any part of a
  multiblock opens its page. The Superposition Pod moves to Multiblocks.
- **The wiki is public**, at <https://kadikular.github.io/quantimium/>.

### Shaders

- **Iris**: shader packs now draw the Reactor, Tesseracts, beams and glows, where they skipped them or
  drew them black. Support is partial: some fades show hard edges, rifts stay dark and the mirror loses
  its colours. `config/quantimium/iris.properties` can steer a pipeline for a pack that draws one badly.

### Fixes

- A world could hang on "Preparing for world creation" with **Potions Master** installed.
- Counts on the Reactor's list could promise more than could be made, and crafting cable anchors used
  up a cutting knife each time.
- The Decoherence Lance's beam starts at the lance, in first and third person.
- Tesseract **Stabiliser**, spelled like everything else.

### Art and license

- **All textures and sounds are Quantimium's own**, or vanilla's referenced rather than copied: new
  Anomalite crystals, Trace, mirror flora and Mirror Endermite, and vanilla's own sounds re-pitched.
- **Quantimium is licensed LGPL-3.0.**

## 0.3.1-alpha: the Fold Chamber

**The Fold Chamber**, a first version that folds our own Quantum Foundry. Worlds from 0.3.0 carry on.

- **Build a frame round the machine:** a Fold Pylon on each corner, Fold Rails along the edges, and the
  Fold Core in the middle of any bottom edge. Each side is 3 to 17 blocks; a Foundry takes 9 × 3 × 9.
- **Bind a Tesseract to the controller**, put it in the core and press **Fold**, for 2,000 FE a block.
  **Unfolding is free**, in any chamber with room for the machine. Contents never cross a fold.
- **The Folded Tesseract** is a catalyst in a Quantum Crafter or ME Superposition Crafter for the
  machine's recipes, at a Critical field.
- **`crafter.structurelessMultiblocks` is now off by default**: fold the built machine instead. The
  Casual preset turns it back on. An existing config keeps the value it was written with.

## 0.3.0-alpha: Minecraft 26.1

**Quantimium moves to Minecraft 26.1.2 and NeoForge 26.1.2.109**, the versions All the Mods 11 is built
on, and needs Java 25. There is no 26.1 build of 0.2.0 and no 1.21.1 build of this one: 1.21.1 ends at
0.2.0 plus the milestones below, on the `1.21.1` branch. **Alpha: start a new world.** 1.21.1 worlds
are not supported.

### Unrealised Matter becomes the pack's own ores (M4)

- **A collapse picks one of the pack's ores**, from every `#c:ores/*` tag, and gives what it drops
  mined with a plain pickaxe, so every mod's ores work with no per-mod code. Rarity comes from
  `#quantimium:unrealised/*` block tags, and the band where the Chamber stands decides the odds:
  commons only at Low, very rare from Critical. JEI shows each ore's chance in every band.
- **Process it unobserved.** Matter put through a catalyst in a Quantum Crafter stays Matter and
  remembers the step, up to five; each step keeps every form it could make (raw, dust, ingot, plate,
  wire...).
- **Observe it step by step.** A Chamber walks the steps that hold, each with a chance that rises with
  the band, then collapses into one of the forms reached. Above Low it takes flux for better odds.
- **The Materialiser** makes Matter into the ore and form you choose, capped by its band, for Trace,
  flux and FE. With no output chosen it **works on demand**: pipes and import buses see every form it
  could make and have it made as they pull. A bound Tesseract in its Matter or Trace slot reads a
  linked inventory.

### Anomalite, harvested (M5)

- **The Anomalite Cell** (Foundry, Medium) carries charge, and **the Emitter** (Foundry, Medium) is now
  the Lance's light.
- **The Decoherence Projector burns Anomalite Cells**: watching is free, beaming costs charge. It mounts
  on any face, and Anomalite no longer grows on it.
- **The Harvest Laser and Rift Lens** fire through a tear onto a crystal up to six blocks out, wait for
  it to grow full and shatter it whole for its shards. The tear leaks anomaly and, rarely, a mite.
  Wrenches turn both.
- **Veil Thread:** fired through a Rift Lens into a hall, the laser draws a thread from the Veiled it
  holds, every three minutes.

### Fixes from the move

- A Tesseract Stabilizer exporting from a linked Crafter crafts again; pipes and buses asking a Crafter
  or Materialiser what it offers no longer spend energy (or Matter read through a Tesseract).
- A breaching Tier 4 rift stops at four mites again, and closing a rift recalls its mites.
- The Quantum Crafter runs AE2's Charger and Inscriber recipes again, and JEI shows the Foundry's
  recipes on servers.
- Works with Jade, AE2 and JourneyMap as All the Mods 11 ships them.

### For pack makers

- A creative-only **Creative Energy Cell** powers machines without a partner mod's battery.
- Recipe adapters can read inputs by field (`"input_mode": "field"`), including optional ones, for
  mods whose recipes don't expose them to the recipe book.

## 0.2.0-alpha: the spine

Flux is now stable, visible and worth running hot. **Alpha: this version breaks 0.1 worlds.** Registry
ids were renamed and the field model was rewritten; start a new world.

### The field (Field Model 2.0)

- **A steady factory gives a steady reading.** Emission eases in through a pool, the field decays on a
  smooth curve and spreads to its neighbours, so it settles once, on an S-curve, and never flickers at a
  band edge or a chunk line.
- **A source makes a plateau**, a band for each tenfold of FE: 1,000 FE/t holds the 3×3 round it at
  Medium, 10,000 at High, 100,000 at Critical.
- **Containment is capacity.** A chunk under containment shows its load; flux past the capacity turns
  into anomaly in proportion, so a little over is a trickle and far over is a flood. Contained anomaly
  is atmosphere only. Containment fades by distance past its core, so the land round a hot base is
  dangerous.
- **One job per early field block:** the Quantum Exciter raises flux, the Flux Suppressor holds it
  under a ceiling, and the new **Basic Anomaly Siphon** (replacing Anomaly Containment) holds anomaly
  down and makes Anomaly Fragments. Siphons land on their mark and stay; a Clear setting holds at 0.
- **Where it's heading:** every reading says where the field is going, not just where it is:
  *Medium · heading for High*.
- A **Debug Field Emitter** (creative) for watching the field settle.

### Running hot pays

Machines do more in a hotter field, read where they stand:

- **Quantum Simulator:** largest batch 2× / 4× / 8× / 16× / 32× from Low to Singularity (up from 8×).
- **Quantum Crafter** and **ME Superposition Crafter:** each catalyst tier needs a band (electric
  machines Medium, heavy machines High, multiblock controllers Critical, the Fusion Reactor
  Singularity), and the instant-craft tax falls from 2.0× to 1.1× as the field heats up.
- **Zeno Field Controller:** fastest rate 2× to 16× by band, 25% cheaper at Singularity.
- Below a band, a machine says what it needs and where the field is heading.

### Tiers and recipes

- Each tier needs the material of the loop before it: Trace at Low, Anomaly Fragments and Anomalite at
  Mid, Rift Residue and Tesseracts at High. **High starts with your first rift.**
- The **Quantum Crafter**, **Quantum Simulator** and **Tesseract Stabilizer** have recipes.
- Recipes ask for common tags (`#c:ingots/iron` and so on), so any mod's materials work.

### Rifts and the Veiled

- The Unobserved is now called **the Veiled**.
- **Rift Seeds** are a Mid-tier craft. Planted in an uncontained chunk at Medium anomaly or more, a
  seed opens a wild rift; anywhere else it fizzles and is kept. On a Rift Anchor it opens a held rift
  anywhere. Only a Rift Seed lights the Stabilised Portal.
- A stage 1 rift pays no Residue: let it grow first (3 / 6 / 10 after).
- Rifts are limited by neighbourhood (at most 3 within 256 blocks, 64 apart), not by dimension.
- A held Veiled keeps the peace for 128 blocks. The Containment Hall's screen shows whether its cell
  can take a Veiled, and a Veiled that gets away tells you why the hall nearby couldn't hold it.
- Fixed: the Veiled's blink could land out of reach, or be kept out by a shut door.

### Seeing the field

- **Field Monitor:** a map of the 9×9 chunks round it, and an alarm (redstone, and a comparator
  counting chunks) for overloaded containment or uncontained anomaly.
- **Field map** on the Mirror Lens (N).
- **Jade:** flux, anomaly, where it's heading, load and what anomaly costs, for Quantimium blocks.
- **JourneyMap:** the field and rifts on your map, each with a toolbar button to show or hide them.
- **In-game guide:** the whole wiki, through GuideME. The Quantimium Field Guide is a Book and
  Unrealised Matter; hold G over an item for its page.
- **Advancements** on their own tab, laid out as the path through the mod.

### For pack makers

- A pack-maker page on the wiki: every config value, tag and datapack hook, with Kitchen sink,
  Casual, Expert and Server presets.
- New switches: structureless multiblock catalysts, Simulator nesting depth, every band reward, rift
  limits, map overlays.
- Registry ids renamed: `entangled_link` → `tesseract`, `flux_generator` → `quantum_exciter`,
  `unobserved` → `veiled`.

## 0.1.0-alpha

The first tagged alpha.
