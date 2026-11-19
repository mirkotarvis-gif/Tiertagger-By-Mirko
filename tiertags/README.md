# TierTags (Fabric, Minecraft 1.21.11)

Shows player tiers from **MCTiers**, **PvPTiers**, **SubTiers** and **GoatTierList**.

## Features
- **Nametags:** the player's best tiers around their name, plus their region:
  `HT1 [sword] Name LT2 [mace] EU`
  Same gamemode is only shown once. Active tiers come before retired ones; retired tiers show as `RHT1`/`RLT2`.
- **Tab list:** the same tags next to every name in the player list.
- **Server nametags:** servers that draw their own nametags with text displays get the tags too.
- **Cracked / offline-mode servers:** players are looked up by name, so GoatTierList always works
  and the other lists work if the name belongs to a real account.
- **`/tiers <player>`:** profile screen with a tab per tierlist. Hover a ranking for details.
- **Settings:** in **Mod Menu** (TierTags → config button) or with **`/tiertags`**.

## Settings
| Page | Options |
|---|---|
| General | Enabled, Nametags, Tab list, Server nametags, Cracked lookup, Refresh all tiers |
| Sources | Turn MCTiers / PvPTiers / SubTiers / GoatTierList on or off |
| Display | 1 or 2 tags, one fixed gamemode or the best, icons, region (off/left/right), brackets, bold, retired tiers, source letter (M/P/S/G) |
| Tier colors | Hex color for every tier (HT1 to LT5), retired and brackets, with a reset button |
| Regions | Hex color for every region (NA, EU, SA, AS, AU, OC, ME, AF) |

A live preview at the bottom of the screen shows how a nametag will look. Settings are saved to
`config/tiertags.json`.

## Install
Requires Fabric Loader 0.17+, **Fabric API**, and Java 21. **Mod Menu** is optional (17.x for 1.21.11).
Drop the jar and Fabric API into `.minecraft/mods`.

## Build
Push to GitHub: the included workflow builds it, download the jar from the run's **Artifacts** (Actions tab).
Or locally with JDK 21: `./gradlew build` (jar in `build/libs/`).

## APIs used
- `https://mctiers.com/api/v2/profile/<uuid>`
- `https://pvptiers.com/api/profile/<uuid without dashes>`
- `https://subtiers.net/api/v2/profile/<uuid>`
- `https://goattierlist.zenvex.cloud/api/tiers` (whole list, matched by name, refreshed every 5 minutes)
- `https://api.mojang.com/users/profiles/minecraft/<name>` (name to UUID)

Results are cached for 15 minutes; failed requests retry after 1 minute.

## Credits
Gamemode icons are from [TierTagger](https://github.com/mctiers-dev/TierTagger)
(MPL-2.0, (c) MCTiers). Not affiliated with MCTiers, PvPTiers, SubTiers or GoatTierList.
