# Screen Flick (Fabric, Minecraft 26.2)

One keybind (default `R`, rebind under Controls > Screen Flick) that runs a flick hit plus a sprint reset:

1. Locks the nearest mob or player near your crosshair within reach.
2. Snaps your crosshair 45° past its center. Holding `A` flicks left, `D` flicks right; with neither, it continues the way your crosshair is already crossing them.
3. Next tick, attacks with that yaw so the knockback goes along the flick, then snaps back to center.
4. W-tap (releases W for 2 ticks) or S-tap (releases W and holds S for 2 ticks), then re-sprints if you're still holding W.

Settings live in `config/screenflick.json` (created on first launch): `flickAngle`, `tapMode` (`W_TAP` / `S_TAP`), `tapTicks`, `autoResprint`, `targetCone`.

## Build
Push to GitHub and the `build` workflow produces the jar as the `screenflick-jar` artifact, or run `./gradlew build` locally with JDK 25 (output in `build/libs/`).

Requires Fabric Loader 0.19.3+ and Fabric API for 26.2.
