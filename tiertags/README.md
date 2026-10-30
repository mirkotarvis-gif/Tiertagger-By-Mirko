# TierTags (Fabric, Minecraft 1.21.11)

Shows player tiers from **MCTiers**, **PvPTiers** and **SubTiers**.

## Features
- **Nametags:** the player's 2 best tiers (across all three tierlists) around their name:
  `HT1 [shield] Name LT1 [sword]`
  Same gamemode is only shown once (e.g. Sword HT1 on MCTiers and PvPTiers counts once).
  Active tiers come before retired ones; retired tiers show as `RHT1`/`RLT2` (peak tier).
- **`/tiers <player>`:** opens a profile screen (skin on the left; Region, Points + title,
  Global rank and every ranking on the right) with a tab for MCTiers, PvPTiers and SubTiers.
  Hover a ranking to see when it was attained, points, and peak. Works for players who aren't online.

## Install
Requires Fabric Loader 0.17+, **Fabric API**, and Java 21. Drop `TierTags-1.0.0+mc1.21.11.jar`
and Fabric API into `.minecraft/mods`.

## Build
Option A, no setup: push this folder to a GitHub repo. The included workflow builds it;
download the jar from the run's **Artifacts** (Actions tab).

Option B, locally (needs JDK 21):
```
./gradlew build        # Windows: gradlew.bat build
```
The jar ends up in `build/libs/`.

## APIs used
- `https://mctiers.com/api/v2/profile/<uuid>`
- `https://pvptiers.com/api/profile/<uuid without dashes>`
- `https://subtiers.net/api/v2/profile/<uuid>`
- `https://api.mojang.com/users/profiles/minecraft/<name>` (name to UUID for /tiers)

Results are cached for 15 minutes; failed requests retry after 1 minute.

## Credits
Gamemode icons are from [TierTagger](https://github.com/mctiers-dev/TierTagger)
(MPL-2.0, (c) MCTiers). Not affiliated with MCTiers, PvPTiers or SubTiers.
