# Contributing to Traveler's Lectern

We love your input! We want to make contributing to Traveler's Lectern as easy and transparent as possible, whether it's:

- Reporting a bug
- Discussing the current state of the code
- Submitting a fix
- Proposing new features
- Becoming a maintainer

## Development Process

We use GitHub to host code, to track issues and feature requests, as well as accept pull requests.

1. Fork the repo and create your branch from `main`
2. If you've added code that should be tested, add tests
3. If you've changed APIs, update the documentation
4. Ensure the test suite passes
5. Make sure your code lints
6. Issue that pull request!

## Build Process

One source tree builds a jar for each range of Minecraft versions, using
[Stonecutter](https://stonecutter.kikugie.dev/), as Traveler's Journal does. Each folder under
`versions/` is one jar: its `gradle.properties` names the Minecraft version it builds against and
the range its jar declares. Code that differs between versions is marked with Stonecutter comments,
for example:

```java
//? if >=1.21.6 {
return SharedConstants.getCurrentVersion().dataVersion().version();
//?} else {
/*return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
*///?}
```

Whole features that a version lacks are left out by `build.gradle` instead: the lectern manager
(`com/gbti/travelerslectern/dialog/`, `CustomClickActionMixin` and
`travelers_lectern.dialog.mixins.json`) builds only for 1.21.6 and later, where Minecraft has server
dialogs, and only those jars' `fabric.mod.json` lists that mixin config. The lectern mixin ships on
every version.

Three things differ by version and each has one home:

- **The lectern click** is `use` before 1.20.5 and `useItemOn` after, whose result type changed
  again in 1.21.2. `LecternBlockMixin` injects into whichever the version has and hands the click
  to `Lecterns.use`.
- **The book** is NBT before 1.20.5 and item components after. `LecternBooks` turns it into text
  and back, and runs Minecraft's own item upgrades on a book saved by an older version.
- **The player's level** is never read with `Entity.level()`, whose intermediary name changed in
  1.21.9 inside the 1.21.6 jar's range. It comes from the event or the player's command source.

Releases ship one jar. `scripts/bundle.py` packs every per-range jar inside it (Fabric's
jar-in-jar) and renames each one's mod id to `travelers_lectern_mc`. The outer jar is the mod
`travelers_lectern`, holds no code, and requires one of the supported ranges, so Fabric loads the
per-range jar that fits the server and refuses the whole jar anywhere else. The per-range jars
never ship on their own.

You need JDK 25. It also builds the 1.20.x jars, which target Java 17, and the 1.21.x jars, which
target Java 21.

1. Build every jar into `build/libs/`:
   ```bash
   ./gradlew buildAndCollect
   ```
   `npm run build` does the same, and `npm run build -- --deploy` also copies the 1.21.1 jar into
   the GBTI server's `mods` folder.
2. Build one jar: `./gradlew :1.21.11:build`.
3. Work on one version in your IDE: `./gradlew "Set active project to 1.21.1"`. Run
   `./gradlew "Reset active project"` before committing, so `src/` is committed in its 26.3 state.

## Saved lecterns

Lecterns are saved in `config/travelers-lectern/lecterns.json`, keyed by dimension and position,
each with its book as text and the data version it was saved at. Releases before 2.0.0 kept them in
`travelers_lecterns.json`. 2.0.0 imports that file once and never writes it, so a server that goes
back to 1.2.x finds it as it was; if the old file is newer, because 1.2.x ran in between, it is
imported again. A file that cannot be read is never written over: the store turns read-only and
saving says so. A book a version cannot read is kept as saved and written back unchanged.

## Testing

`scripts/test-matrix.sh` boots every jar on a real Fabric server for Minecraft 1.20 to 1.20.4 and
every version from 1.21 to 26.3, with the probe test mod in `src/probe` beside it. The probe
installs the lecterns a 1.2.x release left (`src/probe/fixtures/past-install`), joins mock players
the way a login does, and checks, in steps a second or more apart so the mod's once-a-second tick
runs in between:

- `LecternProbe`: the import of the 1.2.x file and that file left untouched; the commands and that
  they need an operator; taking the book by sneaking and with the lectern screen's Take Book
  button; the empty lectern's action bar line and that it takes no other book; the book coming back
  after its cooldown and being free to take again at once; reading leaving the timer alone; a
  plain lectern behaving as vanilla; only operators breaking one; the same position in two
  dimensions; `destroy` putting a taken book back first; a broken file leaving the store read-only.
  Below 1.21, where a 1.21.1 book cannot be read, it checks the imported books are kept as saved.
- `DialogProbe`, on 1.21.6 and later: the manager. Each press takes a button from the dialog the
  server actually sent, its action and additions, lays the inputs' values over them as a client
  does, and goes through the click packet's codec. It covers every screen and state, Teleport there
  into the Nether, and refusals.
- `PacketCheck`, on every version: every packet the server sends a mock player is encoded with
  that version's own codec, so a packet no client could receive fails the run.

A jar passes only when every check does. The probe announces its count on its `START` line, and
`release:stage` holds each passing cell to its own count as well (`CHECKS_BEFORE_MANAGER` and
`CHECKS_WITH_MANAGER` in `scripts/release.js`), so change those when you add checks. Each version
must have exactly one passing per-range jar, and Fabric must refuse every other one there for its
minecraft range: a refusal for any other reason is a failure. The release then tests the bundled
jar itself on every version, beside the probe built for that version's range, and checks that
Fabric refuses it on 1.20.5 and 1.20.6. The upgrade, downgrade and world tests below run against
the bundled jar too. It runs on Linux or WSL:

```bash
npm test
```

### Upgrades from past releases

A server that ran an earlier Traveler's Lectern must keep its lecterns when it swaps in a new jar.
The upgrade test downloads every release Modrinth lists for Minecraft 1.21.1, where every release
before 2.0.0 ran, checks each against Modrinth's SHA-512, and adds the jars in
`.data/past-releases/`, which is never committed. For each release it boots the old jar first and
the new one second, on the same world: once with the old jar's own default config, and once with
the install in `src/probe/fixtures/past-install`. `UpgradeProbe` checks that every file the old jar
left is byte for byte the same once the new jar has started, and that every lectern in the old
file was imported with a readable book.

```bash
npm run test:upgrades
EXTRA_PAST="/path/to/travelers-lectern-1.2.10.jar" npm run test:upgrades
```

### Going back to an earlier release

The downgrade test puts each past release back in place of the new jar, on the same world, both for
a server that upgraded and then went back and for a fresh install of the new jar. Under the new jar
the probe adds a lectern; then, beside the old jar and with vanilla code only, it checks that the
old file is as the new jar found it and that the old jar loaded every lectern in it.

```bash
npm run test:downgrades
```

### Worlds moving to a later Minecraft

Minecraft upgrades a world's items when the world opens on a later version: books go from NBT to
item components at 1.20.5, and their stored text changes again at 1.21.5. The world upgrade test
moves worlds from 1.20.1 and 1.20.4 to 1.21.1, from 1.21.1 to 1.21.5, from 1.21.5 to 26.1, and from
1.20.1 straight to 26.3, with one Traveler's Lectern whose book is on it and one whose book a player
took. `WorldUpgradeProbe` checks that both books still read on the later version, the taken one is
still out, and it comes back once its cooldown has passed.

```bash
npm run test:worlds
```

### Beside Traveler's Journal

Servers often run both mods, and both have dialogs and a click mixin at the same point. A run cell
copies the jars in `EXTRA_MODS` into the server beside this one and waits for `EXTRA_UNTIL` as
well, so both probes can run in one server; read the Journal probe's `DONE` line from the cell's
log. The probes' mock players have their own names and start empty-handed, since the Journal gives
every new player a journal.

```bash
EXTRA_MODS="travelers-journal-2.0.0.jar travelers-journal-2.0.0+mc26.3-probe.jar" EXTRA_UNTIL="[TJ-PROBE] DONE" \
  scripts/test-matrix.sh run build/libs/travelers-lectern-2.0.0.jar build/libs/travelers-lectern-2.0.0+mc26.3-probe.jar 26.3
```

## Releasing

1. Set `mod_version` in `gradle.properties`, add a `## [<version>]` section to
   `.product/changelog.md`, and commit both.
2. `npm run release:stage` builds every per-range jar, runs the test matrix, bundles the jars,
   tests the bundled jar on every version and through the upgrade, downgrade and world tests, and
   stages it with the release notes and checksums in `build/release/<version>/`.
3. `npm run release:publish -- --dry-run` shows what publishing will do. Without `--dry-run` it tags
   the commit, pushes, creates the GitHub release and uploads to Modrinth and CurseForge when
   `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` are set. Planet Minecraft is updated by hand.

## Any contributions you make will be under the GNU General Public License v3.0

In short, when you submit code changes, your submissions are understood to be under the same [GNU General Public License v3.0](LICENSE) that covers the project. Feel free to contact the maintainers if that's a concern.

## Report bugs using GitHub's [issue tracker](https://github.com/gbti-network/minecraft-mod-travelers-lectern/issues)

We use GitHub issues to track public bugs. Report a bug by [opening a new issue](https://github.com/gbti-network/minecraft-mod-travelers-lectern/issues/new); it's that easy!

## Write bug reports with detail, background, and sample code

**Great Bug Reports** tend to have:

- A quick summary and/or background
- Steps to reproduce
  - Be specific!
  - Give sample code if you can
- What you expected would happen
- What actually happens
- Notes (possibly including why you think this might be happening, or stuff you tried that didn't work)

## License

By contributing, you agree that your contributions will be licensed under its GNU General Public License v3.0.
