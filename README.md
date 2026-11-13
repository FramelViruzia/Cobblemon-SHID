# Cobblemon SHID

**Cobblemon SHID (Simple Hyper-trained IVs Display)** is a mod that display IVs HUD that show changes
to hyper-trained IVs whenever a player holds and uses IV candies (or caps) on their Pokémon.

- Author: ViruziA
- Client-side only: Fabric, Minecraft 1.21.1, Cobblemon 1.6 or newer. Requires Fabric API.
- Cobblemon Utility+ is optional. Without it, the card shows for Cobblemon's IV candies. With it,
  the card also shows for its bottle caps.

## When the card shows
1. You hold an IV candy or a cap, in your main hand or off hand.
2. Your crosshair is on one of **your own** Pokémon.

The default trigger items are:
- Cobblemon's IV candies: `health_candy`, `mighty_candy`, `tough_candy`, `smart_candy`,
  `courage_candy` and `quick_candy`, plus the candies that lower IVs.
- Every Cobblemon Utility+ bottle cap: golden, wooden, silver, shining silver, obsidian and void.

The card stays up while the chat is open. It disappears as soon as the crosshair leaves the Pokémon.

## Base IVs are remembered
The Utility+ Wooden Cap writes its rolls into the Pokémon's **base** IVs, so the game itself
forgets the original values. This mod keeps its own copy:
- It saves each Pokémon's base IVs the first time it sees that Pokémon, in
  `config/cobblemon_shid_original_ivs.json`. Every Pokémon in your party is checked once a second.
- It always compares against that saved copy.

A Pokémon that was already re-rolled before the mod first saw it is recorded with its re-rolled
values. To record a Pokémon again, delete its entry, or delete the whole file.

## Colours
- **Nothing changed:** one yellow shape.
- **Changed by a candy or cap:** the saved base IVs become a faint grey shape, and the final IVs
  are drawn in yellow on top.
- **Labels:** read `base (final)`. The base is grey; the final value is white, or yellow when it is 31.
- **Recent change:** a stat that just changed gets a green or red dot for a few seconds.

## Settings: `config/cobblemon_shid.json`
Saved changes are picked up within a second; no restart is needed.

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Turns the HUD on or off |
| `anchor` | `BESIDE_CROSSHAIR` | Where the card is pinned (options below) |
| `offsetX` | `0` | Pixels right from the anchor (negative = left) |
| `offsetY` | `0` | Pixels down from the anchor (negative = up) |
| `scale` | `1.0` | Size of the whole card |
| `textScale` | `0.67` | Text size relative to the normal font (0.67 = 12 screen px at GUI scale 2) |
| `statNameScale` | `0.56` | Size of the stat names HP, Atk... (0.56 = 10 screen px at GUI scale 2) |
| `changeHighlightSeconds` | `4.0` | How long the change markers stay |
| `useDefaultItems` | `true` | Use the built-in trigger item list |
| `extraItems` | `[]` | More item ids, e.g. `"cobblemon_utility:cobblemax"` |

`anchor` options:
- `BESIDE_CROSSHAIR`: left of the crosshair, bottom edge a little below the last party slot.
  The card is kept clear of Cobblemon's party bar and on screen at every GUI scale.
- `TOP_LEFT`, `CENTER_LEFT`, `BOTTOM_LEFT`, `TOP_RIGHT`, `CENTER_RIGHT` or `BOTTOM_RIGHT`.

## Building
Requires JDK 21. Cobblemon is downloaded from Modrinth's maven during the build.

```
./gradlew build
```

The jar is written to `build/libs/cobblemon-shid-<version>.jar`.

GitHub Actions builds every push and pull request; the jar is attached to the run as an artifact.
Pushing a tag such as `v1.0.0` also creates a GitHub release with the jar attached.

## License
MIT
