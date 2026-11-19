# Cobblemon SHID

**Cobblemon SHID (Simple Hyper-trained IVs Display)** is a mod that displays an IV HUD that shows changes
to hyper-trained IVs whenever a player holds and uses IV candies (or caps) on their Pokémon.

This is a client-side mod.
## Requirements
- Minecraft 1.21.1 Fabric
- Cobblemon 1.6 or newer
- Cobblemon Utility+ (Optional, for their caps)
- Mod Menu (Optional, for the settings screen)

## Settings screen
Open it from Mod Menu. Changes apply when you press **Save & Quit**; the HUD Preview shows them live.

| Option | Choices | Default |
|---|---|---|
| Show HUD | Yes / No | Yes |
| HUD Style | Full / Compact | Full |
| HUD Position | Locked / Draggable | Locked |
| HUD Size | 0.5x to 3.0x (max 2.0x at GUI scale 2, 1.5x at GUI 3, 1.0x at GUI 4+) | 1.0x |
| Font Size | 0.5x to 1.5x | 1.0x |
| Background Opacity | Transparent, 1% to 100% | 90% |

**Moving the card:** set HUD Position to Draggable, then drag the card with the mouse while the chat
is open, or hold **Left Alt** (Controls: *Show mouse when HUD is open to adjust the position*).
Reset next to HUD Position puts the card back beside the crosshair and locks it.

## Config file: `config/cobblemon_shid.json`
| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Show HUD |
| `hudStyle` | `FULL` | `FULL` or `COMPACT` |
| `lockPosition` | `true` | `false` = Draggable |
| `offsetX` / `offsetY` | `0` | Pixels moved from the default spot beside the crosshair |
| `scale` | `1.0` | HUD Size |
| `fontSize` | `1.0` | Font Size |
| `backgroundOpacity` | `90` | Background Opacity in percent |
| `useDefaultItems` | `true` | Use the built-in trigger item list |
| `extraItems` | `[]` | More item ids, e.g. `"cobblemon_utility:cobblemax"` |

A red sync icon in the top-left corner means the client has not received that Pokémon's IVs yet:
open its summary or the PC once.
