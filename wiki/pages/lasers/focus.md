# Laser Focus

[[mechanic:laser.focus]] [[mechanic:laser.relay]] A [[item:laser_focus]] is where lasers gather and set off again. Put it down, then put attachments on its faces, one to a face, by using them on it.

| Attachment | What it does |
| --- | --- |
| [[item:focus_stand]] | A base on the block behind. Add as many as you like. Without one, the focus floats. |
| [[item:focus_emitter]] | The laser leaves by it. |
| [[item:focus_cover]] | A plain cap. |

- A laser reaching the focus by any face without a Stand or Cover stops there and leaves by each Emitter, shared by what each laser's blocks ask for. It does a prism's job for the six straight directions, with no linking.
- A [[item:clear_laser_focus]] does the same, and a laser also passes straight through it and on out of the opposite face, unless a Stand or Cover there stops it.
- Laser Relays from before become a Laser Focus with a Stand on their base and an Emitter on each of their outputs.

## Placing and removing

- An attachment can go on a block with no focus. Used against any block, it puts down a Laser Focus block holding just that attachment, or puts it onto the Laser Focus block already beside that face. It does nothing until a focus is put in.
- A laser passes a block with no focus by, unless an attachment is on the face it comes in or goes out by.
- Sneak-click the focus (by hand or with a wrench) to take the focus out and leave the attachments. Sneak-click an attachment to take just that off (by hand, an Emitter's lens comes out first). The block goes when it holds nothing.

## Emitters

- Click an Emitter to open it. It holds one lens, which only the laser leaving by it goes through. A Harvest Lens on one emitter cuts only there. Its tip takes the lens's color. Using a lens on it puts the lens in.
- A focus refocuses the laser to its source's range. The focus itself loses nothing, but each Emitter takes {{c:LaserTuning.HOP_TAX|pct}} of what leaves by it (a pack can change it). That becomes flux where it stands. A laser passing straight through a Clear Laser Focus loses nothing.

## Redstone

[[mechanic:laser.relay.redstone]] Each of a focus's emitters listens to the redstone reaching the block its own way, set in its screen:

- **Ignore it.**
- **On with a signal**, or **on without one.**
- **Pulse**: each signal lets the laser out until one action down it is done (a block cut, a hit, an item carried) or two seconds pass. One signal can open one emitter and close another. A pulse emitter before a Harvest Lens breaks one block per pulse.

An emitter switched off sends nothing. With all of them off, the focus passes no laser at all, and what is beyond gets only the laser that reaches it some other way.
