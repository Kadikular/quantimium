---
navigation:
  title: Mirrors and relays
  parent: lasers/index.md
  position: 20
---

# Mirrors and relays

A <ItemLink id="quantimium:laser_mirror" /> is a silver plate on a steel stand. Light reflects off it as off a
real mirror: a beam that reaches it bounces and goes on as the same beam, with its own light, so beams crossing one
mirror never mix. Light on the back or edge of the plate stops. A redstone signal turns it off, and then it stops light.

**Setting the plate.** Each face of the mirror has three parts: a middle square, a ring around it, and an outer ring,
each ring split four ways by the diagonals. Click (by hand or with a wrench):

| Where | The plate |
|---|---|
| The middle square of a face | Flat, facing that face: light goes straight back the way it came |
| The inner ring, toward a side | Facing that face, turned 22.5° toward that side |
| The outer ring, toward a side | Half way between that face and that side (45°): light turns through a right angle |

Looking at a face outlines its parts, and a white ghost shows where the plate would turn if you clicked. A plate never faces toward its stand, not even a little. Sneak-click to move the
stand: a face's middle puts it on that face, a ring the side it's toward.

**Angles.** Light travels in 26 ways: the 6 straight, the 12 flat diagonals (45° in one plane) and the 8 corner
diagonals. Off a 45° plate straight light turns a right angle; off a 22.5° plate it leaves at 45°, and 45° light off
a 22.5° plate leaves straight. What leaves a mirror is always one of the 26 (the nearest, where a true reflection isn't
one): a Prism's light, at any angle, is bent onto them only there. A diagonal step counts its true length toward the
light's reach, and diagonal light slips between two blocks that meet only at a corner (unless the
`diagonalsPassCorners` config is off). A block takes diagonal light if it would take light through any face it comes in
by.

A <ItemLink id="quantimium:laser_splitter" /> is a Laser Mirror with a half-silvered plate, set the same way.
Light reaching it goes straight on and is reflected, off both sides of the plate: one beam becomes two, each its own,
sharing the power by what each end asks for. Two beams through one splitter stay apart. Light never leaves a
mirror or splitter through its stand: straight into it, it stops. A splitter closes no cavity.

Both refocus the light to its source's range: each run after a bounce reaches as far as a run from the emitter that
sent it. Each bounce takes 0.25% of what the beam carries (a pack can change it), which becomes flux
where the beam starts.

A <ItemLink id="quantimium:laser_focus" /> is where light gathers and sets off again.
Put it down, then put attachments on its faces, one to a face, by using them on it: a <ItemLink id="quantimium:focus_stand" /> (a base on
the block behind, as many as you like; without one it floats), a <ItemLink id="quantimium:focus_emitter" /> (light leaves by it) or a
<ItemLink id="quantimium:focus_cover" /> (a plain cap). Light reaching it by any face without a Stand or Cover stops there and leaves by
each Emitter, shared by what each beam's blocks ask for: a prism's job for the six straight directions, with no
linking. A <ItemLink id="quantimium:clear_laser_focus" /> does the same, and light also passes straight through it and on out of the
opposite face, unless a Stand or Cover there stops it.

As with a cable and its parts, an attachment can go on a block with no focus (used against any block, it puts down a
Laser Focus block holding just it, or puts it onto the Laser Focus block already beside that face), and it does
nothing until a focus is put in: light passes a block with no focus by, unless an attachment is on the face it comes in
or goes out by. Sneak-click the focus (by hand or with a wrench) to take it out and leave the
attachments, or an attachment to take just that off (by hand, an Emitter's lens comes out first); the block goes
when it holds nothing. Click an Emitter to open it: it holds one lens, which only the light leaving by it goes through (a Harvest
lens on one emitter cuts only there), and its tip takes the lens's color; using a lens on it puts the lens in.
Laser Relays from before become a Laser Focus with a Stand on their base and an Emitter on each of their outputs.

A focus refocuses the light to its source's range: a run from it reaches as far as a run from the emitter that sent
it. The focus itself loses nothing; each Emitter takes 0.5% of what leaves by it (a pack can
change it), and that becomes flux where it stands. Light passing straight through a Clear Laser Focus loses nothing.

Each of a focus's emitters listens to the redstone reaching the block its
own way, set in its screen: it ignores it, lets light out only with a signal, only without one, or in
**pulse** mode, where each signal it gets lets light out until one action down its beam is done (a block
cut, a hit, an item carried) or two seconds pass. One signal can open one emitter and close another. A
pulse emitter before a Harvest lens breaks one block per pulse. An emitter switched off sends nothing; with
all of them off, the focus passes no light at all, and what's beyond gets only the light that reaches it
some other way. An emitter switched off by redstone gives out none.

An <ItemLink id="quantimium:isolator" /> passes light on as a relay does, but lets only power
through: light leaving it carries nothing it did (actions, Pickup, UV), and nothing comes back up through
it, so Pickup's items stop short of it (an <ItemLink id="quantimium:isolator_lens" /> stops them too, in a Lens Mount or a Focus Emitter, unless set to pass logistics). It's a plain block: sneak-click or wrench a face to add an output
there or take one away (one always stays), and use an empty hand to step through how it listens to
redstone.

An <ItemLink id="quantimium:isolator_lens" /> does the same in a Lens Mount or a Relay emitter, and can be set to let more through: switch on
a logistics network, actions, modifiers or a Redstone Lens's signal to let them pass, and list lenses to let through
only those, or to stop just those (an Isolator Lens passing actions but stopping Harvest). It can also stop power:
then nothing past it is charged by its light, though what the light does past it is still paid for. The light's reach
always passes. Unset, it passes power only, as the Isolator does; an Isolator alone in a crafting grid makes one. Use
it in the air to set it.

## Entangled mirrors

An <ItemLink id="quantimium:entangled_mirror" /> sends on the light of another laser block, any
distance away, through anything, in the same dimension. It's made in pairs in the
<ItemLink id="quantimium:quantum_foundry_controller" />, from a <ItemLink id="quantimium:tesseract" /> and two Laser Mirrors. Its muzzle faces you as you
place it.

- Bind a Tesseract to the laser block the light should come from, a <ItemLink id="quantimium:laser_focus" /> say (sneak and use it on
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
