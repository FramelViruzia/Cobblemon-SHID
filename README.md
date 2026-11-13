# Cobblemon SHID

**Cobblemon SHID (Simple Hyper-trained IVs Display)** is a mod that displays an IV HUD that shows changes
to hyper-trained IVs whenever a player holds and uses IV candies (or caps) on their Pokémon.

This is a client-side mod.
## Requirements
- Minecraft 1.21.1 Fabric
- Cobblemon 1.6 or newer
- Cobblemon Utility+ (Optional, for their caps)

## Settings: `config/cobblemon_shid.json`
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
