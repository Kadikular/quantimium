# Quantimium

Quantimium is a NeoForge mod about **measuring things into existence** and living with the
consequences. Its machines craft, simulate and grow by drawing on a local quantum field, and every
craft stirs that field up. Push it far enough and the world starts to tear: into a grey **mirror** of
itself, where things grow that should not, and something watches from the rifts.

**[Read the wiki](https://kadikular.github.io/quantimium/)** for how it all works. The same pages are
in the game as the **Quantimium Field Guide** when [GuideME](https://modrinth.com/mod/guideme) is
installed.

> **Alpha.** Quantimium is in active development. Expect balance changes, and start a new world for
> each alpha release.

## What's in it

- **Flux and anomaly.** Every chunk carries two meters. Flux is how active the world is, and it pays:
  hotter fields unlock stronger machines. Anomaly follows flux up, and it costs: surcharges, leaking
  buffers, crystals growing on your machines, and in the end tears into the mirror.
- **Machines that make other mods faster.** The Quantum Crafter crafts with any machine as its
  catalyst, instantly. The Quantum Simulator runs a machine in a field of its own at up to 32 times
  its speed. The ME Superposition Crafter offers every recipe of its catalyst to an AE2 network.
- **Unrealised Matter.** Ore that hasn't decided what it is, collapsed into any ore of your pack.
- **The mirror.** A grey copy of the world laid over it, with its own flora, mites, rifts and the
  Veiled.
- **Multiblocks**: the Quantum Foundry, the Containment Hall, the Fold Chamber, which folds a whole
  multiblock into one item, and the **Quantimium Reactor**: a captive black hole that holds anything
  you put in it and makes anything your catalysts can make from it.

## Requirements

| | |
|---|---|
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.109 or newer |
| Java | 25 |

No other mods are required. Quantimium works with **Applied Energistics 2**, **Modern
Industrialization**, **EnderIO**, **Energized Power**, **Powah**, **Productive Bees**, **Mystical
Agriculture**, **Iron Furnaces**, **Hostile Neural Networks**, **Jade**, **JEI**, **JourneyMap** and
**GuideME**, and partly with shader packs through **Iris**. See
[Partner mods](https://kadikular.github.io/quantimium/concepts/partner-mods.html) for what each one
adds.

## Building from source

```bash
./gradlew build                # the mod jar, in build/libs
./gradlew runClient            # a development client
./gradlew runGameTestServer    # the in-game test suite
```

Partner mods for the development runs go in `run-26.1/mods`; mods only the test server should load
go in `run-26.1/gametest-mods`. `./gradlew runGameTestServer -PbareMods` runs the tests without them.

The wiki is built from `wiki/pages` and from the mod's own source: every block and item page, every
recipe and every number quoted from the code.

```bash
python3 -m venv .venv-wiki && .venv-wiki/bin/pip install mkdocs-material pillow   # once
./gradlew createMinecraftArtifacts                                                  # once per Minecraft version
.venv-wiki/bin/python tools/wiki/build.py --serve                                   # live preview
```

See [Writing the wiki](wiki/pages/guides/writing-the-wiki.md) for its markup, and how each rule it
describes is tied to the test that checks it.

## License

Quantimium is licensed under the **GNU Lesser General Public License v3.0**: see
[COPYING.LESSER](COPYING.LESSER) and [COPYING](COPYING). Parts of the build come from the NeoForged
MDK, under the MIT license in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).

Minecraft is a trademark of Mojang AB. Quantimium is not affiliated with Mojang or Microsoft. The
source uses Mojang's official mappings, which are under their own
[license](https://github.com/NeoForged/NeoForm/blob/main/Mojang.md).
