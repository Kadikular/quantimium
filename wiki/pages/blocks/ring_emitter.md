Ring Emitters drive the rings that hold the [Reactor](../multiblocks/reactor.md)'s horizon open.

- Place them on the plinth around the core in facing pairs, four blocks out: east and west, north and south, or diagonally three out each way.
- Each whole pair drives one ring. Each ring quadruples what the horizon holds: {{c:HorizonCoreBlockEntity.BASE_CAPACITY}} items with one ring, four million with two, sixteen million with three.
- Each emitter draws {{c:HorizonCoreBlockEntity.EMITTER_FE_PER_TICK}} FE/t. Unpowered, the rings go down and the Reactor takes nothing in and makes nothing, but keeps everything.
