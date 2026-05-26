# Audio assets

This folder includes original synthesized `.wav` assets so the game has audible
feedback in development without third-party licensing. The audio service loads
these bundled `.wav` files first, and still supports `.ogg` and `.mp3` files with
the same names when final production assets are available.

## SFX

Expected files in `public/audio/sfx/`:

- `correct.wav`
- `wrong.wav`
- `streak_3.wav`
- `streak_5.wav`
- `game_start.wav`
- `game_over.wav`
- `sabotage_sent.wav`
- `sabotage_received.wav`
- `tick.wav`
- `ui_click.wav`

## BGM

Expected files in `public/audio/bgm/`:

- `bgm_menu.wav`
- `bgm_game.wav`

For production replacements, use `.ogg` as the primary format and `.mp3` as the
fallback. Recommended sources: freesound.org and zapsplat.com. Make sure every
asset license is compatible with the project before shipping.
